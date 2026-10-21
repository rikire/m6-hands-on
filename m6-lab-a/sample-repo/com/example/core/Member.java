package com.example.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A library member. Tracks currently held loans; historical loans
 * live in the {@link com.example.core.LoanRegistry} instead.
 */
public class Member {

    private final String memberId;
    private final String name;
    private final LocalDate joinedOn;
    private final List<Loan> activeLoans = new ArrayList<>();

    public Member(String memberId, String name, LocalDate joinedOn) {
        this.memberId = memberId;
        this.name = name;
        this.joinedOn = joinedOn;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getJoinedOn() {
        return joinedOn;
    }

    public List<Loan> getActiveLoans() {
        return Collections.unmodifiableList(activeLoans);
    }

    /**
     * Attach a loan to this member. Enforces the standard cap of 5 open loans.
     */
    public void addLoan(Loan loan) {
        if (activeLoans.size() >= 5) {
            throw new IllegalStateException("member " + memberId + " has too many open loans");
        }
        activeLoans.add(loan);
    }

    public boolean returnLoan(String isbn) {
        return activeLoans.removeIf(l -> l.getBook().getIsbn().equals(isbn));
    }
}
