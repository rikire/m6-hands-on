package com.example.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A single book loan. Loans default to a 21-day term.
 * A loan is either open (returnedOn == null) or closed.
 */
public class Loan {

    public static final int DEFAULT_TERM_DAYS = 21;

    private final Book book;
    private final Member borrower;
    private final LocalDate borrowedOn;
    private LocalDate returnedOn;

    public Loan(Book book, Member borrower, LocalDate borrowedOn) {
        this.book = book;
        this.borrower = borrower;
        this.borrowedOn = borrowedOn;
    }

    public Book getBook() {
        return book;
    }

    public Member getBorrower() {
        return borrower;
    }

    public LocalDate getBorrowedOn() {
        return borrowedOn;
    }

    public LocalDate getReturnedOn() {
        return returnedOn;
    }

    public void markReturned(LocalDate day) {
        this.returnedOn = day;
    }

    /**
     * True if the loan is currently past the standard term and has not
     * been returned.
     */
    public boolean isOverdue(LocalDate today) {
        if (returnedOn != null) {
            return false;
        }
        return ChronoUnit.DAYS.between(borrowedOn, today) > DEFAULT_TERM_DAYS;
    }
}
