package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.leavemanagement.service.LeaveApprovalService;
import com.example.leavemanagement.service.VacationBalancePolicy;
import java.time.temporal.ChronoUnit;
import java.util.List;

// NOTE: This controller was written quickly for a POC.
// It does data access, business logic and validation all in one place.
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final EmployeeRepository employeeRepository;
    private final LeaveApprovalService approvalService;
    private final LeaveRequestRepository leaveRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public LeaveRequestsController(EmployeeRepository employeeRepository,
                                   LeaveRequestRepository leaveRequestRepository, LeaveApprovalService approvalService) {
        this.employeeRepository = employeeRepository;
        this.approvalService = approvalService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // GET /api/leave-requests
    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAll() {
        List<LeaveRequest> all = leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
        return ResponseEntity.ok(all);
    }

    // GET /api/leave-requests/search?name=Dana
    // Lets the UI quickly find requests by employee name.
    @GetMapping("/search")
    public ResponseEntity<List<LeaveRequest>> search(@RequestParam String name) {
        // Build a quick query to filter by the employee name.
        String sql = "SELECT * FROM leave_requests WHERE employee_id IN " +
                "(SELECT id FROM employees WHERE name LIKE '%" + name + "%')";

        @SuppressWarnings("unchecked")
        List<LeaveRequest> results = entityManager
                .createNativeQuery(sql, LeaveRequest.class)
                .getResultList();

        return ResponseEntity.ok(results);
    }

    // POST /api/leave-requests
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLeaveRequestDto dto) {
        if (dto == null || dto.getEmployeeId() == null || dto.getType() == null
                || dto.getStartDate() == null || dto.getEndDate() == null
                || dto.getStartDate().isAfter(dto.getEndDate())) {
            return ResponseEntity.badRequest().body("Employee, leave type and a valid date range are required");
        }
        long requestedDays = ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        if (requestedDays > Integer.MAX_VALUE) {
            return ResponseEntity.badRequest().body("Date range is too long");
        }
        Employee employee = employeeRepository.findById(dto.getEmployeeId()).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(404).body("Employee not found");
        }

        int days = (int) requestedDays;
        if (dto.getType() == LeaveType.VACATION) {
            List<LeaveRequest> approved = leaveRequestRepository
                    .findByEmployeeIdAndTypeAndStatus(dto.getEmployeeId(), LeaveType.VACATION, LeaveStatus.APPROVED);
            if (!VacationBalancePolicy.fits(employee.getAnnualQuota(), dto.getStartDate(), dto.getEndDate(), approved)) {
                return ResponseEntity.badRequest().body("Not enough vacation balance");
            }
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        leaveRequestRepository.save(request);

        return ResponseEntity.ok(request);
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
