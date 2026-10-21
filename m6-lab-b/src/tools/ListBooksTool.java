/**
 * list_books: returns the entire book catalogue, verbatim.
 *
 * Intentionally greedy -- the whole JSON file goes back as the tool's
 * reply.  Session 6B asks the agent to prefer the cheaper
 * {@link SearchBooksTool} when the query is specific enough.
 *
 * Session 6B TODO: read books.json and return its content as a string.
 */
public class ListBooksTool implements Tool {

    @Override
    public String name() {
        return "list_books";
    }

    @Override
    public String call(String arg) {
        return "TODO(session-6B): read books.json and return its content";
    }
}
