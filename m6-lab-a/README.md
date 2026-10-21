# M6 Session 6A -- RAG over a local Java repo

Starter for **Session 6A** of the Module 6 hands-on.
A sibling `m6-lab-b/` ships the starter for Session 6B.

## Quick start

    make deps && make test        # offline, uses stubs

The starter ships with `StubEmbedder` and `StubLlmClient` so the whole
pipeline runs without network access or an API key.  Session 6A asks
you to (a) finish the chunker + retrieval TODOs in `RepoIndexer.java`
and `RagAssistant.java`, and (b) swap the stubs for real
`Embedder` / `LlmClient` implementations.

## Installing langchain4j

Only needed when you want to run against a real embedding model /
real LLM (step (b) above).  Requires Maven on your PATH.

    make langchain4j

Pulls langchain4j 0.36.0 + transitive deps into `libs/langchain4j/`
via `mvn dependency:copy-dependencies`.  Subsequent `make build`
picks up the new jars automatically.

See `handson6.tex` for how to install and run Ollama locally
if you want to replace `StubLlmClient` with a real model.
No API keys are involved; everything runs on your machine.

## Solution

The reference solution lives under `solution/`.  Drop it over the
starter with:

    cp solution/src/*.java  src/
    cp solution/test/*.java test/
    make test

See `solution/README.md` for a description of what the solution does.
