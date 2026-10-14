import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;

/**
 * Real semantic embedder: all-MiniLM-L6-v2 (384-dim), run in-process via
 * ONNX from the langchain4j jar.  No network.  Needs {@code make langchain4j}.
 */
public class MiniLmEmbedder implements Embedder {

    private final EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

    @Override
    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }

    @Override
    public String modelId() {
        return "all-MiniLM-L6-v2@langchain4j-0.36.0";
    }
}
