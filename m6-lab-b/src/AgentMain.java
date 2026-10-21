import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive driver for Session 6B: runs one question through the ReAct
 * loop and writes a trace (header + tool-call log + full transcript) to
 * stdout and, if TRACE is set, to traces/&lt;TRACE&gt;.txt.
 *
 * Usage: java AgentMain "question"
 *
 * Environment knobs (all optional):
 *   LLM=ollama|stub           default ollama
 *   OLLAMA_MODEL=<name>       default llama3.2:3b
 *   PROMPT=<path>             default prompts/react.txt
 *   STEPS=<n>                 step budget, default 6
 *   TOKENS=<n>                token budget, default unlimited
 *   TRACE=<name>              save to traces/<name>.txt
 */
public class AgentMain {

    /** Logs every call that reaches a tool, so the header shows what really ran. */
    record LoggingTool(Tool inner, List<String> log) implements Tool {
        @Override
        public String name() {
            return inner.name();
        }

        @Override
        public String call(String arg) {
            String out = inner.call(arg);
            log.add(String.format("%s(\"%s\") -> %d chars", name(), arg, out.length()));
            return out;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: AgentMain \"question\"");
            System.exit(2);
        }
        String question = String.join(" ", args);
        String model = env("OLLAMA_MODEL", "llama3.2:3b");
        Path prompt = Path.of(env("PROMPT", "prompts/react.txt"));
        int steps = Integer.parseInt(env("STEPS", "6"));
        int tokens = Integer.parseInt(env("TOKENS", String.valueOf(AgentDriver.UNLIMITED)));

        LlmClient llm = env("LLM", "ollama").equals("stub")
                ? new StubLlmClient() : new OllamaLlm(model);
        AgentDriver agent = new AgentDriver(llm, prompt, steps, tokens);
        List<String> calls = new ArrayList<>();
        for (Tool t : List.copyOf(agent.getTools().values())) {
            agent.registerTool(new LoggingTool(t, calls));
        }

        Instant t0 = Instant.now();
        String transcript = agent.run(question);
        long secs = Duration.between(t0, Instant.now()).toSeconds();

        StringBuilder out = new StringBuilder();
        out.append("question:    ").append(question).append('\n');
        out.append("llm:         ").append(llm instanceof OllamaLlm ? model : "stub").append('\n');
        out.append("prompt:      ").append(prompt).append('\n');
        out.append("budgets:     steps=").append(steps).append(" tokens=")
                .append(tokens == AgentDriver.UNLIMITED ? "unlimited" : tokens).append('\n');
        out.append("stopped by:  ").append(agent.getStopReason()).append('\n');
        out.append("llm calls:   ").append(agent.getStepsTaken()).append('\n');
        out.append("tokens used: ~").append(agent.getTokensUsed()).append(" (chars/4)\n");
        out.append("wall clock:  ").append(secs).append(" s\n");
        out.append("tool calls:  ").append(calls.size()).append('\n');
        calls.forEach(c -> out.append("  ").append(c).append('\n'));
        out.append("\n--- transcript ---\n").append(transcript);

        System.out.print(out);
        String trace = env("TRACE", "");
        if (!trace.isEmpty()) {
            Path file = Path.of("traces", trace + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, out);
            System.out.println("\n[saved " + file + "]");
        }
    }

    private static String env(String name, String dflt) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? dflt : v;
    }
}
