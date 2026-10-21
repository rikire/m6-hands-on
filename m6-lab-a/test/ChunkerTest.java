import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ChunkerTest {

    private static final String SRC = String.join("\n",
            "package p;",                                        // 1
            "",                                                  // 2
            "/** A thing. */",                                   // 3
            "public class Thing extends Base implements Runnable {", // 4
            "    private static final int CAP = 500;",           // 5
            "",                                                  // 6
            "    /**",                                           // 7
            "     * Runs it.",                                   // 8
            "     */",                                           // 9
            "    @Override",                                     // 10
            "    public void run() {",                           // 11
            "        String s = \"} not a brace {\";",           // 12
            "        if (s.isEmpty()) { return; }",              // 13
            "        // } neither is this",                      // 14
            "    }",                                             // 15
            "",                                                  // 16
            "    int twice(int x) { return 2 * x; }",            // 17
            "}",                                                 // 18
            "",                                                  // 19
            "interface Policy {",                                // 20
            "    int fine(int days);",                           // 21
            "    static Policy dflt() {",                        // 22
            "        return d -> { return d * 5; };",            // 23
            "    }",                                             // 24
            "}",                                                 // 25
            "");

    private List<Chunk> chunk(Path dir) throws IOException {
        Path f = dir.resolve("Thing.java");
        Files.writeString(f, SRC);
        return new RepoIndexer(new StubEmbedder()).chunkFile(f);
    }

    private Chunk methodChunk(List<Chunk> cs, String needle) {
        return cs.stream().filter(c -> c.content().contains(needle)
                        && !c.content().contains("CAP = 500"))
                .findFirst().orElseThrow(() -> new AssertionError("no chunk with " + needle));
    }

    @Test
    void chunkFile_splitsAtMethodBoundaries_ignoringBracesInStringsAndComments(@TempDir Path dir)
            throws IOException {
        List<Chunk> cs = chunk(dir);
        Chunk run = methodChunk(cs, "void run()");
        assertEquals(7, run.startLine());   // starts at its JavaDoc
        assertEquals(15, run.endLine());
        Chunk twice = methodChunk(cs, "int twice(");
        assertEquals(17, twice.startLine());
        assertEquals(17, twice.endLine());
        Chunk dflt = methodChunk(cs, "static Policy dflt()");
        assertEquals(22, dflt.startLine());
        assertEquals(24, dflt.endLine());   // lambda braces did not end it early
    }

    @Test
    void chunkFile_methodChunkCarriesPathClassSignatureAndJavadoc(@TempDir Path dir) throws IOException {
        Chunk run = methodChunk(chunk(dir), "void run()");
        assertTrue(run.content().startsWith("// File: " + dir.resolve("Thing.java")), run.content());
        assertTrue(run.content().contains("public class Thing extends Base implements Runnable"),
                run.content());
        assertTrue(run.content().contains("* Runs it."), run.content());
        assertTrue(run.content().contains("@Override"), run.content());
        assertFalse(run.content().contains("int twice"), "method chunks must not merge");
    }

    @Test
    void chunkFile_emitsTypeHeaderChunkWithFields(@TempDir Path dir) throws IOException {
        Chunk header = chunk(dir).stream()
                .filter(c -> c.content().contains("CAP = 500")).findFirst().orElseThrow();
        assertTrue(header.content().contains("/** A thing. */"));
        assertFalse(header.content().contains("void run()"));
    }

    @Test
    void chunkFile_fixedLineMode_splitsEveryNLinesRegardlessOfStructure(@TempDir Path dir)
            throws IOException {
        Path f = dir.resolve("Thing.java");
        Files.writeString(f, SRC);
        List<Chunk> cs = new RepoIndexer(new StubEmbedder(), 10).chunkFile(f);
        assertEquals(3, cs.size());   // 25 lines -> 1-10, 11-20, 21-25
        assertEquals(11, cs.get(1).startLine());
        assertEquals(25, cs.get(2).endLine());
    }

    @Test
    void indexer_sampleRepo_producesSeveralChunksPerFile() throws IOException {
        RepoIndexer idx = new RepoIndexer(new StubEmbedder());
        idx.indexDirectory(Path.of("sample-repo"));
        assertTrue(idx.size() > 3 * 8, "expected method granularity, got " + idx.size());
    }
}
