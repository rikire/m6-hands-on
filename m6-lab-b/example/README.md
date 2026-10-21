# Example adapter

`OllamaLlm.java` is a reference implementation of `LlmClient` that
delegates to langchain4j's Ollama chat model.  It is NOT in `src/`
because it depends on jars that only exist after you run

    make langchain4j

Once those jars are installed, copy the file into `src/`:

    cp example/OllamaLlm.java src/

and swap `new StubLlmClient()` for `new OllamaLlm()` at the
construction site (see handson6.tex).
