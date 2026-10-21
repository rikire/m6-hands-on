# Session 6A -- RAG over a local Java repo: notes

Setup: embedder `all-MiniLM-L6-v2` (langchain4j 0.36.0, local ONNX), LLM
`llama3.2:3b` via Ollama (temperature 0), top-k = 8, corpus `sample-repo/`
(8 `.java` files).  Raw logs are in `runs/`.

| Log | What it is |
|---|---|
| `runs/starter-stub.txt` | starter code (`main`), stub chunker + stubs, task 3 "before" |
| `runs/retrieval-method.txt` | method chunker, retrieval only |
| `runs/rag-method.txt` | method chunker, retrieval + LLM answers (tasks 5, 6, 8) |
| `runs/retrieval-lines50.txt` | 50-line chunker, retrieval only (task 7) |
| `runs/rag-noguard.txt` | method chunker, anti-hallucination line removed (task 8) |

## 1. Indexer stats (task 3)

| Chunker | Chunks | Index time |
|---|---|---|
| Starter stub (one chunk per file) | 8 | 24-26 ms (stub embedder) |
| Method-level (task 1) | 51 | 540-717 ms (MiniLM) |
| Fixed 50-line (task 7) | 12 | 197 ms (MiniLM) |

51 / 8 ~ 6.4 chunks per file.  The method chunker emits one chunk per
method/constructor plus one header chunk per type (fields + class
signature), so the count tracks the method count rather than the file
count -- it is splitting, not merging.

## 2. Ten questions (tasks 5 and 6)

"Right chunk" = a retrieved chunk from the named file that contains the
evidence string (scored automatically in `Main.isRight`).  Correctness is
my judgement against the source.

| # | Question | Right chunk | Rank | Answer (short) | Correct? |
|---|---|---|---|---|---|
| 1 | Where is the fine for an overdue loan computed? | `FinePolicy.java` `PER_DAY.multiply` | 6 | `StandardFinePolicy.computeFine` | yes |
| 2 | What class holds the catalogue of books, and how is it indexed? | `Catalog.java` `byIsbn` | 1 | `Catalog`, a map keyed by ISBN | yes |
| 3 | How does the system decide a loan has been returned? | `Loan.java` `returnedOn != null` | 1 | by checking `Loan.returnedOn` | yes (hedged: "does not explicitly decide") |
| 4 | What is the maximum fine the policy allows? | `FinePolicy.java` `CAP` | 3 | 500 rupees | yes |
| 5 | Which methods write to the loan registry? | `LoanRegistry.java` `history.add` | 2 | `record()` and `allLoans()` | partial: `record()` right, `allLoans()` is a read |
| 6 | How many books can a member have on loan at the same time? | `Member.java` `size() >= 5` | 2 | "context is insufficient" | **no**: the cap is 5 and that chunk was retrieved |
| 7 | How many days is the standard loan term? | `Loan.java` `DEFAULT_TERM_DAYS` | 2 | 21 days | yes |
| 8 | What happens when someone borrows a book whose ISBN is not in the catalogue? | `LibraryService.java` `unknown book:` | 1 | `IllegalArgumentException("unknown book: ...")` | yes |
| 9 | How is the number of loans per genre computed for the committee report? | `ReportService.java` `counts.merge` | 1 | `loansByGenre()` + `HashMap.merge` | yes |
| 10 | When are two Book objects considered equal? | `Book.java` `isbn.equals` | 1 | when ISBNs are equal | yes |

**Retrieval hit-rate (right chunk in top-8): 10/10.**
Stricter cut-offs: @1 = 5/10, @3 = 9/10.

**Generation: 8/10 correct, 1 partial (Q5), 1 wrong (Q6).**

Retrieval and generation fail independently.  Q6 is a generation failure:
the right chunk was at rank 2, but the 3B model read
`activeLoans.size() >= 5` as "does not specify the maximum" and fell back
on the "say so plainly" escape hatch.  Q5 is a reasoning error on a
correctly retrieved chunk (it calls a read-only getter a write).  Q1 shows
the opposite case: the right chunk was only at rank 6, but the answer was
still correct because the interface chunk at rank 1 names `computeFine`.

Side note on Q3: the honest answer has a twist that the model missed --
`Loan.markReturned` sets `returnedOn`, but nothing in the corpus calls it
(`Member.returnLoan` only removes the loan from `activeLoans`), so a loan
is never actually marked returned.

## 3. Bad chunker: fixed 50-line chunks (task 7)

| Chunker | @1 | @3 | @8 | Mean context per question |
|---|---|---|---|---|
| Method-level | 5/10 | 9/10 | 10/10 | 2 410 chars |
| 50-line | 6/10 | 8/10 | 10/10 | 8 539 chars |

Per-question rank of the right chunk (method -> 50-line):
Q1 6->1, Q2 1->1, Q3 1->1, Q4 3->1, Q5 2->1, Q6 2->4, Q7 2->2, Q8 1->7,
Q9 1->3, Q10 1->1.

**The @8 fraction did not change (10/10 -> 10/10), and that is an
artefact of this corpus, not evidence that chunking does not matter.**
Every file is 37-66 lines, so a 50-line chunk is almost a whole file: the
corpus becomes 12 chunks and top-8 returns two thirds of the code base.
You cannot miss.  The cost shows up elsewhere:

- **3.5x more context per question** (8.5k vs 2.4k chars) for the same
  @8 hit-rate -- exactly the "crank k / bigger chunks" trap: more tokens
  for no gain.
- **Precision drops for questions about one specific method**: Q8
  (`borrow`) falls from rank 1 to 7, Q9 (`loansByGenre`) from 1 to 3, Q6
  from 2 to 4.  A whole-file vector is the average of every method in it,
  so a query about one method has to compete with the rest of the file.
- Questions whose answer is a file-level constant (Q1, Q4, Q5) get
  *better* @1 with whole-file chunks, because the file name and JavaDoc
  dominate the vector.

On a realistic repo (files of hundreds of lines, thousands of chunks) the
50-line split would also cut methods in half, and @8 would collapse.  Here
the visible effect is bloat and lost precision rather than misses.

## 4. Question not in the corpus (task 8)

Question: *"How does the library send overdue reminder emails using
Spring Boot's JavaMailSender?"* (no email, no Spring anywhere in
`sample-repo/`).

- **With the guard** (`Use ONLY the context below. If the context is
  insufficient, say so plainly.`): *"Unfortunately, the provided context
  is insufficient to answer the question about sending overdue reminder
  emails using Spring Boot's JavaMailSender."*  This is the refusal the
  hand-out asks for; our `prompts/rag.txt` (as shipped) words the guard as
  "say so plainly" rather than "I do not find that in the passages", so
  the model paraphrases instead of using that exact phrase.
- **Without the guard** (`runs/rag-noguard.txt`): a confident 70-line
  answer with Spring code that "modifies `LibraryService`", using
  `SimpleMailSender` / `JndiMailSender` (not real Spring classes),
  `loan.getTitle()` and `loan.getMember().getEmail()` (neither exists in
  the corpus -- `Loan` has `getBook()`/`getBorrower()`, `Member` has no
  email), and an `isAfter(loan.getReturnedOn())` check that is backwards
  and would NPE on an open loan.  Retrieval was identical in both runs;
  only the instruction changed.
- **The trade-off cuts both ways**: without the guard, Q6 was answered
  *correctly* ("capped at 5"), and Q3 lost its hedge.  For a 3B model the
  guard suppresses hallucination on unanswerable questions but also makes
  it give up on answerable ones it is unsure about.

The guard line was restored afterwards (`git diff prompts/rag.txt` is
empty).

## 5. Takeaways

- Retrieval was not the bottleneck here (10/10 @8); the generator was.
  Evaluating them separately is what makes that visible -- Q6 would look
  like a retrieval bug if you only read the final answer.
- Chunking dominates *cost and precision* even when the hit-rate metric is
  saturated; on a corpus this small, @8 is too lenient to tell chunkers
  apart, so @1/@3 and context size are the more informative numbers.
- The embedding model id is stored with the index and checked at query
  time (`RagAssistant.retrieve` throws on mismatch), so re-indexing with a
  different embedder cannot silently produce meaningless scores.
