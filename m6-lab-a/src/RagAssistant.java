import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Query-time RAG assistant. Takes a natural-language query, embeds it,
 * pulls the top-k nearest chunks from the {@link RepoIndexer}'s index,
 * formats them into the RAG prompt template, and calls the LLM.
 *
 * Session 6A: complete {@link #retrieve(String, int)} and
 * {@link #buildPrompt(String, List)}.  Do NOT edit {@link #answer}.
 */
public class RagAssistant {

    private final RepoIndexer indexer;
    private final Embedder embedder;
    private final LlmClient llm;
    private final Path promptTemplate;

    public RagAssistant(RepoIndexer indexer, Embedder embedder, LlmClient llm, Path promptTemplate) {
        this.indexer = indexer;
        this.embedder = embedder;
        this.llm = llm;
        this.promptTemplate = promptTemplate;
    }

    public String answer(String query, int k) throws IOException {
        List<Chunk> hits = retrieve(query, k);
        String prompt = buildPrompt(query, hits);
        return llm.complete(prompt);
    }

    /**
     * TODO(session-6A): embed the query, rank every indexed chunk by
     * cosine similarity, and return the top k.
     *
     * The stub below ignores the query entirely and returns the first
     * k chunks in whatever order the indexer stored them.  Good enough
     * for the pipeline to compile; useless for actual retrieval.
     */
    protected List<Chunk> retrieve(String query, int k) {
        List<Chunk> all = indexer.getIndex();
        return new ArrayList<>(all.subList(0, Math.min(k, all.size())));
    }

    /**
     * TODO(session-6A): load the template from {@link #promptTemplate},
     * substitute {@code {query}} and {@code {context}} placeholders with
     * the query and a concatenation of hits (source:line + content), and
     * return the assembled prompt.
     *
     * The stub below returns the raw template with the placeholders
     * still intact -- so the LLM will see literal "{query}" and
     * "{context}" strings and your answers will be visibly wrong.
     */
    protected String buildPrompt(String query, List<Chunk> hits) throws IOException {
        return Files.readString(promptTemplate);
    }
}
