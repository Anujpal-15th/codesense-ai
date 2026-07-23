# CodeSense AI — Project Memory

> A complete reference for the whole system: what it is, how every piece works, why
> the key decisions were made, and how to extend and deploy it. Written so it can be
> picked back up months later with no other context.
>
> Author: **Anuj Pal** · GitHub: [@Anujpal-15th](https://github.com/Anujpal-15th)

---

## What is this project?

CodeSense AI is a DSA (Data Structures & Algorithms) practice platform. You paste a
Java or Python function and it does two independent things at once:

1. **Analyzes it** with an LLM — names the algorithmic pattern (Sliding Window, Two
   Pointers, Hash Map, Binary Search, Dynamic Programming, DFS/BFS, …), estimates time
   and space complexity, judges whether it's optimal, and gives readability/structure
   feedback plus a quality score.
2. **Runs it for real** in a sandbox and captures a genuine step-by-step execution
   trace — every line, the variables at each step, the call stack, console output — and
   the frontend replays it as a scrubbable, animated visualization with a live line
   highlight.

The core idea: the visualization is driven by **real captured execution data**, never
by an LLM guessing at control flow. Java is traced with the JVM's debugger interface
(JDI); Python with `sys.settrace`. Both produce the exact same trace JSON shape, so the
frontend renders them identically.

Aimed at developers prepping for technical interviews who want structured, honest
feedback plus a way to *see* how their code actually executes.

---

## Architecture

Monorepo, two independent apps that only share an HTTP contract:

```
codesense-ai/
├── backend/    Spring Boot REST API (Java 17, Maven)   → api on :8080
└── frontend/   React SPA (Vite)                        → dev server on :5173
```

**Request flow (production):**

```
Browser ─► Vercel (static frontend + /api/* rewrite) ─► Azure VM :8080 (Spring Boot) ─► Neon (Postgres)
                                                              │
                                                              ├─► LLM provider (Google Gemini by default)
                                                              └─► Execution sandbox (Docker on Linux / local process in dev)
```

- **Frontend** — React 19 + Vite, Tailwind CSS v4, Monaco editor. Two feature areas:
  the *Workspace* (`/analyze`) where you write code and hit Run/Submit, and *History*
  (`/history`) which lists your past analyses. State lives in Zustand stores; there are
  two independent pairs — `analysisStore`/`analysisApi` and `executionStore`/`executionApi`
  — plus `workspaceStore` for editor/tab/language state.
- **Backend** — Spring Boot 3.3.4. Two feature packages: `com.codesense.analysis`
  (LLM analysis + persistence) and `com.codesense.exec` (real code execution + tracing),
  with `com.codesense.config` for CORS, rate limiting, and per-provider LLM client beans,
  and `com.codesense.validation` for the shared submission validator.
- **Database** — PostgreSQL, hosted on Neon in production. Both analyses and executions
  are persisted (`analysis` + its bugs/edge-cases/tips element-collection tables, and
  `execution_history` with the full trace as `TEXT` JSON), each best-effort and scoped to
  the caller's `X-User-Id` — a save failure never fails the response the work already
  earned. Both have a `GET .../summary` (lightweight list) + `GET .../{id}` (full record,
  owner-checked) pair; see *Key backend endpoints*.
- **No login, but identity is server-signed, not client-trusted.** `X-User-Id` scopes
  history to "whoever submitted it" with no accounts/passwords - but the id itself is no
  longer an arbitrary string the client makes up. `GET /api/identity` mints
  `<uuid>.<HMAC-SHA256 signature>` (`UserIdentityService`); `UserIdentityFilter` verifies
  the signature on every request and silently strips the header (degrading to anonymous,
  never an error) if it doesn't verify. Before this, the backend trusted any string handed
  to it outright - a captured or guessed id worked exactly as well as the real one. The
  frontend (`lib/userId.js`) fetches its id from that endpoint once and caches it in
  localStorage, instead of generating its own UUID. See *Session-based user isolation*
  below for the full rationale and what this still doesn't solve (leaked-token replay).

### Key backend endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET`  | `/api/identity` | Mints a fresh signed `X-User-Id` token (`UserIdentityService`). The frontend calls this once and caches the result; no request body. |
| `POST` | `/api/analyses` | Analyze a snippet with the LLM, persist it, return the full result. Body: `{codeSnippet, language}`. |
| `GET`  | `/api/analyses/summary` | Lightweight history list for the current `X-User-Id` (scalar columns only, no heavy TEXT/collection loads). |
| `GET`  | `/api/analyses/{id}` | Full analysis by id — **only if it belongs to the caller's `X-User-Id`** (else 404). |
| `POST` | `/api/executions` | Compile+run (Java) or run (Python), persist it, return the full `ExecutionTrace`. Body: `{sourceCode, language}`. |
| `GET`  | `/api/executions/summary` | Lightweight run history list for the current `X-User-Id`. |
| `GET`  | `/api/executions/{id}` | Full execution record (incl. trace + source) by id — owner-checked, same 404-on-mismatch pattern as analyses. |

Both POST endpoints are rate-limited per IP; the two `GET .../{id}` endpoints have a
lighter, generic-abuse-only rate limit (not an authorization boundary — see
*Rate limiting*).

---

## How the execution engine works

The whole point is a **real** trace, so nothing here is faked. The two languages take
different paths but converge on one JSON shape (`ExecutionTrace`).

### The shared trace format

`ExecutionTrace` = a list of `TraceStep`s + an outcome (`NORMAL` / `EXCEPTION` /
`TIMED_OUT` / `TRUNCATED`) + console output + optional exception info + timing. Each
`TraceStep` has an `eventType` (`LINE` / `METHOD_ENTRY` / `METHOD_EXIT`), the full call
stack at that moment (each frame = class/method/line + local variables + optional `this`),
a console-output delta, and an optional return value.

Variable values use a **discriminated union**, `TraceValue`, keyed by `valueKind`:
`primitive`, `string`, `null`, `array`, `object`, `map`, `set`, `list`, `truncated`.
Every value is depth/size/element-count bounded and cycle-safe. Because Java and Python
both emit this exact shape, the frontend renders them with one set of components and no
per-language branching.

### Java: JDI (Java Debug Interface)

Path: `ExecutionService.executeJava()` → `JavaSourceCompiler` → `SandboxRunner` →
`JavaTracer`.

1. **Entry-point detection & wrapping.** JDI can only trace a *running* program, so
   something must call the user's method with concrete arguments. `EntryPointDetector`
   checks for a `public class Main`. If absent (the usual case — a bare LeetCode
   `class Solution { … }`), `DeterministicWrapperGenerator` tries a fast, non-LLM,
   regex-based wrap: it finds the primary public method, synthesizes sample arguments for
   well-known types (primitives, arrays up to 2D, `String`, `List<T>`, `Map`/`Set`,
   `ListNode`, `TreeNode`, simple 1-constructor custom classes), and appends a runnable
   `Main`. If the deterministic wrap can't handle the types, it falls back to
   `SolutionWrapperGenerator`, which asks the active LLM to rewrite the snippet into a
   runnable `Main`. The source that was actually compiled/run is returned as
   `executedSourceCode` — the frontend must display *that* (not the original) on the
   Visualize tab, because the trace's line numbers refer to it.
2. **Compile in memory** with `javax.tools.JavaCompiler` (`-g` for debug info,
   `-proc:none` to disable annotation processing on untrusted source). The file manager is
   created once and reused across requests (re-creating it re-reads the JDK platform image
   and roughly doubled compile time), so `compile()` is `synchronized`.
3. **Launch under a suspended JDWP debug port** via the sandbox runner.
4. **Attach with JDI** (`JavaTracer`) and drive step / method-entry / method-exit /
   exception events, scoped away from `java.*`/`javax.*`/`jdk.*`/`sun.*`/`com.sun.*`.
   Build the trace step by step. A user program that throws or times out is a **normal,
   successful** outcome (HTTP 201) — only real infra failures (compile error, sandbox
   launch failure, JDI attach failure) become HTTP errors.

`jdk.jdi` is not a default root module for classpath apps, so it's added explicitly in
three places: compile (`--add-modules jdk.jdi` in the compiler plugin), `spring-boot:run`
(jvmArguments), and the packaged jar launch (`java --add-modules jdk.jdi -jar …` — there
is no jar-manifest equivalent for `--add-modules`, so the production launch command must
include it).

### Python: `sys.settrace`

Path: `ExecutionService.execute()` routes `language == "python"` to
`PythonExecutionHandler`, which delegates the actual tracing to a bundled Python harness,
`backend/src/main/resources/python/tracer.py`.

There's no compile step and no debug port. `PythonExecutionHandler`:

1. Optionally wraps bare `def`s that have no top-level call (mirrors the deterministic
   Java wrapper — appends `if __name__ == "__main__": print(fn(3, 4, …))`). Code with a
   top-level statement (like `print(twoSum(...))`) runs as-is.
2. Writes `main.py`, `tracer.py`, and a `limits.json` into a temp work dir, then runs
   `python tracer.py main.py limits.json` (limits go through a **file**, not argv — Windows
   argv quoting strips the JSON quotes).
3. `tracer.py` installs a `sys.settrace` hook, runs the user program, and on each
   `call`/`line`/`return` event records a `TraceStep` in the exact `ExecutionTrace` shape
   (including the `valueKind` discriminator). Crucially, the user program's own
   `print()` output is redirected into an in-memory buffer and embedded in the JSON — it
   never touches real stdout. That's what makes "print the trace JSON on stdout" a safe
   transport with no writable mount required, in both local and Docker modes.
4. `tracer.py` enforces the same limits as Java (max steps → `TRUNCATED`, wall-clock
   timeout → `TIMED_OUT`, depth/element/string caps, cycle detection). A `SyntaxError`
   becomes a normal `EXCEPTION` outcome (the Python analogue of a Java compile error),
   so the UI shows the real message and line number.
5. `PythonTracer.parse()` binds the harness's stdout JSON straight into `ExecutionTrace`
   with Jackson — no translation layer.

### Sandboxing (both languages)

`execution.sandbox.type` switches the runner, same pattern for Java and Python:

- **`local-process`** (dev default) — a plain subprocess. **Zero isolation.** Never use
  this anywhere that accepts untrusted input over the network.
- **`docker`** (production on Linux) — a locked-down container: memory/CPU/pids limits,
  read-only root filesystem + a small tmpfs `/tmp`, and for Java a JDWP port published to
  host **loopback only** (it's unauthenticated remote code execution, must never be
  LAN-reachable). Python needs no published port — the trace comes back on stdout.

**Egress blocking is not in the `docker run` flags.** `--network none`/`--internal`
would block outbound access but also disable the `-p` port publishing the Java JDI attach
needs. So the sandbox joins a normal user-defined bridge network and egress is dropped by
a host-level `DOCKER-USER` iptables rule, provisioned once by
`backend/docker/execution-sandbox/provision-sandbox-network.sh`. If that network + rule
aren't provisioned, the container has full internet access. This is a Linux-only design
(Docker Desktop on Windows/macOS runs in a VM where `DOCKER-USER` isn't cleanly reachable),
which is why dev uses `local-process`.

---

## How AI analysis works

Path: `POST /api/analyses` → `AnalysisController` → `AnalysisService.analyze()` →
active `LlmClient` → post-LLM guards → persist → return.

### The pluggable LLM provider

`AnalysisService` depends on the `LlmClient` interface, not a concrete provider. Exactly
one implementation is active per run, chosen by `llm.provider` (env `LLM_PROVIDER`):

| provider | client | model (default) | notes |
|---|---|---|---|
| `gemini` (**default**) | `GeminiLlmClient` | `gemini-flash-latest` | needs `GEMINI_API_KEY`; free tier, no card. Uses Google's `-latest` alias (not a pinned version) deliberately - see the gotcha below. |
| `anthropic` | `AnthropicLlmClient` | `claude-sonnet-5` | needs `ANTHROPIC_API_KEY`. |
| `github-models` | `GithubModelsLlmClient` | `openai/gpt-4o-mini` | **retired by GitHub on 2026-07-30 - do not re-select this provider**, the API no longer exists. Client code left in place only as a reference/example of the pattern. |
| `ollama` | `OllamaLlmClient` | `llama3.2:1b` | fully local/offline, no key; slower and less reliable for the structured prompt. |

**Gotcha - don't pin a specific Gemini model version.** `gemini.model` was originally
`gemini-2.0-flash`, hardcoded. Google retired its free-tier quota for that model, so it was
changed to `gemini-2.5-flash` - which *also* lost free-tier access within the same day
("no longer available to new users"). Pinning any specific dated model name is a
recurring-outage risk with how fast Google rotates the free tier. Fixed by switching to
Google's own `gemini-flash-latest` alias, which is documented as "hot-swapped with every
new release" - this should never need updating again regardless of what Google ships next,
and the days-scale churn is why it's not safe to assume a currently-working pinned name
will still work next week. Also relevant: newer Gemini "thinking" models spend part of
`maxOutputTokens` on an internal reasoning pass before the actual answer, which was
silently truncating/corrupting the JSON response until the budget was raised to 8192 to
give both passes room (`GeminiLlmClient`). A `thinkingConfig.thinkingBudget=0` field to
disable thinking outright was tried and reverted - Gemini rejected it with a generic 400
on this account's model, and the exact schema for it isn't stable enough across model
generations to be worth chasing; the larger token budget sidesteps the question entirely.

Each provider's client + its `RestClient` config bean are gated with
`@ConditionalOnProperty` so only the selected one is instantiated — the other providers'
API keys can be unset without breaking startup. Each client fails fast in its constructor
if its own key is blank when selected. Wire formats differ per provider, so each has its
own request/response record set; what's shared is `LlmClient.SYSTEM_PROMPT` and
`LlmResponseParser` (strips markdown fences, parses into the provider-agnostic
`AnalysisResult`). The prompt is language-agnostic (a "DSA coach"), so Java and Python use
the same prompt — only the deterministic guards below branch by language.

### The deterministic post-LLM guards

LLMs sometimes confidently mislabel. Two cheap regex guards run *after* the LLM (microseconds,
no extra round trip) to catch provably-wrong claims. **Both are language-aware** — the same
structural check has different fingerprints in Java vs Python (Java loops parenthesize the
condition, Python doesn't; Java hashing uses `HashMap`/`.put(`, Python uses `{}`/`dict`),
and running the wrong language's regex silently withholds correct labels.

- **`PatternEvidenceGuard`** — if the model names a well-known pattern but the code plainly
  lacks its most basic structural prerequisite (e.g. "Sliding Window" on code with no loop),
  the label is swapped for `"Pattern unclear - manual review suggested"` and the explanation
  is annotated. It only guards patterns with an unambiguous, cheap fingerprint; anything else
  passes through. It checks the raw (unmasked) snippet on purpose — a comment can only *add*
  evidence (prevent a false trip), never cause one.
- **`ComplexityClaimGuard`** — narrowly neutralizes a complexity-*improvement* claim only
  when it's provably incoherent from arithmetic alone (suggested bound not strictly better
  than current) or self-contradictory (advises a technique the code already uses). It never
  tries to validate improvement claims in general — that needs problem knowledge no regex has.

There's also **`CustomDataStructureDetector`** (pre-LLM): if the snippet defines a custom
self-referential structure (a class with a `next`/`left`/`head`-style field) and uses no
collection, it appends a hint to the prompt so the model doesn't force-fit a named pattern
onto what is really "just a linked list / tree implementation."

### The submission validator

`CodeSubmissionValidator` runs before any LLM call or execution (the backend is
public-facing and can't trust the frontend). It rejects prose/prompt-injection ("write me
a merge sort") and wrong-language paste (Java code while Python is selected, or vice
versa), with language-specific messages. The frontend has a mirror of this in
`frontend/src/lib/codeValidation.js`; keep the two in sync.

### Result shape

Beyond pattern/complexity/optimal/explanation, the analysis also returns readability,
structure, style suggestions, a suggested time complexity, efficiency suggestions, an
overall score, code-quality and maintainability ratings, and lists of bugs / edge cases /
learning tips. Older rows predate some of these columns and store `NULL` — the frontend
renders those as a graceful "not available," never a fabricated value.

---

## Key design decisions and why

- **Real traces, not LLM-guessed execution.** The whole product promise is that the
  step-through is *true*. That's why we pay the cost of JDI and `sys.settrace` instead of
  asking an LLM "what happens when this runs." It also means the visualizer can be trusted
  as a learning tool.
- **One trace shape for every language.** `tracer.py` deliberately emits the identical
  `ExecutionTrace`/`TraceValue` JSON the Java JDI tracer produces. This is the single most
  important design choice for keeping the frontend simple — zero per-language rendering code.
- **Deterministic wrapper before the LLM wrapper.** Wrapping a bare snippet so it can run
  used to always call the LLM (slow, and small models hallucinate broken driver code). The
  regex-based `DeterministicWrapperGenerator` handles the common cases in sub-second time,
  with the LLM only as a fallback for exotic types.
- **Guards are deterministic, not a re-prompt.** Catching a bad label with a second LLM
  call would cost multiple seconds every time. Regex guards are microseconds and match the
  project's never-fabricate principle (they downgrade to honest "unclear," they don't invent).
- **Docker sandbox with host-firewall egress control.** The natural `--network none` breaks
  the debug-port publishing JDI needs, so isolation is split: container resource limits via
  `docker run` flags + egress via a host `DOCKER-USER` iptables rule. Documented as a
  one-time provisioning step because it's a real deployment prerequisite, not self-contained.
- **Vercel for frontend, Azure VM for backend.** The frontend is static and benefits from
  Vercel's CDN + zero-config deploys. The backend needs a real JVM, a JDK compiler, JDI, and
  (in production) Docker for the sandbox — none of which fit a serverless function, so it
  runs on a plain VM under systemd. Vercel rewrites `/api/*` to the VM so the browser sees a
  single origin.
- **Neon for Postgres.** Managed, serverless Postgres — no database to run on the VM, and it
  survives VM rebuilds.
- **No login, but identity is server-signed.** For a portfolio/demo tool, a full auth
  system is overkill and a barrier to trying it. `X-User-Id` keeps each visitor's history
  separate without any login - but see the note below on why it's server-signed rather
  than client-trusted.

### Session-based user isolation (important subtlety)

History is scoped by the `X-User-Id` header. Two non-obvious traps here, one old and one
found later:

1. Spring Data JPA derived queries turn `findBy…AndUserId(id, null)` into
   `… AND user_id IS NULL`, which would match the *legacy* rows that predate the column
   (they have `NULL` userId). So the service layer short-circuits null/blank userId to
   "empty list" / "404" *before* hitting the repository - a missing header must mean "no
   history," never "everyone's history" and never "the orphaned legacy rows."
2. **The id itself used to be trusted outright.** The frontend generated a random UUID
   client-side and the backend accepted *any* string in the header verbatim - a captured
   or guessed id worked exactly as well as the real one, since nothing distinguished
   "an id we issued" from "a string someone typed in." Fixed with `UserIdentityService`
   (HMAC-signs every id the server mints via `GET /api/identity`) + `UserIdentityFilter`
   (verifies the signature on every request, silently degrading an invalid one to "no
   header sent" rather than trusting or erroring on it). This does **not** solve
   capture/replay - a leaked *signed* id is just as reusable as a leaked unsigned one was;
   that needs real sessions/login, out of scope for a no-login tool. What it removes is
   trivial forgery: before, typing any string into the header worked; now it must be a
   token this server actually minted.

### Rate limiting

`RateLimitFilter` (Bucket4j, in-memory Caffeine cache of IP → bucket) limits the two
expensive POST endpoints (`/api/analyses` - external LLM cost; `/api/executions` - compiles
+ spawns a process, the real DoS surface) plus a much more generous limit on the two
single-record `GET .../{id}` reads (plain DB queries, so this is abuse/resource
protection only - **not** an authorization boundary, since both already scope by
`findByIdAndUserId` and the caller's own summary endpoint already hands back every id
they're allowed to read). The summary list endpoints stay unlimited - a single cheap query
each, not id-driven. `X-Forwarded-For` is only trusted when the direct peer is in
`rate-limit.trusted-proxies`, so a client hitting the backend directly can't spoof its
source IP. Behind the reverse proxy on the deployment host, add that proxy's address to
`trusted-proxies`.

---

## Known limitations and future improvements

- **Collections in Java traces are semantic now, but boxed primitives are verbose.** Maps/
  Sets/Lists serialize as real entries/elements (via `invokeMethod` on the debuggee), but
  the Memory panel lists each boxed `Integer` as its own heap node. Cosmetic.
- **Python frame class name is `main`.** Python has no class for module-level code, so the
  tracer labels frames `main.<function>`. Reads fine (module.function) but isn't a real
  class name the way Java's is. Could be prettified.
- **Docker sandbox egress isolation is Linux-only and not fully end-to-end verified.** The
  design is sound and the individual Docker behaviors were tested, but the complete
  provision-network-+-iptables path should be verified on the live host with the printed
  checks.
- **`local-process` sandbox has no isolation.** Fine for local dev, unacceptable for any
  public host — production must use `docker`.
- **Bearer-token replay is still possible.** A captured *signed* `X-User-Id` is just as
  reusable as the old unsigned scheme was - the signing (see *Session-based user
  isolation*) only stops forgery, not theft-and-reuse of a real token. Closing that needs
  real sessions/login.
- **LLM label variance.** Even with the guards, the same snippet can get slightly different
  pattern labels run to run (e.g. a hash-map two-sum sometimes called "Two Pointers"). The
  guards only remove *provably* wrong claims, not merely debatable ones.
- **Test-case generation / AI code review** were part of the original larger vision and
  aren't built.
- **No automated test suite** in the backend yet; there's a `regression-suite.mjs` at the
  repo root that hits a running backend with a fixed set of analysis + execution cases and
  prints a pass/fail report (label matching is substring-based because LLM output varies).

---

## How to add a new language (worked example: JavaScript)

The architecture is built for this. To add JavaScript:

**Backend**

1. **Tracer.** Write a `resources/js/tracer.js` harness that runs the user code and emits
   the same `ExecutionTrace`/`TraceValue` JSON on stdout (the way `tracer.py` does for
   Python). For JS you'd use the `node:inspector` API or a source-instrumentation approach;
   the key contract is the JSON shape, including the `valueKind` discriminator and the
   step/line/frame structure.
2. **Handler.** Add a `JsExecutionHandler` modeled on `PythonExecutionHandler` — resolve
   the `node` executable, write the work-dir files, run `node tracer.js main.js limits.json`,
   collect stdout/stderr, hard-kill on timeout, and parse stdout into `ExecutionTrace`.
   Reuse the same local-process / Docker split (add `node` to the sandbox Dockerfile).
3. **Route it.** In `ExecutionService.execute()`, add a `"javascript"` branch to the
   language switch that calls the new handler. Do the same in `AnalysisService.analyze()`
   for validation (the LLM prompt itself is language-agnostic — no change needed there).
4. **Validation + guards.** Add a `"javascript"` branch to `CodeSubmissionValidator`
   (and its frontend mirror), and add JS structural fingerprints to `PatternEvidenceGuard`
   / `ComplexityClaimGuard` (JS loops parenthesize like Java; hashing uses `Map`/`Set`/`{}`;
   recursion uses `function name(` / `const name = (`). This is exactly what the Python
   support added — follow that commit as the template.
5. **Request records.** The `language` field already accepts a regex; widen the
   `@Pattern` in `ExecutionRequest`/`AnalysisRequest` to include `javascript`.

**Frontend**

6. Add `javascript` to the language dropdown (`EditorToolbar`), a default skeleton and an
   example snippet (`workspaceStore` / `exampleSnippet.js`), Monaco language mapping (it
   already supports `javascript`), and a validation branch in `codeValidation.js`.

That's it — because every language emits the same trace JSON, the entire Visualize tab
(variables, call stack, memory, line highlight, playback) works with no frontend changes.

---

## How to deploy

Everything is automated through GitHub Actions on push to `main`
(`.github/workflows/deploy.yml`). Jobs run in order: **build** → **deploy (backend)** →
**deploy-frontend (Vercel)** → **smoke-test**. `deploy-frontend` and `smoke-test` both
`needs: deploy` - if the backend deploy job fails, the frontend deploy is **skipped
entirely**, even for a frontend-only change. Known gap, not yet fixed: frontend deploys
shouldn't be hostage to backend deploy health.

**Backend (Azure VM, `codesense` systemd service, port 8080):**
- The deploy job SSHes into the VM (key auth, non-interactive - no TTY) and runs
  `git fetch origin main && git reset --hard origin/main` (reset, not pull, so a
  force-pushed history doesn't cause a merge conflict), then
  `mvn clean package -DskipTests`, `sudo systemctl restart codesense`, and a health check
  against `/api/analyses`.
- Requires on the VM: JDK 17, Maven, **Python 3** (for the Python execution path), and —
  for the real sandbox — Docker with the provisioned sandbox network + iptables rule and
  the `codesense/execution-sandbox` image built. The systemd unit (plus a
  `codesense.service.d/override.conf` drop-in for the identity secret) sets the DB env
  (`DB_USERNAME=neondb_owner`, connection string/password), `GEMINI_API_KEY`, and
  `USER_IDENTITY_SECRET` - all as plain `Environment=` lines, no external secrets manager.
- CORS: `cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`) must include the Vercel origin.
  Vercel rewrites `/api/*` server-side, which forwards the browser's `Origin` header to the
  backend, so the backend's CORS list must include `https://codesense-ai-phi.vercel.app`
  (not just localhost) or every request is rejected with "Invalid CORS request."

**Gotcha - `sudo` on the deploy VM must stay passwordless for `azureuser`, and it's
fragile.** The deploy script's `sudo systemctl restart codesense` runs over a
non-interactive SSH session with no TTY - if sudo ever decides a password is needed, the
whole deploy job fails with `sudo: a password is required`, no matter how many times you
re-run it. This actually happened: resetting `azureuser`'s login password via the Azure
Portal (done to get Serial Console access working) appears to disturb the account's sudo
grants. The fix is **not** just "add a NOPASSWD sudoers rule" - `/etc/sudoers.d/*` files
are read in **alphabetical order**, and for a command matching multiple rules, **the last
one read wins**, regardless of specificity. A pre-existing Azure-managed file
(`/etc/sudoers.d/waagent`) sorts after a naively-named override and was silently winning
with a password-requiring catch-all, even though earlier files granted NOPASSWD. The
working fix: `/etc/sudoers.d/zzz-azureuser-nopasswd` containing
`azureuser ALL=(ALL) NOPASSWD:ALL` - the `zzz` prefix guarantees it sorts last among
everything in that directory. Diagnose this class of problem with `sudo -n -l` (shows the
full *effective* rule list in evaluation order - the last line is the one that actually
applies) rather than trusting `visudo -c` alone (that only validates syntax, not which
rule wins).

**Frontend (Vercel, primary URL `codesense-ai-phi.vercel.app`):**
- The deploy-frontend job runs `npm ci && npm run build` (fast-fail gate) then
  `npx vercel --prod --yes --token=$VERCEL_TOKEN`. Vercel does its own build too.
- Requires three GitHub Actions secrets: `VERCEL_TOKEN`, `VERCEL_ORG_ID`,
  `VERCEL_PROJECT_ID` (the last two come from `frontend/.vercel/project.json`, which is
  gitignored — CI has no other way to know which project to target).
- `frontend/vercel.json` rewrites `/api/*` to the Azure backend and SPA-rewrites everything
  else to `/`.

**Database (Neon):** managed Postgres, no deploy step; schema is auto-created by Hibernate
`ddl-auto: update` on first backend startup.

**Local development:**
- Backend: from `backend/`, set `DB_USERNAME` / `DB_PASSWORD` / `GEMINI_API_KEY` (or
  another provider's key + `LLM_PROVIDER`), have Postgres reachable, then
  `mvn spring-boot:run` (:8080).
- Frontend: from `frontend/`, `npm install` then `npm run dev` (:5173). It talks to
  `http://localhost:8080` by default; override with `VITE_API_BASE_URL` (a `.env.local`
  pointing at a remote backend is handy but remember it's loaded in dev too).

---

## Tech stack reference

| Layer | Technology | Why |
|---|---|---|
| Frontend framework | **React 19** | Component model + hooks; large ecosystem. |
| Build tool | **Vite** | Fast dev server + build; first-class Tailwind v4 plugin. |
| Styling | **Tailwind CSS v4** (`@tailwindcss/vite`) | Utility-first, no separate config file needed. |
| Editor | **Monaco Editor** (`@monaco-editor/react`) | The VS Code editor; real Java/Python syntax + decoration API for the line highlight. |
| Client state | **Zustand** | Minimal, no boilerplate; per-feature stores. |
| Routing | **react-router-dom** | Standard SPA routing. |
| Animation | **framer-motion** | Pop-in/flash animations in the Memory/Variables panels. |
| Linting | **oxlint** | Fast Rust-based linter. |
| Backend framework | **Spring Boot 3.3.4** (Java 17, Maven) | Mature REST + DI; needed for a real JVM (JDI, in-memory compiler). |
| Persistence | **Spring Data JPA / Hibernate** | Entity mapping + derived queries; `ddl-auto: update` for schema. |
| Database | **PostgreSQL** (Neon in prod) | Reliable relational store; Neon = managed serverless Postgres. |
| Java execution | **JDI** (`com.sun.jdi`, module `jdk.jdi`) | Official JVM debugger interface — real step/variable/stack data. |
| Java compile | **`javax.tools.JavaCompiler`** | In-memory compilation of untrusted source with debug info. |
| Python execution | **`sys.settrace`** (bundled `tracer.py`) | Standard-library tracing hook — real per-line/variable/stack data, no deps. |
| Sandbox | **Docker** (prod) / plain process (dev) | Resource + filesystem + egress isolation for untrusted code. |
| Rate limiting | **Bucket4j** + **Caffeine** | Per-IP token-bucket on expensive endpoints, in-memory. |
| Boilerplate | **Lombok** | Getters/setters/constructors on entities and records. |
| LLM (default) | **Google Gemini** (`gemini-flash-latest`) | Free tier, no card required. Uses Google's `-latest` alias, not a pinned version - see the gotcha in *The pluggable LLM provider*. |
| LLM (alternates) | **Anthropic** (`claude-sonnet-5`), **Ollama** (local) | Pluggable behind one `LlmClient` interface. GitHub Models was the original default but was fully retired by GitHub on 2026-07-30 - do not re-enable it. |
| Frontend hosting | **Vercel** | CDN static hosting + auto-deploy + `/api/*` rewrite to the backend. |
| Backend hosting | **Azure VM** (systemd) | Needs a real JVM + JDK compiler + Docker; a plain VM fits, serverless doesn't. |
| CI/CD | **GitHub Actions** | Build → deploy backend (SSH) → deploy frontend (Vercel CLI) → smoke test, on push to `main`. |
