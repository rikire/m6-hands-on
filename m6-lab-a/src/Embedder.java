public interface Embedder {
    /** Return a fixed-length embedding for the given text. */
    float[] embed(String text);

    /**
     * Name + version of the embedding model. Stored with the index so that a
     * query embedded by a different model fails loudly instead of returning
     * meaningless similarity scores.
     */
    default String modelId() {
        return getClass().getName();
    }
}
