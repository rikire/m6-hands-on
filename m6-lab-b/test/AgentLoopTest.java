import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class AgentLoopTest {

    /** Replays canned responses in order and records every prompt it was sent. */
    static class ScriptedLlm implements LlmClient {
        final Deque<String> replies;
        final List<String> prompts = new ArrayList<>();

        ScriptedLlm(String... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public String complete(String prompt) {
            prompts.add(prompt);
            return replies.isEmpty() ? "Thought: out of script\nFinal Answer: ?" : replies.poll();
        }
    }

    private static final Path PROMPT = Path.of("prompts/react.txt");

    private static final String SEARCH_LE_GUIN = """
            Thought: filter by author and decade
            Action: search_books
            Action Input: Ursula K. Le Guin|1970|1979""";

    private static final String FINAL = """
            Thought: one book matched
            Final Answer: 1""";

    // ---- parseAction -------------------------------------------------

    @Test
    public void parseAction_extractsNameAndArgument() {
        Map.Entry<String, String> a = agent(new ScriptedLlm(), 5).parseAction(SEARCH_LE_GUIN);
        assertEquals("search_books", a.getKey());
        assertEquals("Ursula K. Le Guin|1970|1979", a.getValue());
    }

    @Test
    public void parseAction_toleratesCaseAndSpacing() {
        Map.Entry<String, String> a = agent(new ScriptedLlm(), 5)
                .parseAction("thought: x\n  action :  List_Books \naction input:\n");
        assertEquals("list_books", a.getKey());
        assertEquals("", a.getValue());
    }

    @Test
    public void parseAction_takesFirstPairOnly() {
        String two = SEARCH_LE_GUIN + "\nAction: list_books\nAction Input: x";
        assertEquals("search_books", agent(new ScriptedLlm(), 5).parseAction(two).getKey());
    }

    @Test
    public void parseAction_noAction_returnsNull() {
        assertNull(agent(new ScriptedLlm(), 5).parseAction("I think the answer is 1."));
    }

    // ---- shouldStop --------------------------------------------------

    @Test
    public void shouldStop_onFinalAnswer() {
        assertTrue(agent(new ScriptedLlm(), 5).shouldStop(FINAL, 0));
    }

    @Test
    public void shouldStop_notOnActionBeforeBudget() {
        assertFalse(agent(new ScriptedLlm(), 5).shouldStop(SEARCH_LE_GUIN, 0));
    }

    @Test
    public void shouldStop_actionThenFinalAnswerOnFirstStep_isAGuessNotAStop() {
        // No tool result has been seen yet, so the answer cannot be grounded:
        // the action has to run first.
        String guess = SEARCH_LE_GUIN + "\nFinal Answer: 0";
        assertFalse(agent(new ScriptedLlm(), 5).shouldStop(guess, 0));
    }

    @Test
    public void shouldStop_actionThenFinalAnswerAfterAnObservation_stops() {
        // Seen with llama3.2:3b: after a correct search it tacks a needless
        // "Action: list_books" in front of the right Final Answer.
        String late = "Thought: x\nAction: list_books\nAction Input:\n\nFinal Answer: 1";
        assertTrue(agent(new ScriptedLlm(), 5).shouldStop(late, 1));
    }

    @Test
    public void shouldStop_atLastStep() {
        assertTrue(agent(new ScriptedLlm(), 3).shouldStop(SEARCH_LE_GUIN, 2));
    }

    // ---- run ---------------------------------------------------------

    @Test
    public void run_callsToolThenStopsOnFinalAnswer() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(SEARCH_LE_GUIN, FINAL);
        AgentDriver ag = agent(llm, 5);
        String trace = ag.run("How many books did Ursula Le Guin write in the 1970s?");

        assertEquals(2, llm.prompts.size());
        assertTrue(trace.contains("Observation: [{\"id\": \"b002\""), trace);
        assertTrue(llm.prompts.get(1).contains("The Dispossessed"), "observation fed back");
        assertEquals("final answer", ag.getStopReason());
        assertEquals(2, ag.getStepsTaken());
    }

    @Test
    public void run_acceptsFinalAnswerTrailingAnActionOnceGrounded() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(SEARCH_LE_GUIN,
                "Thought: x\nAction: list_books\nAction Input:\n\nFinal Answer: 1");
        AgentDriver ag = agent(llm, 5);
        String trace = ag.run("q");
        assertEquals("final answer", ag.getStopReason());
        assertEquals(1, count(trace, "Observation:"), "list_books is not run");
    }

    @Test
    public void run_dropsFabricatedObservation() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(
                SEARCH_LE_GUIN + "\nObservation: [] (made up)\nFinal Answer: 0", FINAL);
        String trace = agent(llm, 5).run("q");
        assertFalse(trace.contains("made up"), trace);
        assertTrue(trace.contains("The Dispossessed"), trace);
    }

    @Test
    public void run_stepBudget_abortsCleanly() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(SEARCH_LE_GUIN, SEARCH_LE_GUIN, SEARCH_LE_GUIN, SEARCH_LE_GUIN);
        AgentDriver ag = agent(llm, 3);
        String trace = ag.run("q");

        assertEquals(3, llm.prompts.size(), "exactly stepBudget LLM calls");
        assertEquals(2, count(trace, "Observation:"), "last action is not executed");
        assertTrue(trace.contains("ABORTED: step budget 3 exhausted"), trace);
        assertEquals("step budget", ag.getStopReason());
    }

    @Test
    public void run_tokenBudget_abortsBeforeTheCallThatWouldExceedIt() throws IOException {
        // Call 1 is cheap; its observation (list_books, ~3.3k chars) makes the
        // next prompt far bigger than what is left of the budget.
        String listAll = "Thought: read everything\nAction: list_books\nAction Input:";
        ScriptedLlm llm = new ScriptedLlm(listAll, FINAL);
        AgentDriver ag = new AgentDriver(llm, PROMPT, 10, 900);
        String trace = ag.run("q");

        assertEquals(1, llm.prompts.size(), "second call must not be sent");
        assertTrue(trace.contains("ABORTED: token budget 900 exhausted"), trace);
        assertEquals("token budget", ag.getStopReason());
        assertTrue(ag.getTokensUsed() <= 900, "never spends past the budget: " + ag.getTokensUsed());
    }

    @Test
    public void run_tokenBudget_notHitOnCheapPath() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(SEARCH_LE_GUIN, FINAL);
        AgentDriver ag = new AgentDriver(llm, PROMPT, 10, 900);
        ag.run("q");
        assertEquals("final answer", ag.getStopReason());
    }

    @Test
    public void estimateTokens_isCharsOverFourRoundedUp() {
        assertEquals(0, AgentDriver.estimateTokens(""));
        assertEquals(1, AgentDriver.estimateTokens("abc"));
        assertEquals(2, AgentDriver.estimateTokens("abcde"));
    }

    private static AgentDriver agent(LlmClient llm, int steps) {
        return new AgentDriver(llm, PROMPT, steps);
    }

    private static int count(String s, String needle) {
        return s.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
