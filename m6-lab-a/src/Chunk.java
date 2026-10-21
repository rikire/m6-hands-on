/**
 * A slice of source with (optionally) an embedding attached.
 * source is a file path or logical name; startLine/endLine are 1-indexed inclusive.
 */
public record Chunk(String source, int startLine, int endLine, String content, float[] embedding) {

    public Chunk withEmbedding(float[] e) {
        return new Chunk(source, startLine, endLine, content, e);
    }
}
