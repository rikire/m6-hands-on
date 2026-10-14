import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
     * Embeds the query, ranks every indexed chunk by cosine similarity and
     * returns the top k (best first).  Refuses to run if the query embedder
     * is not the model the index was built with.
     */
    protected List<Chunk> retrieve(String query, int k) {
        if (!embedder.modelId().equals(indexer.modelId())) {
            throw new IllegalStateException("embedding model mismatch: index built with "
                    + indexer.modelId() + ", query embedded with " + embedder.modelId());
        }
        float[] q = embedder.embed(query);
        return indexer.getIndex().stream()
                .sorted(Comparator.comparingDouble((Chunk c) -> cosine(q, c.embedding())).reversed())
                .limit(k)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Full cosine (does not assume unit vectors); 0 for a zero vector. */
    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dim mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(query|context)\\}");

    /**
     * Loads the template and fills {@code {query}} and {@code {context}} in a
     * single pass over the template, so placeholder-like text inside the
     * query or the retrieved code is never substituted again.  Each hit is
     * rendered as a {@code --- source:start-end} line followed by its content.
     */
    protected String buildPrompt(String query, List<Chunk> hits) throws IOException {
        StringBuilder ctx = new StringBuilder();
        for (Chunk c : hits) {
            ctx.append("--- ").append(c.source()).append(':')
               .append(c.startLine()).append('-').append(c.endLine()).append('\n')
               .append(c.content());
            if (!c.content().endsWith("\n")) ctx.append('\n');
        }
        String context = ctx.toString();
        Matcher m = PLACEHOLDER.matcher(Files.readString(promptTemplate));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = m.group(1).equals("query") ? query : context;
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
