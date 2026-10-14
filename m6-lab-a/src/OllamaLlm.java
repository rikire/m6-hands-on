import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.time.Duration;

public class OllamaLlm implements LlmClient {
	private final ChatLanguageModel model;

	public OllamaLlm(String modelName) {
		model = OllamaChatModel.builder()
				.baseUrl("http://localhost:11434")
				.modelName(modelName)
				.temperature(0.0)
				.timeout(Duration.ofMinutes(5))
				.build();
	}

	@Override
	public String complete(String prompt) {
		return model.generate(prompt);
	}
}
