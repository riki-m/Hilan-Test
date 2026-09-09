package com.example.leavemanagement.service;

import com.example.leavemanagement.model.*;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;

@Service
public class LeaveApprovalService {
    private final EmployeeRepository employees;
    private final LeaveRequestRepository requests;

    public LeaveApprovalService(EmployeeRepository employees, LeaveRequestRepository requests) {
        this.employees = employees;
        this.requests = requests;
    }

    public static class ApprovalException extends RuntimeException {
        private final int status;
        public ApprovalException(int status, String message) { super(message); this.status = status; }
        public int getStatus() { return status; }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaveRequest approve(long id) {
        // Read only the owner ID before locking, avoiding a stale managed request/employee.
        Long employeeId = requests.findEmployeeIdByRequestId(id)
                .orElseThrow(() -> new ApprovalException(404, "Leave request not found"));
        Employee employee = employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ApprovalException(409, "Employee no longer exists"));
        // Every approval for this employee takes the same lock before reading mutable state.
        LeaveRequest request = requests.findById(id)
                .orElseThrow(() -> new ApprovalException(404, "Leave request not found"));
        if (!employeeId.equals(request.getEmployeeId())) {
            throw new ApprovalException(409, "Request owner changed; retry approval");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ApprovalException(409, "Only pending requests can be approved");
        }
        if (request.getType() == null || request.getStartDate() == null || request.getEndDate() == null
                || request.getStartDate().isAfter(request.getEndDate())) {
            throw new ApprovalException(409, "Request has invalid leave dates or type");
        }
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        if (days > Integer.MAX_VALUE || request.getDays() != days) {
            throw new ApprovalException(409, "Request day count is inconsistent with its dates");
        }
        if (request.getType() == LeaveType.VACATION && !VacationBalancePolicy.fits(employee.getAnnualQuota(),
                request.getStartDate(), request.getEndDate(), requests.findByEmployeeIdAndTypeAndStatus(
                        employeeId, LeaveType.VACATION, LeaveStatus.APPROVED))) {
            throw new ApprovalException(409, "Not enough vacation balance");
        }
        request.setStatus(LeaveStatus.APPROVED);
        return requests.saveAndFlush(request);
    }
}
