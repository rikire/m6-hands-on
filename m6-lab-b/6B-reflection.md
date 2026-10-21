# Session 6B -- ReAct agent: reflection

Setup: `llama3.2:3b` via Ollama (temperature 0), `prompts/react.txt` as
shipped unless noted, tools over `books.json` (30 books).  Token counts are
the driver's estimate (characters / 4) for prompt + response, not the
model's tokenizer.  Every run is in `traces/`; each trace starts with a
header (stop reason, LLM calls, tokens, the tool calls that actually ran)
followed by the full transcript.

**Build:** `src/OllamaLlm.java` needs langchain4j, so run `make langchain4j`
(needs Maven) before `make test`; without it the build fails on that file.
The tests themselves never call a model.  `make run Q="..."` needs Ollama
with `llama3.2:3b` (`LLM=stub` to run without it).

Ground truth for question A ("How many books did Ursula Le Guin write in
the 1970s?"): **1** -- *The Dispossessed* (1974).  *The Left Hand of
Darkness* (1969) sits just outside the range.

## What was built

- `ListBooksTool` returns `books.json` verbatim; `SearchBooksTool` parses
  `author|year_from|year_to` (empty or missing parts = no constraint,
  inclusive bounds, author matched word-by-word ignoring case and
  punctuation, so "Ursula Le Guin" finds "Ursula K. Le Guin").  Bad input
  comes back as `ERROR: ...` text so the model can see it.  JSON is read
  with a small regex reader (pure JDK) so `make test` needs no extra jars.
- `AgentDriver`: `parseAction` takes the first `Action:` / `Action Input:`
  pair (case and spacing tolerant); `shouldStop` stops on a usable
  `Final Answer:` or at the last step.  The driver also cuts every response
  at the first `Observation:` the model writes itself (observations come
  only from tools), and has a token budget checked *before* each call, so
  the call that would overrun is never sent.  Both budgets end the
  transcript with an explicit `ABORTED: ...` line.
- `AgentMain` (`make run Q="..."`, knobs via `PROMPT`, `STEPS`, `TOKENS`,
  `TRACE`) wraps each tool in a logger and writes the trace.
- 27 tests (`make test`), offline, with a scripted LLM.

## Task 3 -- question A with the sharp descriptions

`traces/A-sharp.txt`: **2 LLM calls, 1 tool call
`search_books("Ursula Le Guin|1970|1979")` -> 1 book, Final Answer: 1,
~613 tokens, 4 s.**

Two things the first attempt taught about the *driver*, not the model:

- My first `shouldStop` treated a `Final Answer` that follows an `Action`
  in the same turn as invented and ran the action instead.  On step 2 the
  model wrote `Action: list_books` *and* `Final Answer: 1` -- a correct
  answer grounded in the real step-1 observation.  Forcing the action sent
  it into a loop: 6 calls, 5 tool calls (3 x `list_books`), ~9.4k tokens,
  no answer (`traces/A-sharp-strict-final.txt`).  The rule now accepts such
  an answer once at least one real observation has been seen, and only
  treats it as a guess on the first step.  Same model, same prompt:
  15x fewer tokens.  The stop rule is part of the agent's behaviour.
- The run is **not reproducible across sessions** even at temperature 0.
  In a later batch the same prompt went search -> list_books -> search ->
  answer (4 calls, `traces/task4/sharp-run*.txt`, 5/5 identical within the
  batch), and once took the list_books detour within a token budget
  (`traces/A-tokens1000.txt`).  Why the model's output changed between
  sessions is not verified (Ollama/llama.cpp state is a guess).  Because of
  this, task 4 is judged on 5 runs per variant, not one.

## Task 4 -- vague tool descriptions

Variants live in `prompts/variants/`; `prompts/react.txt` was never edited.
Question A, `STEPS=4`, 5 runs each (`traces/task4/`), plus one `STEPS=6`
run each (`traces/A-v*.txt`):

| Variant | Change | First tool | `list_books` called | Answer within budget |
|---|---|---|---|---|
| sharp | as shipped | search 5/5 | 5/5 (one detour) | **5/5 correct (1)** |
| v1-search-vague | search: "Search for books." (hand-out's example) | search 5/5 | 5/5 | 0/5 (also 0/1 with 6 steps) |
| v2-list-no-cost | list: "returns all the books, so nothing is missed." (cost warning removed) | search 5/5 | 5/5 | 0/5 (also 0/1 with 6 steps) |
| v3-both-vague | list: "Lists books."; search: "Search for books."; no argument format | search 5/5 | 0/5 | 0/5 -- invents `{"author": "Ursula Le Guin", "decade": "1970s"}`, gets `[]`, repeats it every step |

- **The first choice never changed.**  The query names an author and a
  decade, and the tool *names* (`search_books` vs `list_books`) carry that
  signal even when the descriptions say nothing.
- **What changed is whether the agent finishes.**  With the sharp prompt
  it wraps up after the detour; with either one-line change it keeps
  alternating search -> list_books and never commits to an answer, at
  ~8-9k tokens for 6 steps instead of ~0.6k.
- **Removing the argument format is the worst change.**  The model invents
  its own (JSON with a "decade" key), the tool cannot read it and returns an
  empty list, and the agent retries the identical call forever.  It never
  falls back to `list_books`, which would have worked.  The tool's tolerant
  parsing made this worse: it treated the JSON as an author name and
  answered `[]` instead of `ERROR: ...`, so the model got no hint that the
  *call* was wrong.

## Task 5 -- step budget 3

Question: "Compare how many books Ursula K. Le Guin, Octavia E. Butler and
Neal Stephenson each published in the 1990s. Who published the most?"
(needs 3 searches + an answer = 4 calls; truth: Le Guin 0, Butler 1,
Stephenson 2).

`traces/B-steps3.txt`: **3 LLM calls, 2 tool calls, then
`ABORTED: step budget 3 exhausted before a Final Answer`** -- the third
response's action is not executed, the transcript ends cleanly, `run()`
returns normally.

With 8 steps (`traces/B-steps8.txt`) the result is the same, only longer:
the model runs `search_books("Ursula K. Le Guin|1990|1999")` 7 times, gets
the correct `[]` every time, and never moves on to Butler.  A 3B model does
not carry a multi-step plan here; the budget is what ends it.

## Task 6 -- token budget

Predictions were written down before running.

| Run | Budget | Predicted | Actual |
|---|---|---|---|
| `A-v2-tokens1500` (v2 prompt, list_books path) | 1500 | stop before call 3 (~610 used, next prompt with the catalogue ~1200) | **stopped before call 3: used 563, next prompt ~1162** |
| `A-tokens1000` (sharp prompt) | 1000 | not fire (the sharp run used ~613) | **fired**: this time the model took the list_books detour; used 609, next prompt ~1183 |

The budget did what it should in both; the wrong prediction in the second
row is the non-determinism above.  It also shows what the budget is for:
the one expensive observation (the full catalogue, ~840 tokens) is what
pushes the next prompt over, and the driver refuses to send that prompt.

## Reflection questions

**Which tool did the agent prefer, and why?**
`search_books`, on the first call, in every run (29/29 traces, all prompts).
The query maps directly onto an author/year filter, and the name alone says
"filter".  But "preferred" is generous: after a correct search the model
regularly calls `list_books` anyway, repeating its first Thought word for
word.  It does not treat the transcript as its own history -- it restarts
the plan each turn -- so the greedy tool gets called not by choice but
because the loop does not converge.  The Thought lines in the traces
("I need to filter the catalogue by author and year" before calling
`list_books`) are narration, not the reason for the action.

**How much did description quality matter?**
A lot for *convergence and cost*, little for the *first choice*.  One
vague line was enough to turn a 2-4 call run into a non-terminating
alternation (0/5 answers within budget, ~15x tokens).  Dropping the
argument format broke the tool interface outright.  For a model this
small, the description is not just documentation: it carries the
interface contract and the cost signal, and the model cannot recover
either from the tool's name.

**What would a real production driver need on top of this?**
- Native tool calling (structured function calls with a JSON schema)
  instead of parsing free text; validate arguments against the schema and
  return a clear error on mismatch instead of a silently empty result.
- Stop sequences on `Observation:` at the API level, rather than cutting
  invented observations after the fact.
- Loop detection: the same (tool, argument) twice in a row should be
  answered with "you already ran this; result: ..." or end the run.
- Real token accounting from the provider's usage fields, budgets for
  wall-clock time and money, and per-tool output limits (paginate or
  truncate `list_books`).
- Structured traces (JSON lines: step, prompt hash, tokens, latency, tool,
  args, result size, stop reason) and evaluation over many runs with known
  answers, because single runs are not reproducible even at temperature 0.
- Pinned model version and prompt version recorded in every trace.
- Retries with backoff for the LLM and tools, timeouts, and a graceful
  "I could not answer within budget" result for the user instead of a raw
  transcript.
