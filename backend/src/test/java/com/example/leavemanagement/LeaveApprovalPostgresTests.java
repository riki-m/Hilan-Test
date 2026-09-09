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
        concurrent(e, first, second);
        var approved = leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION, LeaveStatus.APPROVED);
        assertEquals(20, approved.stream().mapToInt(LeaveRequest::getDays).sum());
        long pendingCount = List.of(first, second).stream().filter(r ->
            leaveRequests.findById(r.getId()).orElseThrow().getStatus() == LeaveStatus.PENDING).count();
        assertEquals(1, pendingCount);
    }

    @Test void concurrentSameRequestIsApprovedOnce() throws Exception {
        Employee e = employee(); LeaveRequest r = request(e, 2, LeaveStatus.PENDING);
        concurrent(e, r, r);
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(r.getId()).orElseThrow().getStatus());
        assertEquals(1, leaveRequests.findByEmployeeIdAndTypeAndStatus(e.getId(), LeaveType.VACATION, LeaveStatus.APPROVED).size());
    }

    private void concurrent(Employee e, LeaveRequest first, LeaveRequest second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(2);
        java.util.ArrayList<Future<Integer>> futures = new java.util.ArrayList<>();
        try {
            // Hold the real employee lock while both independent workers enter approval.
            new TransactionTemplate(transactionManager).execute(status -> {
                employees.findByIdForUpdate(e.getId()).orElseThrow();
                for (LeaveRequest r : List.of(first, second)) futures.add(pool.submit(() -> {
                    entered.countDown();
                    try { approvals.approve(r.getId()); return 200; }
                    catch (LeaveApprovalService.ApprovalException ex) { return ex.getStatus(); }
                }));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    for (Future<Integer> f : futures)
                        assertThrows(TimeoutException.class, () -> f.get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException(ex);
                }
                return null;
            });
            var results = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 409).count());
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
