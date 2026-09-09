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
    List<LeaveRequest> findAllByOrderByStartDateDesc();

    // Keep the existing case-sensitive LIKE/wildcard behavior, but bind user input as data.
    @Query(value = "SELECT * FROM leave_requests WHERE employee_id IN " +
            "(SELECT id FROM employees WHERE name LIKE CONCAT('%', :name, '%'))", nativeQuery = true)
    List<LeaveRequest> searchByEmployeeName(@Param("name") String name);

    @Query("select r.employeeId from LeaveRequest r where r.id = :id")
    Optional<Long> findEmployeeIdByRequestId(@Param("id") Long id);

    List<LeaveRequest> findByEmployeeIdAndTypeAndStatus(Long employeeId, LeaveType type, LeaveStatus status);
}
