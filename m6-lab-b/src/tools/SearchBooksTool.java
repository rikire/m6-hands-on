import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * search_books: targeted lookup by author and year range.
 *
 * Expected argument format: {@code author|year_from|year_to}
 * where empty parts mean "no constraint".  Example:
 * <pre>
 *   search_books("Ursula K. Le Guin|1970|1979")
 * </pre>
 * Any part may be left empty, e.g. {@code "|1990|2000"} for a
 * year-range query across all authors; missing trailing parts count as
 * empty.  Year bounds are inclusive.
 *
 * The author filter is forgiving because the argument comes from an LLM:
 * every word of the query (ignoring case and punctuation) must occur in the
 * author's name, so "ursula le guin" matches "Ursula K. Le Guin".
 *
 * Returns the matching books as a JSON array.  Malformed arguments come
 * back as an "ERROR: ..." string rather than an exception, so the model
 * sees the problem as an Observation and can retry.
 */
public class SearchBooksTool implements Tool {

    private final Path file;

    public SearchBooksTool() {
        this(BookCatalog.DEFAULT_PATH);
    }

    public SearchBooksTool(Path file) {
        this.file = file;
    }

    @Override
    public String name() {
        return "search_books";
    }

    @Override
    public String call(String arg) {
        String a = arg == null ? "" : arg.strip();
        if (a.length() >= 2 && a.startsWith("\"") && a.endsWith("\"")) {
            a = a.substring(1, a.length() - 1);
        }
        String[] parts = a.split("\\|", -1);
        if (parts.length > 3) {
            return "ERROR: expected author|year_from|year_to, got " + parts.length + " parts in '" + arg + "'";
        }
        List<String> words = words(parts[0]);
        Integer from, to;
        try {
            from = year(parts, 1);
            to = year(parts, 2);
        } catch (NumberFormatException e) {
            return "ERROR: year bounds must be integers or empty, got '" + arg + "'";
        }
        return BookCatalog.load(file).stream()
                .filter(b -> words(b.author()).containsAll(words))
                .filter(b -> from == null || b.year() >= from)
                .filter(b -> to == null || b.year() <= to)
                .map(BookCatalog.Book::json)
                .collect(Collectors.joining(",\n  ", "[", "]"));
    }

    private static Integer year(String[] parts, int i) {
        if (i >= parts.length || parts[i].isBlank()) {
            return null;
        }
        return Integer.valueOf(parts[i].strip());
    }

    private static List<String> words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isEmpty())
                .toList();
    }
}
