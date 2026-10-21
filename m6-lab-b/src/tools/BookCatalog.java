import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only view of books.json shared by the two tools.
 *
 * books.json is a flat array of flat objects (no nesting, no arrays inside
 * an entry), so a small regex reader is enough and keeps {@code make test}
 * free of a JSON library.  Each book keeps its original JSON text, so tool
 * output is byte-for-byte what the catalogue says.
 */
final class BookCatalog {

    record Book(String author, int year, String json) {}

    static final Path DEFAULT_PATH = Path.of("books.json");

    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}");
    private static final String STR = "\"((?:[^\"\\\\]|\\\\.)*)\"";

    private BookCatalog() {}

    static String raw(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    static List<Book> load(Path file) {
        List<Book> out = new ArrayList<>();
        Matcher m = OBJECT.matcher(raw(file));
        while (m.find()) {
            String obj = m.group();
            out.add(new Book(stringField(obj, "author"), intField(obj, "year"), obj));
        }
        return out;
    }

    private static String stringField(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*" + STR).matcher(obj);
        if (!m.find()) {
            throw new IllegalStateException("book without \"" + key + "\": " + obj);
        }
        return m.group(1);
    }

    private static int intField(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(obj);
        if (!m.find()) {
            throw new IllegalStateException("book without \"" + key + "\": " + obj);
        }
        return Integer.parseInt(m.group(1));
    }
}
