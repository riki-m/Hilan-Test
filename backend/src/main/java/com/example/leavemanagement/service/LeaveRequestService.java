package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.*;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employees, LeaveRequestRepository requests) {
        this.employeeRepository = employees;
        this.leaveRequestRepository = requests;
    }

    public enum Failure { INVALID_INPUT, EMPLOYEE_NOT_FOUND, INSUFFICIENT_BALANCE }

    public static class CreationException extends RuntimeException {
        private final Failure failure;
        public CreationException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }
        public Failure getFailure() { return failure; }
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getAll() {
        return leaveRequestRepository.findAllByOrderByStartDateDesc();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> search(String name) {
        return leaveRequestRepository.searchByEmployeeName(name);
    }

    @Transactional
    public LeaveRequest create(CreateLeaveRequestDto dto) {
        if (dto == null || dto.getEmployeeId() == null || dto.getType() == null
                || dto.getStartDate() == null || dto.getEndDate() == null
                || dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new CreationException(Failure.INVALID_INPUT, "Employee, leave type and a valid date range are required");
        }
        long requestedDays = ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        if (requestedDays > Integer.MAX_VALUE) {
            throw new CreationException(Failure.INVALID_INPUT, "Date range is too long");
        }
        Employee employee = employeeRepository.findById(dto.getEmployeeId()).orElse(null);
        if (employee == null) {
            throw new CreationException(Failure.EMPLOYEE_NOT_FOUND, "Employee not found");
        }

        int days = (int) requestedDays;
        if (dto.getType() == LeaveType.VACATION) {
            List<LeaveRequest> approved = leaveRequestRepository
                    .findByEmployeeIdAndTypeAndStatus(dto.getEmployeeId(), LeaveType.VACATION, LeaveStatus.APPROVED);
            if (!VacationBalancePolicy.fits(employee.getAnnualQuota(), dto.getStartDate(), dto.getEndDate(), approved)) {
                throw new CreationException(Failure.INSUFFICIENT_BALANCE, "Not enough vacation balance");
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

        return request;
    }
}
