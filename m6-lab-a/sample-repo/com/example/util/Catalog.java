package com.example.util;

import com.example.core.Book;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory catalogue keyed by ISBN. Provides very simple search
 * primitives; a real system would delegate to a proper index.
 */
public class Catalog {

    private final Map<String, Book> byIsbn = new HashMap<>();

    public void add(Book b) {
        byIsbn.put(b.getIsbn(), b);
    }

    public Optional<Book> findByIsbn(String isbn) {
        return Optional.ofNullable(byIsbn.get(isbn));
    }

    /**
     * Case-insensitive substring search over title. Not intended for
     * production use; there is no tokenisation.
     */
    public List<Book> searchByTitle(String needle) {
        String lower = needle.toLowerCase();
        List<Book> out = new ArrayList<>();
        for (Book b : byIsbn.values()) {
            if (b.getTitle().toLowerCase().contains(lower)) {
                out.add(b);
            }
        }
        return out;
    }

    public List<Book> byAuthor(String author) {
        List<Book> out = new ArrayList<>();
        for (Book b : byIsbn.values()) {
            if (b.getAuthor().equalsIgnoreCase(author)) {
                out.add(b);
            }
        }
        return out;
    }

    public int size() {
        return byIsbn.size();
    }
}
