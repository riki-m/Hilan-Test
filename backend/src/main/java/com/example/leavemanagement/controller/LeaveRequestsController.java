package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.service.LeaveApprovalService;
import com.example.leavemanagement.service.LeaveRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {
    private final LeaveRequestService requestService;
    private final LeaveApprovalService approvalService;

    public LeaveRequestsController(LeaveRequestService requestService, LeaveApprovalService approvalService) {
        this.requestService = requestService;
        this.approvalService = approvalService;
    }

    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAll() {
        return ResponseEntity.ok(requestService.getAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<LeaveRequest>> search(@RequestParam String name) {
        return ResponseEntity.ok(requestService.search(name));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLeaveRequestDto dto) {
        try {
            return ResponseEntity.ok(requestService.create(dto));
        } catch (LeaveRequestService.CreationException ex) {
            int status = ex.getFailure() == LeaveRequestService.Failure.EMPLOYEE_NOT_FOUND ? 404 : 400;
            return ResponseEntity.status(status).body(ex.getMessage());
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") long id) {
        try {
            return ResponseEntity.ok(approvalService.approve(id));
        } catch (LeaveApprovalService.ApprovalException ex) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
        }
    }
}
