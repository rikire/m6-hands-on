/**
 * search_books: targeted lookup by author and year range.
 *
 * Expected argument format: {@code author|year_from|year_to}
 * where empty parts mean "no constraint".  Example:
 * <pre>
 *   search_books("Ursula K. Le Guin|1970|1979")
 * </pre>
 * Any part may be left empty, e.g. {@code "|1990|2000"} for a
 * year-range query across all authors.
 *
 * Session 6B TODO: parse the three-part argument, filter the JSON
 * entries in books.json, and return the matching books as a JSON array.
 */
public class SearchBooksTool implements Tool {

    @Override
    public String name() {
        return "search_books";
    }

    @Override
    public String call(String arg) {
        return "TODO(session-6B): parse '" + arg + "' and filter books.json";
    }
}
