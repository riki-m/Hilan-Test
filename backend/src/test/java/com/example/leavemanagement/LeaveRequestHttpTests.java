package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// In-process HTTP mapping/JSON checks. No network server or database is started.
class LeaveRequestHttpTests {
    private LeaveRequestRepository requests;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        requests = mock(LeaveRequestRepository.class);
        Employee employee = new Employee();
        employee.setAnnualQuota(20);
        when(employees.findById(1L)).thenReturn(Optional.of(employee));
        LeaveRequest previous = new LeaveRequest();
        previous.setStartDate(LocalDate.of(2026, 1, 1));
        previous.setEndDate(LocalDate.of(2026, 1, 18));
        when(requests.findByEmployeeIdAndTypeAndStatus(1L, LeaveType.VACATION, LeaveStatus.APPROVED))
                .thenReturn(List.of(previous));
        mvc = MockMvcBuilders.standaloneSetup(new LeaveRequestsController(employees, requests, mock(com.example.leavemanagement.service.LeaveApprovalService.class))).build();
    }

    @Test
    void rejectsExcessAs400WithoutSaving() throws Exception {
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                .content(payload("2026-03-03")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not enough vacation balance"));
        verify(requests, never()).save(any());
        verify(requests).findByEmployeeIdAndTypeAndStatus(1L, LeaveType.VACATION, LeaveStatus.APPROVED);
    }

    @Test
    void acceptsExactBalanceWithPendingJson() throws Exception {
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                .content(payload("2026-03-02")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.type").value(0))
                .andExpect(jsonPath("$.employeeId").value(1));
        verify(requests).save(any(LeaveRequest.class));
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requests);
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                .content(payload("invalid-date"))).andExpect(status().isBadRequest());
        verifyNoInteractions(requests);
    }

    private String payload(String end) {
        return "{\"employeeId\":1,\"type\":0,\"startDate\":\"2026-03-01\",\"endDate\":\""
                + end + "\"}";
    }
}
