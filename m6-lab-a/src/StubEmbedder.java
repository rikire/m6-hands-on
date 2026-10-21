/**
 * Deterministic fake embedder. NOT a real semantic model. The vector is
 * derived from the input string's hash so repeated calls with the same
 * text return the same vector -- enough to exercise the retrieval /
 * indexing pipeline without a network.
 */
public class StubEmbedder implements Embedder {

    public static final int DIM = 32;

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIM];
        if (text == null || text.isEmpty()) {
            return v;
        }
        long seed = text.hashCode() * 2654435761L;
        for (int i = 0; i < DIM; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            v[i] = ((seed >>> 32) & 0xFFFF) / 65535.0f - 0.5f;
        }
        // L2-normalise so cosine similarity behaves sensibly.
        double norm = 0.0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) v[i] /= (float) norm;
        }
        return v;
    }

    /** Cosine similarity helper -- callers may use this from RagAssistant.retrieve. */
    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dim mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return (float) dot;
    }

    @Override
    public String toString() {
        return "StubEmbedder[dim=" + DIM + "]";
    }
}
