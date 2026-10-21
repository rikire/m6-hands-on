import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A minimal ReAct-style agent loop.
 *
 * Each turn we send the LLM the running scratchpad ("Thought/Action/
 * Observation" transcript so far) plus the user's original query, parse
 * exactly one Action out of the LLM's response, call the corresponding
 * tool, append the Observation, and loop.
 *
 * Session 6B TODOs:
 *  - {@link #shouldStop(String, int)}: decide when we're done
 *    (either a "Final Answer:" line has appeared or we hit the step budget).
 *  - {@link #parseAction(String)}: extract the Action name and argument
 *    from the model's most recent response.
 */
public class AgentDriver {

    private final LlmClient llm;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path promptTemplate;
    private final int stepBudget;

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget) {
        this.llm = llm;
        this.promptTemplate = promptTemplate;
        this.stepBudget = stepBudget;
        registerTool(new ListBooksTool());
        registerTool(new SearchBooksTool());
    }

    public void registerTool(Tool t) {
        tools.put(t.name(), t);
    }

    public Map<String, Tool> getTools() {
        return tools;
    }

    /**
     * Run the loop. Returns the final scratchpad (transcript of all
     * thoughts, actions, observations, plus the last LLM response).
     */
    public String run(String userQuery) throws IOException {
        String template = Files.readString(promptTemplate);
        StringBuilder scratch = new StringBuilder();
        String lastResponse = "";
        for (int step = 0; step < stepBudget; step++) {
            String prompt = template
                    .replace("{query}", userQuery)
                    .replace("{scratchpad}", scratch.toString());
            lastResponse = llm.complete(prompt);
            scratch.append(lastResponse).append('\n');
            if (shouldStop(lastResponse, step)) {
                break;
            }
            Map.Entry<String, String> action = parseAction(lastResponse);
            if (action == null) {
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
     * TODO(session-6B): return true once we've hit the budget or the LLM
     * has emitted a "Final Answer:" line. The stub below only checks the
     * step counter, so the loop always runs the full budget.
     */
    protected boolean shouldStop(String lastResponse, int step) {
        return step >= stepBudget - 1;
    }

    /**
     * TODO(session-6B): parse the first "Action: <name>" / "Action Input: <arg>"
     * pair out of {@code response} and return them as a (name, arg) entry.
     * The stub below always returns null so the loop exits after one turn.
     */
    protected Map.Entry<String, String> parseAction(String response) {
        return null;
    }
}
