package com.example.api;

import com.example.core.Loan;
import com.example.core.LoanRegistry;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only analytics over the loan registry. Kept separate from
 * {@link LibraryService} so query-heavy callers can be routed at a
 * different layer.
 */
public class ReportService {

    private final LoanRegistry registry;

    public ReportService(LoanRegistry registry) {
        this.registry = registry;
    }

    public int totalLoans() {
        return registry.allLoans().size();
    }

    public List<Loan> overdue(LocalDate today) {
        return registry.overdueOn(today);
    }

    /**
     * Loans per genre in the entire history. Useful for the monthly
     * committee report.
     */
    public Map<String, Integer> loansByGenre() {
        Map<String, Integer> counts = new HashMap<>();
        for (Loan l : registry.allLoans()) {
            String g = l.getBook().getGenre();
            counts.merge(g, 1, Integer::sum);
        }
        return counts;
    }
}
