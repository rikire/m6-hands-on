import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal ReAct-style agent loop.
 *
 * Each turn we send the LLM the running scratchpad ("Thought/Action/
 * Observation" transcript so far) plus the user's original query, parse
 * exactly one Action out of the LLM's response, call the corresponding
 * tool, append the Observation, and loop.
 *
 * Two hard budgets bound the loop:
 *  - a step budget: at most {@code stepBudget} LLM calls; on the last one
 *    the loop stops even if the model asked for another tool;
 *  - an optional token budget: before each call we check that the prompt
 *    still fits in what is left, so the call that would overrun the budget
 *    is never sent.
 * Either way the transcript ends with an explicit "ABORTED: ..." line.
 */
public class AgentDriver {

    /** No token budget. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private static final Pattern ACTION = Pattern.compile(
            "(?im)^\\s*action\\s*:\\s*(\\S+)\\s*$\\R?(?:^\\s*action\\s+input\\s*:[ \\t]*(.*)$)?");
    private static final Pattern FINAL = Pattern.compile("(?im)^\\s*final\\s+answer\\s*:");
    private static final Pattern OBSERVATION = Pattern.compile("(?im)^\\s*observation\\s*:");

    private final LlmClient llm;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path promptTemplate;
    private final int stepBudget;
    private final int tokenBudget;

    private int stepsTaken;
    private int tokensUsed;
    private String stopReason;

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget) {
        this(llm, promptTemplate, stepBudget, UNLIMITED);
    }

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget, int tokenBudget) {
        this.llm = llm;
        this.promptTemplate = promptTemplate;
        this.stepBudget = stepBudget;
        this.tokenBudget = tokenBudget;
        registerTool(new ListBooksTool());
        registerTool(new SearchBooksTool());
    }

    public void registerTool(Tool t) {
        tools.put(t.name(), t);
    }

    public Map<String, Tool> getTools() {
        return tools;
    }

    /** LLM calls made by the last {@link #run}. */
    public int getStepsTaken() {
        return stepsTaken;
    }

    /** Estimated tokens (prompt + response) spent by the last {@link #run}. */
    public int getTokensUsed() {
        return tokensUsed;
    }

    /**
     * Why the last {@link #run} ended: "final answer", "step budget",
     * "token budget" or "no action".
     */
    public String getStopReason() {
        return stopReason;
    }

    /**
     * Run the loop. Returns the final scratchpad (transcript of all
     * thoughts, actions, observations, plus the last LLM response).
     */
    public String run(String userQuery) throws IOException {
        String template = Files.readString(promptTemplate);
        StringBuilder scratch = new StringBuilder();
        String lastResponse = "";
        stepsTaken = 0;
        tokensUsed = 0;
        stopReason = null;
        for (int step = 0; step < stepBudget; step++) {
            String prompt = template
                    .replace("{query}", userQuery)
                    .replace("{scratchpad}", scratch.toString());
            int promptTokens = estimateTokens(prompt);
            if (tokensUsed + promptTokens > tokenBudget) {
                stopReason = "token budget";
                scratch.append("ABORTED: token budget ").append(tokenBudget)
                        .append(" exhausted (used ").append(tokensUsed)
                        .append(", next prompt ~").append(promptTokens).append(")\n");
                break;
            }
            lastResponse = dropFabricatedObservation(llm.complete(prompt));
            stepsTaken++;
            tokensUsed += promptTokens + estimateTokens(lastResponse);
            scratch.append(lastResponse).append('\n');
            if (shouldStop(lastResponse, step)) {
                if (hasFinalAnswer(lastResponse, step)) {
                    stopReason = "final answer";
                } else {
                    stopReason = "step budget";
                    scratch.append("ABORTED: step budget ").append(stepBudget)
                            .append(" exhausted before a Final Answer\n");
                }
                break;
            }
            Map.Entry<String, String> action = parseAction(lastResponse);
            if (action == null) {
                stopReason = "no action";
                scratch.append("Observation: (no action parsed; stopping)\n");
                break;
            }
            Tool t = tools.get(action.getKey());
            String obs = (t == null)
                    ? "ERROR: unknown tool " + action.getKey()
                    : t.call(action.getValue());
            scratch.append("Observation: ").append(obs).append('\n');
        }
        return scratch.toString();
    }

    /**
     * True once the LLM has emitted a usable "Final Answer:" line or this
     * was the last step the budget allows.
     */
    protected boolean shouldStop(String lastResponse, int step) {
        return hasFinalAnswer(lastResponse, step) || step >= stepBudget - 1;
    }

    /**
     * The first "Action: name" line and the "Action Input: arg" line right
     * after it, as (lower-cased name, arg); null if there is no Action.
     * A missing or empty Action Input gives "".
     */
    protected Map.Entry<String, String> parseAction(String response) {
        Matcher m = ACTION.matcher(response);
        if (!m.find()) {
            return null;
        }
        String arg = m.group(2) == null ? "" : m.group(2).strip();
        return Map.entry(m.group(1).toLowerCase(), arg);
    }

    /** ~4 characters per token, rounded up; good enough for a budget. */
    static int estimateTokens(String s) {
        return (s.length() + 3) / 4;
    }

    /**
     * A "Final Answer:" counts if it comes before any Action, or if the model
     * has already seen at least one real tool result (step &gt; 0) -- small
     * models often prefix a grounded answer with a needless extra Action.
     * On the first step an Action followed by a Final Answer is a guess, so
     * the action runs instead.  (Invented Observations are already cut off
     * by {@link #dropFabricatedObservation}.)
     */
    private static boolean hasFinalAnswer(String response, int step) {
        Matcher f = FINAL.matcher(response);
        if (!f.find()) {
            return false;
        }
        Matcher a = ACTION.matcher(response);
        return step > 0 || !a.find() || f.start() < a.start();
    }

    /**
     * Cut the response at the first "Observation:" the model wrote itself:
     * observations come from tools, and anything after an invented one
     * (including a Final Answer) is built on it.
     */
    private static String dropFabricatedObservation(String response) {
        Matcher m = OBSERVATION.matcher(response);
        return (m.find() ? response.substring(0, m.start()) : response).strip();
    }
}
