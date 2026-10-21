import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BookToolsTest {

    private final SearchBooksTool search = new SearchBooksTool();

    @Test
    public void listBooks_returnsWholeCatalogueVerbatim() throws IOException {
        assertEquals(Files.readString(Path.of("books.json")), new ListBooksTool().call(""));
    }

    @Test
    public void search_authorAndDecade_returnsOnlyMatches() {
        String out = search.call("Ursula K. Le Guin|1970|1979");
        assertTrue(out.contains("The Dispossessed"), out);
        assertFalse(out.contains("The Left Hand of Darkness"), out);   // 1969
        assertFalse(out.contains("A Wizard of Earthsea"), out);        // 1968
        assertEquals(1, count(out));
    }

    @Test
    public void search_yearBoundsAreInclusive() {
        String out = search.call("|1969|1969");
        assertTrue(out.contains("The Left Hand of Darkness"), out);
        assertEquals(1, count(out));
    }

    @Test
    public void search_emptyAuthor_filtersByYearOnly() {
        // 1990-2000 in books.json: Snow Crash, Parable of the Sower, A Suitable Boy,
        // The God of Small Things, Cryptonomicon.
        assertEquals(5, count(search.call("|1990|2000")));
    }

    @Test
    public void search_authorOnly_matchesIgnoringCaseAndInitials() {
        assertEquals(3, count(search.call("ursula le guin")));
        assertEquals(3, count(search.call("Ursula K. Le Guin||")));
    }

    @Test
    public void search_toleratesQuotesAndSpaces() {
        assertEquals(1, count(search.call(" \"Ursula K. Le Guin | 1970 | 1979\" ")));
    }

    @Test
    public void search_noMatch_returnsEmptyArray() {
        assertEquals("[]", search.call("Tolkien||"));
    }

    @Test
    public void search_badYear_returnsErrorNotException() {
        assertTrue(search.call("Le Guin|nineteen seventy|").startsWith("ERROR"));
        assertTrue(search.call("a|1|2|3").startsWith("ERROR"));
    }

    @Test
    public void search_resultIsJsonArrayOfCatalogueObjects() {
        String out = search.call("Frank Herbert||");
        assertTrue(out.startsWith("[") && out.endsWith("]"), out);
        assertTrue(out.contains("\"title\": \"Dune\""), out);
    }

    private static int count(String json) {
        return json.split("\"id\"", -1).length - 1;
    }
}
