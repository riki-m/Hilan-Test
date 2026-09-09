package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import com.example.leavemanagement.service.LeaveApprovalService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveApprovalPostgresTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestsController controller;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Autowired private LeaveApprovalService approvals;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test void approvalIsPersisted() {
        Employee e = employee();
        LeaveRequest r = request(e, 2, LeaveStatus.PENDING);
        approvals.approve(r.getId());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test void insufficientBalanceDoesNotChangeStoredStatus() {
        Employee e = employee(); request(e, 19, LeaveStatus.APPROVED);
        LeaveRequest r = request(e, 2, LeaveStatus.PENDING);
        assertEquals(409, assertThrows(LeaveApprovalService.ApprovalException.class,
                () -> approvals.approve(r.getId())).getStatus());
        assertEquals(LeaveStatus.PENDING, leaveRequests.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test void outerFailureRollsBackFlushedApproval() {
        Employee e = employee(); LeaveRequest r = request(e, 2, LeaveStatus.PENDING);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            approvals.approve(r.getId());
            throw new IllegalStateException("Deliberate rollback after flush");
        }));
        assertEquals(LeaveStatus.PENDING, leaveRequests.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test void concurrentDifferentRequestsCannotExceedQuota() throws Exception {
        Employee e = employee(); request(e, 18, LeaveStatus.APPROVED);
        LeaveRequest first = request(e, 2, LeaveStatus.PENDING);
        LeaveRequest second = request(e, 2, LeaveStatus.PENDING);
        concurrent(e, first, second, 1);
        var approved = leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION, LeaveStatus.APPROVED);
        assertEquals(20, approved.stream().mapToInt(LeaveRequest::getDays).sum());
        long pendingCount = List.of(first, second).stream().filter(r ->
            leaveRequests.findById(r.getId()).orElseThrow().getStatus() == LeaveStatus.PENDING).count();
        assertEquals(1, pendingCount);
    }

    @Test void concurrentSameRequestIsApprovedOnce() throws Exception {
        Employee e = employee(); LeaveRequest r = request(e, 2, LeaveStatus.PENDING);
        concurrent(e, r, r, 1);
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(r.getId()).orElseThrow().getStatus());
        assertEquals(1, leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION, LeaveStatus.APPROVED).size());
    }

    @Test void concurrentRequestsWithinQuotaBothSucceed() throws Exception {
        Employee e = employee(); request(e, 16, LeaveStatus.APPROVED);
        LeaveRequest first = request(e, 2, LeaveStatus.PENDING);
        LeaveRequest second = request(e, 2, LeaveStatus.PENDING);
        concurrent(e, first, second, 2);
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(20, leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION,
                LeaveStatus.APPROVED).stream().mapToInt(LeaveRequest::getDays).sum());
    }

    @Test void approvalUpdatesCreationBalanceWithoutDoubleChargeOrCrossEmployeeMixing() {
        Employee e = employee(); Employee other = employee();
        request(e, 16, LeaveStatus.APPROVED);
        request(other, 20, LeaveStatus.APPROVED);
        LeaveRequest pending = request(e, 2, LeaveStatus.PENDING);
        approvals.approve(pending.getId());
        assertEquals(409, assertThrows(LeaveApprovalService.ApprovalException.class,
                () -> approvals.approve(pending.getId())).getStatus());
        assertEquals(18, leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION,
                LeaveStatus.APPROVED).stream().mapToInt(LeaveRequest::getDays).sum());
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(e.getId()); dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1)); dto.setEndDate(LocalDate.of(2026, 3, 3));
        long before = leaveRequests.count();
        assertEquals(400, controller.create(dto).getStatusCode().value());
        assertEquals(before, leaveRequests.count());
        dto.setEndDate(LocalDate.of(2026, 3, 2));
        assertEquals(200, controller.create(dto).getStatusCode().value());
        assertEquals(before + 1, leaveRequests.count());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-01-01,2026-01-18,2026-03-01,2026-03-02,true",
        "2026-01-01,2026-01-18,2026-03-01,2026-03-03,false",
        "2026-01-01,2026-01-20,2026-03-01,2026-03-01,false",
        "2025-01-01,2025-01-20,2026-03-01,2026-03-02,true",
        "2027-01-01,2027-01-20,2026-03-01,2026-03-02,true",
        "2026-01-01,2026-01-18,2026-12-30,2027-01-02,true",
        "2027-02-01,2027-02-19,2026-12-31,2027-01-02,false",
        "2024-02-28,2024-03-01,2024-03-02,2024-03-19,false"
    })
    void calendarBalanceIsRecheckedFromDatabase(String from, String to, String start, String end, boolean accepted) {
        Employee e = employee();
        LeaveRequest history = request(e, 1, LeaveStatus.APPROVED);
        dates(history, from, to);
        LeaveRequest target = request(e, 1, LeaveStatus.PENDING);
        dates(target, start, end);
        if (accepted) approvals.approve(target.getId());
        else assertEquals(409, assertThrows(LeaveApprovalService.ApprovalException.class,
                () -> approvals.approve(target.getId())).getStatus());
        LeaveRequest stored = leaveRequests.findById(target.getId()).orElseThrow();
        assertEquals(accepted ? LeaveStatus.APPROVED : LeaveStatus.PENDING, stored.getStatus());
        assertEquals(target.getEmployeeId(), stored.getEmployeeId());
        assertEquals(target.getStartDate(), stored.getStartDate());
        assertEquals(target.getEndDate(), stored.getEndDate());
        assertEquals(target.getDays(), stored.getDays());
        assertEquals(target.getType(), stored.getType());
    }

    @ParameterizedTest @CsvSource({"SICK", "UNPAID"})
    void nonVacationCanBeApprovedWithExhaustedQuota(LeaveType type) {
        Employee e = employee(); request(e, 20, LeaveStatus.APPROVED);
        LeaveRequest target = request(e, 3, LeaveStatus.PENDING);
        target.setType(type); leaveRequests.saveAndFlush(target);
        approvals.approve(target.getId());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(target.getId()).orElseThrow().getStatus());
        assertEquals(20, leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION,
                LeaveStatus.APPROVED).stream().mapToInt(LeaveRequest::getDays).sum());
    }

    @Test void approvalFiltersAndSumsPersistedHistory() {
        Employee e = employee(); Employee other = employee();
        request(e, 10, LeaveStatus.APPROVED); request(e, 8, LeaveStatus.APPROVED);
        request(other, 20, LeaveStatus.APPROVED);
        request(e, 20, LeaveStatus.PENDING); request(e, 20, LeaveStatus.REJECTED);
        for (LeaveType type : List.of(LeaveType.SICK, LeaveType.UNPAID)) {
            LeaveRequest history = request(e, 20, LeaveStatus.APPROVED);
            history.setType(type); leaveRequests.saveAndFlush(history);
        }
        LeaveRequest target = request(e, 2, LeaveStatus.PENDING);
        approvals.approve(target.getId());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(target.getId()).orElseThrow().getStatus());
        assertEquals(20, leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION,
                LeaveStatus.APPROVED).stream().mapToInt(LeaveRequest::getDays).sum());
    }

    @Test void differentEmployeesCanProgressIndependently() throws Exception {
        Employee first = employee(); Employee second = employee();
        request(first, 18, LeaveStatus.APPROVED); request(second, 18, LeaveStatus.APPROVED);
        LeaveRequest a = request(first, 2, LeaveStatus.PENDING);
        LeaveRequest b = request(second, 2, LeaveStatus.PENDING);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CompletableFuture<Integer> pid = new CompletableFuture<>();
        java.util.ArrayList<Future<?>> futures = new java.util.ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                employees.findByIdForUpdate(first.getId()).orElseThrow();
                futures.add(pool.submit(() -> new TransactionTemplate(transactionManager).execute(worker -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    pid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    return approvals.approve(a.getId());
                })));
                try {
                    int workerPid = pid.get(5, TimeUnit.SECONDS);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    boolean blocked;
                    do {
                        blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                                "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, workerPid));
                        if (!blocked) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
                    } while (!blocked && System.nanoTime() < deadline);
                    assertTrue(blocked);
                    Future<?> independent = pool.submit(() -> approvals.approve(b.getId()));
                    futures.add(independent);
                    independent.get(5, TimeUnit.SECONDS);
                    assertFalse(futures.get(0).isDone(), "First employee remains locked while second commits");
                } catch (Exception ex) { throw new IllegalStateException(ex); }
                return null;
            });
            futures.get(0).get(15, TimeUnit.SECONDS);
            for (LeaveRequest target : List.of(a,b)) {
                LeaveRequest stored = leaveRequests.findById(target.getId()).orElseThrow();
                assertEquals(LeaveStatus.APPROVED, stored.getStatus());
                assertEquals(target.getEmployeeId(), stored.getEmployeeId());
                assertEquals(20, leaveRequests.findByEmployeeIdAndTypeAndStatus(target.getEmployeeId(),
                        LeaveType.VACATION, LeaveStatus.APPROVED).stream().mapToInt(LeaveRequest::getDays).sum());
            }
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS)); }
    }

    private void dates(LeaveRequest request, String start, String end) {
        request.setStartDate(LocalDate.parse(start)); request.setEndDate(LocalDate.parse(end));
        request.setDays((int) ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1);
        leaveRequests.saveAndFlush(request);
    }

    private void concurrent(Employee e, LeaveRequest first, LeaveRequest second, int successes) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(2);
        ConcurrentLinkedQueue<Integer> workerPids = new ConcurrentLinkedQueue<>();
        java.util.ArrayList<Future<Integer>> futures = new java.util.ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                employees.findByIdForUpdate(e.getId()).orElseThrow();
                for (LeaveRequest r : List.of(first, second)) futures.add(pool.submit(() -> {
                    try {
                        return new TransactionTemplate(transactionManager).execute(workerStatus -> {
                            jdbc.execute("SET LOCAL lock_timeout = '10s'");
                            workerPids.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                            entered.countDown();
                            approvals.approve(r.getId());
                            return 200;
                        });
                    } catch (LeaveApprovalService.ApprovalException ex) { return ex.getStatus(); }
                }));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    assertEquals(2, workerPids.stream().distinct().count());
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    boolean bothBlocked = false;
                    do {
                        bothBlocked = workerPids.stream().allMatch(pid -> Boolean.TRUE.equals(jdbc.queryForObject(
                                "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid)));
                        if (!bothBlocked) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
                    } while (!bothBlocked && System.nanoTime() < deadline);
                    assertTrue(bothBlocked, "Both PostgreSQL sessions must actually wait for locks before release");
                    assertTrue(futures.stream().noneMatch(Future::isDone));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException(ex);
                }
                return null;
            });
            var results = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            assertEquals(successes, results.stream().filter(code -> code == 200).count());
            assertEquals(2 - successes, results.stream().filter(code -> code == 409).count());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private Employee employee() {
        Employee e = new Employee(); e.setName("B2 integration"); e.setAnnualQuota(20);
        return employees.saveAndFlush(e);
    }
    private LeaveRequest request(Employee e, int days, LeaveStatus status) {
        LeaveRequest r = new LeaveRequest(); r.setEmployeeId(e.getId()); r.setType(LeaveType.VACATION);
        r.setStartDate(LocalDate.of(2026, 1, 1)); r.setEndDate(r.getStartDate().plusDays(days - 1));
        r.setDays(days); r.setStatus(status); return leaveRequests.saveAndFlush(r);
    }
}
