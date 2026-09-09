package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Exercises the actual controller with repository doubles; no database or Docker.
class LeaveBalanceTests {
    @ParameterizedTest
    @CsvSource({"18, 3, false", "18, 2, true", "18, 1, true", "20, 1, false", "0, 21, false", "0, 20, true"})
    void create_RespectsRemainingBalance(int usedDays, int requestedDays, boolean accepted) {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        LeaveRequestsController controller = new LeaveRequestsController(employees, requests);
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setAnnualQuota(20);
        when(employees.findById(1L)).thenReturn(Optional.of(employee));
        LeaveRequest previous = new LeaveRequest();
        previous.setDays(usedDays);
        previous.setStartDate(LocalDate.of(2026, 1, 1));
        previous.setEndDate(previous.getStartDate().plusDays(Math.max(usedDays, 1) - 1));
        when(requests.findByEmployeeIdAndTypeAndStatus(1L, LeaveType.VACATION, LeaveStatus.APPROVED))
                .thenReturn(usedDays == 0 ? List.of() : List.of(previous));

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(1L);
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(dto.getStartDate().plusDays(requestedDays - 1));
        var result = controller.create(dto);

        if (accepted) {
            assertEquals(HttpStatus.OK, result.getStatusCode());
            LeaveRequest created = assertInstanceOf(LeaveRequest.class, result.getBody());
            assertEquals(requestedDays, created.getDays());
            assertEquals(LeaveStatus.PENDING, created.getStatus());
            verify(requests).save(created);
        } else {
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("Not enough vacation balance", result.getBody());
            verify(requests, never()).save(any(LeaveRequest.class));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2025-01-01,2025-01-20,2026-03-01,2026-03-03,20,true",
            "2027-01-01,2027-01-20,2026-03-01,2026-03-03,20,true",
            "2025-12-20,2026-01-02,2026-03-01,2026-03-19,20,false",
            "2025-12-20,2026-01-02,2026-03-01,2026-03-18,20,true",
            "2026-01-01,2026-01-18,2026-12-30,2027-01-02,20,true",
            "2027-02-01,2027-02-19,2026-12-31,2027-01-02,20,false",
            "2024-02-28,2024-03-01,2024-03-02,2024-03-19,20,false"
    })
    void create_ChecksEachCalendarYear(String from, String to, String start, String end,
                                      int quota, boolean accepted) {
        check(List.of(approved(from, to)), LeaveType.VACATION, start, end, quota, accepted);
    }

    @Test
    void create_SumsMultipleApprovedRequests() {
        check(List.of(approved("2026-01-01", "2026-01-10"),
                approved("2026-02-01", "2026-02-08")), LeaveType.VACATION,
                "2026-03-01", "2026-03-03", 20, false);
    }

    @Test
    void create_AllowsOtherLeaveTypesWithExhaustedVacation() {
        for (LeaveType type : List.of(LeaveType.SICK, LeaveType.UNPAID)) {
            check(List.of(approved("2026-01-01", "2026-01-20")), type,
                    "2026-03-01", "2026-03-03", 20, true);
        }
    }

    @Test
    void create_RejectsReversedDates() {
        check(List.of(), LeaveType.VACATION, "2026-03-03", "2026-03-01", 20, false);
    }

    @Test
    void create_SingleDayConsumesOneDay() {
        check(List.of(), LeaveType.VACATION, "2026-03-01", "2026-03-01", 1, true);
        check(List.of(), LeaveType.VACATION, "2026-03-01", "2026-03-01", 0, false);
    }

    @Test
    void create_RejectsMissingFieldsBeforeRepositoryAccess() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        var controller = new LeaveRequestsController(employees, requests);
        assertEquals(400, controller.create(null).getStatusCode().value());
        for (int missing = 0; missing < 4; missing++) {
            CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
            dto.setEmployeeId(missing == 0 ? null : 1L);
            dto.setType(missing == 1 ? null : LeaveType.VACATION);
            dto.setStartDate(missing == 2 ? null : LocalDate.of(2026, 1, 1));
            dto.setEndDate(missing == 3 ? null : LocalDate.of(2026, 1, 2));
            assertEquals(400, controller.create(dto).getStatusCode().value());
        }
        verifyNoInteractions(employees, requests);
    }

    @Test
    void create_RejectsUnrepresentableDayCount() {
        check(List.of(), LeaveType.VACATION, "0001-01-01", "+999999999-12-31", 20, false);
    }

    @Test
    void create_UnknownEmployeeReturns404WithoutSaving() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        when(employees.findById(1L)).thenReturn(Optional.empty());
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(1L);
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(dto.getStartDate());
        assertEquals(404, new LeaveRequestsController(employees, requests)
                .create(dto).getStatusCode().value());
        verifyNoInteractions(requests);
    }

    private LeaveRequest approved(String from, String to) {
        LeaveRequest request = new LeaveRequest();
        request.setStartDate(LocalDate.parse(from));
        request.setEndDate(LocalDate.parse(to));
        request.setDays((int) java.time.temporal.ChronoUnit.DAYS.between(
                request.getStartDate(), request.getEndDate()) + 1);
        return request;
    }

    private void check(List<LeaveRequest> history, LeaveType type, String start, String end,
                       int quota, boolean accepted) {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        Employee employee = new Employee();
        employee.setAnnualQuota(quota);
        when(employees.findById(1L)).thenReturn(Optional.of(employee));
        when(requests.findByEmployeeIdAndTypeAndStatus(1L, LeaveType.VACATION, LeaveStatus.APPROVED))
                .thenReturn(history);
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(1L);
        dto.setType(type);
        dto.setStartDate(LocalDate.parse(start));
        dto.setEndDate(LocalDate.parse(end));
        var response = new LeaveRequestsController(employees, requests).create(dto);
        assertEquals(accepted ? 200 : 400, response.getStatusCode().value());
        if (accepted) {
            LeaveRequest saved = assertInstanceOf(LeaveRequest.class, response.getBody());
            assertEquals(LeaveStatus.PENDING, saved.getStatus());
            assertEquals(java.time.temporal.ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1,
                    saved.getDays());
            assertEquals(dto.getEmployeeId(), saved.getEmployeeId());
            assertEquals(type, saved.getType());
            verify(requests).save(saved);
        } else {
            verify(requests, never()).save(any(LeaveRequest.class));
        }
    }
}
