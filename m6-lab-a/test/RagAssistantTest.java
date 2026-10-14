import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class RagAssistantTest {

    /** Maps known texts to fixed 2-d vectors so ranking is predictable. */
    private static Embedder fixed(Map<String, float[]> m, String id) {
        return new Embedder() {
            public float[] embed(String text) {
                return m.getOrDefault(text, new float[] {0f, 0f});
            }
            public String modelId() {
                return id;
            }
        };
    }

    private static RepoIndexer indexWith(Embedder e, String... texts) {
        RepoIndexer idx = new RepoIndexer(e);
        for (int i = 0; i < texts.length; i++) {
            idx.add(new Chunk("f" + i, i + 1, i + 1, texts[i], null));
        }
        return idx;
    }

    @Test
    void retrieve_returnsTopKByCosineDescending() {
        Embedder e = fixed(Map.of(
                "east", new float[] {1f, 0f},
                "north", new float[] {0f, 1f},
                "northeast", new float[] {3f, 3f},   // unnormalised on purpose
                "q", new float[] {0.1f, 1f}), "fixed");
        RepoIndexer idx = indexWith(e, "east", "north", "northeast");
        RagAssistant ra = new RagAssistant(idx, e, p -> "", Path.of("unused"));
        List<Chunk> hits = ra.retrieve("q", 2);
        assertEquals(List.of("north", "northeast"), hits.stream().map(Chunk::content).toList());
    }

    @Test
    void retrieve_failsLoudlyWhenQueryEmbedderDiffersFromIndexEmbedder() {
        Embedder a = fixed(Map.of(), "model-a");
        Embedder b = fixed(Map.of(), "model-b");
        RagAssistant ra = new RagAssistant(indexWith(a, "x"), b, p -> "", Path.of("unused"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ra.retrieve("q", 1));
        assertTrue(ex.getMessage().contains("model-a") && ex.getMessage().contains("model-b"));
    }

    @Test
    void buildPrompt_substitutesBothPlaceholdersWithSourceLocations(@TempDir Path dir) throws IOException {
        Path tpl = dir.resolve("t.txt");
        Files.writeString(tpl, "Q={query}\nC={context}\n");
        RagAssistant ra = new RagAssistant(new RepoIndexer(new StubEmbedder()), new StubEmbedder(),
                p -> "", tpl);
        String p = ra.buildPrompt("why $1?", List.of(
                new Chunk("a/B.java", 3, 9, "int f() {}", null),
                new Chunk("c/D.java", 1, 2, "{query} literal in code", null)));
        assertFalse(p.contains("{context}"));
        assertTrue(p.startsWith("Q=why $1?\n"), p);
        assertTrue(p.contains("a/B.java:3-9\nint f() {}"), p);
        assertTrue(p.contains("{query} literal in code"), "code text must not be re-substituted");
        assertTrue(p.indexOf("a/B.java") < p.indexOf("c/D.java"), "hits keep rank order");
    }
}
