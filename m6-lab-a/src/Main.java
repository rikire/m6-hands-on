import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * End-to-end driver for Session 6A:
 *   1. index sample-repo/;
 *   2. for each question in {@link #QUESTIONS}, retrieve the top-k
 *      chunks and ask the LLM for an answer.
 *
 * Environment knobs (all optional):
 *   EMBEDDER=minilm|stub      default minilm (all-MiniLM-L6-v2, local)
 *   LLM=ollama|stub           default ollama
 *   OLLAMA_MODEL=<name>       default llama3.2:3b
 *   CHUNK_LINES=<n>           n &gt; 0: fixed n-line chunker (task 7); default
 *                             0 = method-level chunker
 *   SKIP_LLM=1                print retrieval only, do not call the LLM
 */
public class Main {

    /**
     * A question plus the "right chunk" for scoring retrieval: a hit is a
     * retrieved chunk from {@code file} whose text contains any of
     * {@code evidence}.  {@code file == null} marks a question whose answer
     * is not in the corpus (task 8).
     */
    record Question(String text, String file, String... evidence) {}

    private static final Question[] QUESTIONS = {
        new Question("Where is the fine for an overdue loan computed?",
                "FinePolicy.java", "PER_DAY.multiply"),
        new Question("What class holds the catalogue of books, and how is it indexed?",
                "Catalog.java", "Map<String, Book> byIsbn"),
        new Question("How does the system decide a loan has been returned?",
                "Loan.java", "returnedOn != null", "this.returnedOn = day"),
        new Question("What is the maximum fine the policy allows?",
                "FinePolicy.java", "CAP = new BigDecimal(\"500.00\")"),
        new Question("Which methods write to the loan registry?",
                "LoanRegistry.java", "history.add(loan)"),
        new Question("How many books can a member have on loan at the same time?",
                "Member.java", "activeLoans.size() >= 5"),
        new Question("How many days is the standard loan term?",
                "Loan.java", "DEFAULT_TERM_DAYS = 21"),
        new Question("What happens when someone borrows a book whose ISBN is not in the catalogue?",
                "LibraryService.java", "unknown book: "),
        new Question("How is the number of loans per genre computed for the committee report?",
                "ReportService.java", "counts.merge(g"),
        new Question("When are two Book objects considered equal?",
                "Book.java", "isbn.equals(other.isbn)"),
        // Task 8: not answerable from the corpus.
        new Question("How does the library send overdue reminder emails using Spring Boot's JavaMailSender?",
                null),
    };

    private static final int TOP_K = 8;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "sample-repo");
        int chunkLines = Integer.parseInt(env("CHUNK_LINES", "0"));
        boolean skipLlm = env("SKIP_LLM", "").equals("1");

        // -----------------------------------------------------------
        // 1. Pipeline construction.
        // -----------------------------------------------------------
        Embedder    emb = env("EMBEDDER", "minilm").equals("stub")
                ? new StubEmbedder() : new MiniLmEmbedder();
        LlmClient   llm = env("LLM", "ollama").equals("stub")
                ? new StubLlmClient() : new OllamaLlm(env("OLLAMA_MODEL", "llama3.2:3b"));
        RepoIndexer idx = new RepoIndexer(emb, chunkLines);
        System.out.printf("embedder=%s llm=%s chunker=%s%n", emb.modelId(),
                skipLlm ? "(skipped)" : llm.getClass().getSimpleName(),
                chunkLines > 0 ? chunkLines + "-line" : "method");

        // -----------------------------------------------------------
        // 2. Index the corpus; report count + wall-clock time.
        // -----------------------------------------------------------
        Instant t0 = Instant.now();
        idx.indexDirectory(dir);
        Duration elapsed = Duration.between(t0, Instant.now());
        System.out.printf("Indexed %d chunk(s) from %s in %d ms%n%n",
                idx.size(), dir, elapsed.toMillis());

        // -----------------------------------------------------------
        // 3. Ask each question.  Print retriever hits + LLM answer.
        //    (retrieve() and answer() both re-rank; the redundancy is
        //    deliberate so the trace shows what the retriever picked.)
        // -----------------------------------------------------------
        RagAssistant ra = new RagAssistant(
                idx, emb, llm, Path.of("prompts/rag.txt"));
        int scored = 0, found = 0, at1 = 0, at3 = 0;
        long contextChars = 0;
        for (Question q : QUESTIONS) {
            System.out.println("=== Q: " + q.text());
            List<Chunk> hits = ra.retrieve(q.text(), TOP_K);
            int rank = 0;
            for (int i = 0; i < hits.size(); i++) {
                Chunk c = hits.get(i);
                contextChars += c.content().length();
                boolean right = isRight(q, c);
                if (right && rank == 0) rank = i + 1;
                System.out.println("  hit: " + c.source()
                        + ":" + c.startLine() + "-" + c.endLine() + (right ? "   <- right chunk" : ""));
            }
            if (q.file() != null) {
                scored++;
                if (rank > 0) found++;
                if (rank == 1) at1++;
                if (rank > 0 && rank <= 3) at3++;
                System.out.println("  right chunk in top-" + TOP_K + ": "
                        + (rank > 0 ? "yes (rank " + rank + ")" : "NO"));
            }
            if (!skipLlm) {
                Instant a0 = Instant.now();
                String answer = ra.answer(q.text(), TOP_K);
                System.out.printf("--- A (%d s): %s%n",
                        Duration.between(a0, Instant.now()).toSeconds(), answer.strip());
            }
            System.out.println();
        }
        System.out.printf("Retrieval hit-rate @1: %d/%d  @3: %d/%d  @%d: %d/%d%n",
                at1, scored, at3, scored, TOP_K, found, scored);
        System.out.printf("Mean retrieved context per question: %d chars%n",
                contextChars / QUESTIONS.length);
    }

    private static boolean isRight(Question q, Chunk c) {
        return q.file() != null && c.source().endsWith("/" + q.file())
                && Arrays.stream(q.evidence()).anyMatch(c.content()::contains);
    }

    private static String env(String name, String dflt) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? dflt : v;
    }
}
