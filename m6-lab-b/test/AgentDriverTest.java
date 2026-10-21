import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;

public class AgentDriverTest {

    @Test
    public void run_producesNonEmptyTranscript() throws IOException {
        AgentDriver agent = new AgentDriver(
                new StubLlmClient(), Path.of("prompts/react.txt"), 3);
        String transcript = agent.run("What is 2 + 2?");
        assertNotNull(transcript);
        assertTrue(transcript.contains("STUB RESPONSE"), transcript);
    }

    @Test
    public void run_registersBothTools() {
        AgentDriver agent = new AgentDriver(
                new StubLlmClient(), Path.of("prompts/react.txt"), 3);
        assertTrue(agent.getTools().containsKey("list_books"));
        assertTrue(agent.getTools().containsKey("search_books"));
    }
}
