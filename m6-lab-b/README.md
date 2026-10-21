# M6 Session 6B -- ReAct agent over a book catalogue

Starter for **Session 6B** of the Module 6 hands-on.
A sibling `m6-lab-a/` ships the starter for Session 6A.

## Quick start

    make deps && make test        # offline, uses stubs

The starter ships with `StubLlmClient` so the ReAct loop runs without
network access or an API key.  Session 6B asks you to (a) fill in
`AgentDriver.shouldStop` / `AgentDriver.parseAction`, (b) fill in the
two tool bodies (`ListBooksTool` and `SearchBooksTool`) against
`books.json`, and (c) optionally plug a real LLM in by swapping
`LlmClient`.

## Installing langchain4j

Only needed when you want to run against a real LLM.  Requires Maven
on your PATH.

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

    cp solution/src/AgentDriver.java       src/
    cp solution/src/tools/*.java           src/tools/
    cp solution/test/*.java                test/
    make test

See `solution/README.md` for a description of what the solution does.
