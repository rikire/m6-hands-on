package com.example.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Append-only history of every loan (open or closed) the library has issued.
 * Used for reporting; not the source of truth for what's currently on loan.
 */
public class LoanRegistry {

    private final List<Loan> history = new ArrayList<>();

    public void record(Loan loan) {
        history.add(loan);
    }

    public List<Loan> allLoans() {
        return Collections.unmodifiableList(history);
    }

    public List<Loan> loansFor(String memberId) {
        List<Loan> out = new ArrayList<>();
        for (Loan l : history) {
            if (l.getBorrower().getMemberId().equals(memberId)) {
                out.add(l);
            }
        }
        return out;
    }

    public List<Loan> overdueOn(LocalDate day) {
        List<Loan> out = new ArrayList<>();
        for (Loan l : history) {
            if (l.isOverdue(day)) {
                out.add(l);
            }
        }
        return out;
    }
}
