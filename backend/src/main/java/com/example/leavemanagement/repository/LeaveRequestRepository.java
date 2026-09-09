package com.example.leavemanagement.repository;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    @Query("select r.employeeId from LeaveRequest r where r.id = :id")
    Optional<Long> findEmployeeIdByRequestId(@Param("id") Long id);

    List<LeaveRequest> findByEmployeeIdAndTypeAndStatus(Long employeeId, LeaveType type, LeaveStatus status);
}
