public interface Embedder {
    /** Return a fixed-length embedding for the given text. */
    float[] embed(String text);
}
