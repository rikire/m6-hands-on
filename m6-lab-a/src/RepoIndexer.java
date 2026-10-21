import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Walks a directory of .java files, splits each file into method-level
 * chunks, embeds each chunk, and stores the result in an in-memory index.
 *
 * Session 6A: complete {@link #chunkFile(Path)} and {@link #embed(String)}.
 */
public class RepoIndexer {

    private final Embedder embedder;
    private final int fixedLines;
    private final List<Chunk> index = new ArrayList<>();

    public RepoIndexer(Embedder embedder) {
        this(embedder, 0);
    }

    /**
     * @param fixedLines if &gt; 0, ignore code structure and cut every file
     *                   into windows of this many lines (the deliberately bad
     *                   chunker for task 7); 0 means method-level chunking.
     */
    public RepoIndexer(Embedder embedder, int fixedLines) {
        this.embedder = embedder;
        this.fixedLines = fixedLines;
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
                add(c);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to index " + file, e);
        }
    }

    /**
     * Splits the file into method-level chunks with a brace-balanced scanner.
     *
     * Every method/constructor with a body becomes one chunk: its leading
     * JavaDoc, annotations, signature and body, prefixed with the file path
     * and the enclosing class signature(s).  Each type additionally gets a
     * "header" chunk holding its JavaDoc, signature and everything that is
     * not inside a method (fields, constants, abstract methods), so values
     * such as {@code CAP = 500.00} stay retrievable.  Falls back to one
     * whole-file chunk if no type declaration is found.
     */
    protected List<Chunk> chunkFile(Path file) throws IOException {
        String src = Files.readString(file);
        String[] lines = src.split("\n", -1);
        int lineCount = (int) src.lines().count();
        if (fixedLines > 0) {
            return fixedLineChunks(file, lines, lineCount);
        }

        List<int[]> docs = new ArrayList<>();          // [start, end) of each /** */
        char[] code = mask(src, docs);
        int[] lineOf = lineIndex(src);

        List<Chunk> out = new ArrayList<>();
        List<Frame> types = new ArrayList<>();          // finished types, for header chunks
        Deque<Frame> stack = new ArrayDeque<>();
        int stmtStart = 0;
        for (int i = 0; i < code.length; i++) {
            char ch = code[i];
            if (ch == ';') {
                stmtStart = i + 1;
            } else if (ch == '{') {
                String header = new String(code, stmtStart, i - stmtStart).strip();
                int headerStart = skipSpace(code, stmtStart);
                Frame parent = stack.peek();
                Frame f;
                if (TYPE_DECL.matcher(header).find() && !header.startsWith("new ")) {
                    f = new Frame(Kind.TYPE, docStart(src, docs, headerStart), parent,
                            header.replaceAll("\\s+", " "));
                } else if (parent != null && parent.kind == Kind.TYPE && isMethodHeader(header)) {
                    f = new Frame(Kind.METHOD, docStart(src, docs, headerStart), parent, null);
                } else {
                    f = new Frame(Kind.OTHER, headerStart, parent, null);
                }
                stack.push(f);
                stmtStart = i + 1;
            } else if (ch == '}') {
                Frame f = stack.pop();
                f.endLine = lineOf[i];
                if (f.kind == Kind.METHOD) {
                    f.parent.methods.add(f);
                    out.add(chunk(file, f.parent, lines, lineOf[f.start], f.endLine,
                            joinLines(lines, lineOf[f.start], f.endLine)));
                } else if (f.kind == Kind.TYPE) {
                    types.add(f);
                    if (f.parent != null) f.parent.methods.add(f);   // nested type: not part of outer header
                }
                stmtStart = i + 1;
            }
        }

        if (types.isEmpty()) {
            return List.of(new Chunk(file.toString(), 1, lineCount, src, null));
        }
        for (Frame t : types) {
            int first = lineOf[t.start];
            StringBuilder body = new StringBuilder();
            boolean lastBlank = false;
            for (int ln = first; ln <= t.endLine; ln++) {
                final int l = ln;
                if (t.methods.stream().anyMatch(m -> l >= lineOf[m.start] && l <= m.endLine)) continue;
                boolean blank = lines[ln - 1].isBlank();
                if (blank && lastBlank) continue;
                body.append(lines[ln - 1]).append('\n');
                lastBlank = blank;
            }
            out.add(chunk(file, t.parent, lines, first, t.endLine, body.toString()));
        }
        return out;
    }

    private List<Chunk> fixedLineChunks(Path file, String[] lines, int lineCount) {
        List<Chunk> out = new ArrayList<>();
        for (int s = 1; s <= lineCount; s += fixedLines) {
            int e = Math.min(lineCount, s + fixedLines - 1);
            out.add(new Chunk(file.toString(), s, e,
                    "// File: " + file + "\n" + joinLines(lines, s, e), null));
        }
        return out;
    }

    private enum Kind { TYPE, METHOD, OTHER }

    private static final class Frame {
        final Kind kind;
        final int start;            // char offset, including leading JavaDoc
        final Frame parent;
        final String signature;     // TYPE only
        final List<Frame> methods = new ArrayList<>();   // TYPE only: carved out of the header chunk
        int endLine;

        Frame(Kind kind, int start, Frame parent, String signature) {
            this.kind = kind;
            this.start = start;
            this.parent = parent;
            this.signature = signature;
        }
    }

    private static final Pattern TYPE_DECL =
            Pattern.compile("(^|\\s)(class|interface|enum|record)\\s+\\w+");

    /** A member header like {@code public int f(int x) throws E}, not {@code Runnable r = new R()}. */
    private static boolean isMethodHeader(String header) {
        int paren = header.indexOf('(');
        int eq = header.indexOf('=');
        return paren > 0 && (eq < 0 || paren < eq);
    }

    private static Chunk chunk(Path file, Frame enclosing, String[] lines, int from, int to, String body) {
        StringBuilder sb = new StringBuilder("// File: ").append(file).append('\n');
        Deque<String> sigs = new ArrayDeque<>();
        for (Frame t = enclosing; t != null; t = t.parent) {
            if (t.kind == Kind.TYPE) sigs.push(t.signature);
        }
        for (String s : sigs) sb.append("// In: ").append(s).append('\n');
        return new Chunk(file.toString(), from, to, sb.append(body).toString(), null);
    }

    private static String joinLines(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int ln = from; ln <= to; ln++) sb.append(lines[ln - 1]).append('\n');
        return sb.toString();
    }

    /** If a JavaDoc ends right before {@code pos} (only whitespace between), returns its start. */
    private static int docStart(String src, List<int[]> docs, int pos) {
        for (int[] d : docs) {
            if (d[1] <= pos && src.substring(d[1], pos).isBlank()) return d[0];
        }
        return pos;
    }

    private static int skipSpace(char[] code, int i) {
        while (i < code.length && Character.isWhitespace(code[i])) i++;
        return i;
    }

    /** lineOf[offset] = 1-based line number of that character. */
    private static int[] lineIndex(String src) {
        int[] lineOf = new int[src.length() + 1];
        int line = 1;
        for (int i = 0; i < src.length(); i++) {
            lineOf[i] = line;
            if (src.charAt(i) == '\n') line++;
        }
        lineOf[src.length()] = line;
        return lineOf;
    }

    /**
     * Copy of src with comments and string/char literals blanked to spaces
     * (newlines kept, so offsets and line numbers still match).  Records
     * the span of every JavaDoc comment in {@code docs}.
     */
    private static char[] mask(String src, List<int[]> docs) {
        char[] c = src.toCharArray();
        int n = c.length;
        int i = 0;
        while (i < n) {
            int start = i;
            if (c[i] == '/' && i + 1 < n && c[i + 1] == '/') {
                while (i < n && c[i] != '\n') i++;
            } else if (c[i] == '/' && i + 1 < n && c[i + 1] == '*') {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                if (src.startsWith("/**", start) && i - start > 3) docs.add(new int[] {start, i});
            } else if (src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                i = end < 0 ? n : end + 3;
            } else if (c[i] == '"' || c[i] == '\'') {
                char q = c[i++];
                while (i < n && c[i] != q && c[i] != '\n') i += c[i] == '\\' ? 2 : 1;
                i = Math.min(n, i + 1);
            } else {
                i++;
                continue;
            }
            for (int k = start; k < i; k++) if (c[k] != '\n') c[k] = ' ';
        }
        return c;
    }

    /**
     * TODO(session-6A): swap the stub for a real embedder implementation
     * (langchain4j all-MiniLM-L6-v2 is one option). The signature stays
     * the same; only the interface binding at construction time changes.
     */
    protected float[] embed(String text) {
        return embedder.embed(text);
    }

    /** Embeds and stores a single chunk. */
    public void add(Chunk c) {
        index.add(c.withEmbedding(embed(c.content())));
    }

    /** The embedding model every stored vector came from. */
    public String modelId() {
        return embedder.modelId();
    }

    public List<Chunk> getIndex() {
        return index;
    }

    public int size() {
        return index.size();
    }
}
