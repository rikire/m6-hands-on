import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
public class OllamaLlm implements LlmClient {
	private final ChatLanguageModel model = OllamaChatModel.builder()
						.baseUrl("http://localhost:11434")
						.modelName("llama3.1:8b")
						.temperature(0.0)
						.build();
	@Override
	public String complete(String prompt) {
		return model.generate(prompt);
	}
}
