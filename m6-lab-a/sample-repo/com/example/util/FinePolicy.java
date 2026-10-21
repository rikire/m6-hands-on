package com.example.util;

import com.example.core.Loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Late-fee policy. INR-denominated; caps at 500 rupees.
 * The math is intentionally simple: linear per day after the grace period.
 */
public interface FinePolicy {

    BigDecimal computeFine(Loan loan, LocalDate today);

    static FinePolicy defaultPolicy() {
        return new StandardFinePolicy();
    }
}

class StandardFinePolicy implements FinePolicy {

    private static final BigDecimal PER_DAY = new BigDecimal("5.00");
    private static final BigDecimal CAP = new BigDecimal("500.00");

    @Override
    public BigDecimal computeFine(Loan loan, LocalDate today) {
        if (!loan.isOverdue(today)) {
            return BigDecimal.ZERO;
        }
        long daysLate = ChronoUnit.DAYS.between(loan.getBorrowedOn(), today)
                - Loan.DEFAULT_TERM_DAYS;
        BigDecimal fine = PER_DAY.multiply(BigDecimal.valueOf(daysLate));
        return fine.compareTo(CAP) > 0 ? CAP : fine;
    }
}
