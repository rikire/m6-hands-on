import java.nio.file.Path;

/**
 * list_books: returns the entire book catalogue, verbatim.
 *
 * Intentionally greedy -- the whole JSON file goes back as the tool's
 * reply.  Session 6B asks the agent to prefer the cheaper
 * {@link SearchBooksTool} when the query is specific enough.
 */
public class ListBooksTool implements Tool {

    private final Path file;

    public ListBooksTool() {
        this(BookCatalog.DEFAULT_PATH);
    }

    public ListBooksTool(Path file) {
        this.file = file;
    }

    @Override
    public String name() {
        return "list_books";
    }

    /** The argument is ignored. */
    @Override
    public String call(String arg) {
        return BookCatalog.raw(file);
    }
}
