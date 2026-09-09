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

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

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

    @Test
    void create_WithinQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
        LeaveRequest created = assertInstanceOf(LeaveRequest.class, result.getBody());
        LeaveRequest persisted = leaveRequests.findById(created.getId()).orElseThrow();
        assertEquals(LeaveStatus.PENDING, persisted.getStatus());
        assertEquals(3, persisted.getDays());
        assertEquals(emp.getId(), persisted.getEmployeeId());
    }

    @Test
    void create_ExceedingRemainingQuota_IsRejectedWithoutSaving() {
        Employee employee = new Employee();
        employee.setName("Quota Regression");
        employee.setAnnualQuota(20);
        employees.save(employee);

        LeaveRequest previous = new LeaveRequest();
        previous.setEmployeeId(employee.getId());
        previous.setType(LeaveType.VACATION);
        previous.setStatus(LeaveStatus.APPROVED);
        previous.setStartDate(LocalDate.of(2026, 1, 1));
        previous.setEndDate(LocalDate.of(2026, 1, 18));
        previous.setDays(18);
        leaveRequests.saveAndFlush(previous);
        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(employee.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3));

        ResponseEntity<?> result = controller.create(dto);

        assertEquals(400, result.getStatusCode().value());
        assertEquals("Not enough vacation balance", result.getBody());
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void balanceQuery_OnlyIncludesSelectedEmployeesApprovedVacation() {
        Employee employee = new Employee();
        employee.setName("B1 filter target");
        employee.setAnnualQuota(20);
        employees.saveAndFlush(employee);
        Employee other = new Employee();
        other.setName("B1 other employee");
        other.setAnnualQuota(20);
        employees.saveAndFlush(other);

        LeaveRequest included = saveHistory(employee, LeaveType.VACATION, LeaveStatus.APPROVED);
        saveHistory(other, LeaveType.VACATION, LeaveStatus.APPROVED);
        saveHistory(employee, LeaveType.SICK, LeaveStatus.APPROVED);
        saveHistory(employee, LeaveType.UNPAID, LeaveStatus.APPROVED);
        saveHistory(employee, LeaveType.VACATION, LeaveStatus.PENDING);
        saveHistory(employee, LeaveType.VACATION, LeaveStatus.REJECTED);

        var matches = leaveRequests.findByEmployeeIdAndTypeAndStatus(
                employee.getId(), LeaveType.VACATION, LeaveStatus.APPROVED);
        assertEquals(java.util.List.of(included.getId()), matches.stream().map(LeaveRequest::getId).toList());
    }

    private LeaveRequest saveHistory(Employee employee, LeaveType type, LeaveStatus status) {
        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employee.getId());
        request.setType(type);
        request.setStatus(status);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 18));
        request.setDays(18);
        return leaveRequests.saveAndFlush(request);
    }
}
