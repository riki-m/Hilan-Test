package com.example.leavemanagement.service;

import com.example.leavemanagement.model.LeaveRequest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Calendar-year quota, inclusive dates, no carry-over. Shared by creation and approval. */
public final class VacationBalancePolicy {
    private VacationBalancePolicy() { }

    public static boolean fits(int quota, LocalDate start, LocalDate end, List<LeaveRequest> approved) {
        for (int year = start.getYear(); year <= end.getYear(); year++) {
            LocalDate first = LocalDate.of(year, 1, 1);
            LocalDate last = LocalDate.of(year, 12, 31);
            long used = approved.stream().mapToLong(r -> daysWithin(r.getStartDate(), r.getEndDate(), first, last)).sum();
            if (used + daysWithin(start, end, first, last) > quota) return false;
        }
        return true;
    }

    private static long daysWithin(LocalDate start, LocalDate end, LocalDate first, LocalDate last) {
        LocalDate from = start.isAfter(first) ? start : first;
        LocalDate to = end.isBefore(last) ? end : last;
        return from.isAfter(to) ? 0 : ChronoUnit.DAYS.between(from, to) + 1;
    }
}
