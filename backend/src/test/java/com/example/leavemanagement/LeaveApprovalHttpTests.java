package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.service.LeaveRequestService;
import com.example.leavemanagement.model.*;
import com.example.leavemanagement.repository.*;
import com.example.leavemanagement.service.LeaveApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeaveApprovalHttpTests {
    @Test void approvedJsonRetainsNumericStatus() throws Exception {
        var service=mock(LeaveApprovalService.class);
        LeaveRequest r=new LeaveRequest();r.setId(7L);r.setEmployeeId(1L);r.setStatus(LeaveStatus.APPROVED);r.setType(LeaveType.VACATION);
        when(service.approve(7L)).thenReturn(r);
        var mvc=MockMvcBuilders.standaloneSetup(new LeaveRequestsController(mock(LeaveRequestService.class),service)).build();
        mvc.perform(post("/api/leave-requests/7/approve")).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7)).andExpect(jsonPath("$.status").value(1));
        verify(service).approve(7L);
    }
    @ParameterizedTest @CsvSource({"404,Leave request not found","409,Only pending requests can be approved","409,Not enough vacation balance"})
    void mapsBusinessErrors(int code,String message) throws Exception {
        var service=mock(LeaveApprovalService.class);
        when(service.approve(7L)).thenThrow(new LeaveApprovalService.ApprovalException(code,message));
        var mvc=MockMvcBuilders.standaloneSetup(new LeaveRequestsController(mock(LeaveRequestService.class),service)).build();
        mvc.perform(post("/api/leave-requests/7/approve")).andExpect(status().is(code)).andExpect(content().string(message));
    }
}
