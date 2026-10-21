import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RepoIndexerTest {

    @Test
    public void indexer_indexesEveryJavaFileUnderSampleRepo() throws IOException {
        RepoIndexer idx = new RepoIndexer(new StubEmbedder());
        Path repo = Path.of("sample-repo");
        assertTrue(Files.exists(repo), "run `make test` from the m6/ directory");
        idx.indexDirectory(repo);
        // sample-repo ships with eight classes; every chunk gets an embedding.
        assertTrue(idx.size() >= 8, "expected >=8 chunks, got " + idx.size());
        for (Chunk c : idx.getIndex()) {
            assertNotNull(c.embedding(), "chunk missing embedding: " + c.source());
            assertEquals(StubEmbedder.DIM, c.embedding().length);
        }
    }

    @Test
    public void stubEmbedder_isDeterministic() {
        StubEmbedder e = new StubEmbedder();
        float[] a = e.embed("hello world");
        float[] b = e.embed("hello world");
        assertArrayEquals(a, b);
    }

    @Test
    public void stubEmbedder_producesUnitVectors() {
        StubEmbedder e = new StubEmbedder();
        float[] v = e.embed("public class Foo {}");
        double norm = 0.0;
        for (float x : v) norm += x * x;
        assertEquals(1.0, Math.sqrt(norm), 1e-3);
    }
}
