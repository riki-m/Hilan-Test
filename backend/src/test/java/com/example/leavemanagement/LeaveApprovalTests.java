package com.example.leavemanagement;

import com.example.leavemanagement.model.*;
import com.example.leavemanagement.repository.*;
import com.example.leavemanagement.service.LeaveApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveApprovalTests {
    EmployeeRepository employees;
    LeaveRequestRepository requests;
    LeaveApprovalService service;
    LeaveRequest pending;

    @BeforeEach void setup() {
        employees = mock(EmployeeRepository.class);
        requests = mock(LeaveRequestRepository.class);
        service = new LeaveApprovalService(employees, requests);
        Employee employee = new Employee(); employee.setAnnualQuota(20);
        pending = leave("2026-03-01", "2026-03-02"); pending.setId(7L); pending.setEmployeeId(1L);
        pending.setType(LeaveType.VACATION); pending.setStatus(LeaveStatus.PENDING);
        when(requests.findEmployeeIdByRequestId(7L)).thenReturn(Optional.of(1L));
        when(employees.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(requests.findById(7L)).thenReturn(Optional.of(pending));
        when(requests.findByEmployeeIdAndTypeAndStatus(1L, LeaveType.VACATION, LeaveStatus.APPROVED)).thenReturn(List.of());
        when(requests.saveAndFlush(pending)).thenReturn(pending);
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
    void rechecksCalendarBalance(String from, String to, String start, String end, boolean accepted) {
        pending.setStartDate(LocalDate.parse(start)); pending.setEndDate(LocalDate.parse(end));
        pending.setDays((int)ChronoUnit.DAYS.between(pending.getStartDate(), pending.getEndDate())+1);
        when(requests.findByEmployeeIdAndTypeAndStatus(1L, LeaveType.VACATION, LeaveStatus.APPROVED))
            .thenReturn(List.of(leave(from,to)));
        if (accepted) { assertSame(pending, service.approve(7L)); assertEquals(LeaveStatus.APPROVED,pending.getStatus()); verify(requests).saveAndFlush(pending); }
        else reject(409);
    }

    @Test void locksOwnerBeforeReadingStateAndBalance() {
        service.approve(7L);
        var order = inOrder(employees,requests);
        order.verify(requests).findEmployeeIdByRequestId(7L);
        order.verify(employees).findByIdForUpdate(1L);
        order.verify(requests).findById(7L);
        order.verify(requests).findByEmployeeIdAndTypeAndStatus(1L,LeaveType.VACATION,LeaveStatus.APPROVED);
        order.verify(requests).saveAndFlush(pending);
    }
    @Test void missingRequest() { when(requests.findEmployeeIdByRequestId(7L)).thenReturn(Optional.empty()); reject(404); verifyNoInteractions(employees); }
    @Test void missingEmployee() { when(employees.findByIdForUpdate(1L)).thenReturn(Optional.empty()); reject(409); }
    @Test void requestDeletedWhileWaiting() { when(requests.findById(7L)).thenReturn(Optional.empty()); reject(404); }
    @Test void changedOwner() { pending.setEmployeeId(2L); reject(409); }
    @ParameterizedTest @CsvSource({"APPROVED","REJECTED"})
    void rejectsTerminalStatus(LeaveStatus status) { pending.setStatus(status); reject(409); }
    @ParameterizedTest @CsvSource({"SICK","UNPAID"})
    void nonVacationDoesNotConsumeQuota(LeaveType type) {
        pending.setType(type); service.approve(7L);
        assertEquals(LeaveStatus.APPROVED,pending.getStatus());
        verify(requests,never()).findByEmployeeIdAndTypeAndStatus(any(),any(),any());
    }
    @ParameterizedTest @CsvSource({"type","start","end","reversed","days"})
    void invalidStoredData(String field) {
        switch(field) {
            case "type" -> pending.setType(null);
            case "start" -> pending.setStartDate(null);
            case "end" -> pending.setEndDate(null);
            case "reversed" -> pending.setStartDate(LocalDate.of(2026,4,1));
            case "days" -> pending.setDays(99);
        }
        reject(409);
    }
    @Test void databaseFailurePropagatesRatherThanReturningSuccess() {
        when(requests.saveAndFlush(pending)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("test failure"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.approve(7L));
        // Database rollback is verified only by the separate PostgreSQL test.
    }
    private void reject(int code) {
        LeaveStatus original=pending.getStatus();
        var ex=assertThrows(LeaveApprovalService.ApprovalException.class,()->service.approve(7L));
        assertEquals(code,ex.getStatus()); assertEquals(original,pending.getStatus());
        verify(requests,never()).saveAndFlush(any());
    }
    private LeaveRequest leave(String start,String end) {
        LeaveRequest r=new LeaveRequest(); r.setStartDate(LocalDate.parse(start));r.setEndDate(LocalDate.parse(end));
        r.setDays((int)ChronoUnit.DAYS.between(r.getStartDate(),r.getEndDate())+1); return r;
    }
}
