package com.example.api;

import com.example.core.Book;
import com.example.core.Loan;
import com.example.core.LoanRegistry;
import com.example.core.Member;
import com.example.util.Catalog;
import com.example.util.FinePolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Facade over the catalogue, member registry, and loan history.
 * This is the class most clients of the library actually call.
 */
public class LibraryService {

    private final Catalog catalog;
    private final LoanRegistry registry;
    private final FinePolicy finePolicy;

    public LibraryService(Catalog catalog, LoanRegistry registry, FinePolicy finePolicy) {
        this.catalog = catalog;
        this.registry = registry;
        this.finePolicy = finePolicy;
    }

    /**
     * Issue a loan for the given ISBN to the given member.
     * Throws if the book is not in the catalogue or the member is at cap.
     */
    public Loan borrow(String memberId, Member member, String isbn, LocalDate today) {
        Book b = catalog.findByIsbn(isbn)
                .orElseThrow(() -> new IllegalArgumentException("unknown book: " + isbn));
        Loan loan = new Loan(b, member, today);
        member.addLoan(loan);
        registry.record(loan);
        return loan;
    }

    public BigDecimal fineFor(Loan loan, LocalDate today) {
        return finePolicy.computeFine(loan, today);
    }

    public List<Book> search(String needle) {
        return catalog.searchByTitle(needle);
    }
}
