package com.example.core;

import java.time.Year;
import java.util.Objects;

/**
 * A single book in the catalogue.
 * Books are treated as immutable value objects; loans and reservations
 * live on {@link Loan} and {@link Member} respectively.
 */
public class Book {

    private final String isbn;
    private final String title;
    private final String author;
    private final Year publishedIn;
    private final String genre;

    public Book(String isbn, String title, String author, Year publishedIn, String genre) {
        this.isbn = Objects.requireNonNull(isbn, "isbn");
        this.title = Objects.requireNonNull(title, "title");
        this.author = Objects.requireNonNull(author, "author");
        this.publishedIn = Objects.requireNonNull(publishedIn, "publishedIn");
        this.genre = genre == null ? "unspecified" : genre;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Year getPublishedIn() {
        return publishedIn;
    }

    public String getGenre() {
        return genre;
    }

    /**
     * Two books are equal iff they share an ISBN.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;
        return isbn.equals(other.isbn);
    }

    @Override
    public int hashCode() {
        return isbn.hashCode();
    }

    @Override
    public String toString() {
        return "Book[" + isbn + " " + title + " by " + author + "]";
    }
}
