/**
 * Never calls a real model. Echoes back a truncated prefix of the prompt
 * so tests can assert on shape without needing network access.
 */
public class StubLlmClient implements LlmClient {

    @Override
    public String complete(String prompt) {
        String head = prompt == null ? "" : prompt.substring(0, Math.min(50, prompt.length()));
        return "STUB RESPONSE for: " + head;
    }
}
