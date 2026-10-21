import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Walks a directory of .java files, splits each file into method-level
 * chunks, embeds each chunk, and stores the result in an in-memory index.
 *
 * Session 6A: complete {@link #chunkFile(Path)} and {@link #embed(String)}.
 */
public class RepoIndexer {

    private final Embedder embedder;
    private final List<Chunk> index = new ArrayList<>();

    public RepoIndexer(Embedder embedder) {
        this.embedder = embedder;
    }

    /** Walks dir, indexing every *.java file below it. */
    public void indexDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(this::indexFile);
        }
    }

    private void indexFile(Path file) {
        try {
            List<Chunk> chunks = chunkFile(file);
            for (Chunk c : chunks) {
                float[] v = embed(c.content());
                index.add(c.withEmbedding(v));
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to index " + file, e);
        }
    }

    /**
     * TODO(session-6A): split the file into method-level chunks. For the
     * hands-on, a "brace-balanced method body plus signature" chunker is
     * enough. Return whole-file as a single chunk if you cannot detect
     * method boundaries.
     */
    protected List<Chunk> chunkFile(Path file) throws IOException {
        String body = Files.readString(file);
        // Placeholder: one chunk per file.
        int lines = (int) body.lines().count();
        return List.of(new Chunk(file.toString(), 1, lines, body, null));
    }

    /**
     * TODO(session-6A): swap the stub for a real embedder implementation
     * (langchain4j all-MiniLM-L6-v2 is one option). The signature stays
     * the same; only the interface binding at construction time changes.
     */
    protected float[] embed(String text) {
        return embedder.embed(text);
    }

    public List<Chunk> getIndex() {
        return index;
    }

    public int size() {
        return index.size();
    }
}
