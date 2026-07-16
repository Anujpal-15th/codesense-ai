package com.codesense.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Python analogue of the Java compile→sandbox→JDI pipeline, collapsed into one
 * step: no compilation, no debug port, no readiness protocol. The bundled
 * {@code tracer.py} harness runs the user program under {@code sys.settrace}
 * and prints the finished {@link ExecutionTrace} JSON on stdout when the
 * program ends (or hits the step/time caps, or raises) - so this handler only
 * has to launch a process, collect stdout/stderr, enforce a hard-kill backstop,
 * and hand stdout to {@link PythonTracer#parse}.
 *
 * <p>Sandboxing mirrors the Java side's {@code execution.sandbox.type} switch:
 * {@code local-process} runs the host interpreter with NO isolation (dev only -
 * same warning as {@link LocalProcessSandboxRunner}), {@code docker} reuses the
 * execution-sandbox image (which must include python3 - see
 * {@code backend/docker/execution-sandbox/Dockerfile}) with the same
 * memory/cpu/pids/read-only flags as {@link DockerSandboxRunner} but strictly
 * fewer moving parts: no {@code -p} port publish at all, since the trace comes
 * back on stdout. Like the Java docker path, the docker branch is written for
 * the Linux deployment host and is NOT validated on this Windows dev machine.
 */
@Slf4j
@Service
public class PythonExecutionHandler {

    /** Interpreter candidates probed in order when no executable is configured.
     * Linux/macOS typically ship {@code python3}; Windows installs register
     * {@code python} and the {@code py} launcher. */
    private static final List<String> INTERPRETER_CANDIDATES = List.of("python3", "python", "py");

    /** Grace period past the tracer's own soft timeout before we hard-kill the
     * process - covers user code blocked inside C calls (time.sleep, input())
     * where no trace events fire and the soft timeout can't trigger. */
    private static final long HARD_KILL_GRACE_MS = 5_000;

    private static final Pattern DEF_PATTERN =
            Pattern.compile("^def\\s+(\\w+)\\s*\\(([^)]*)\\)", Pattern.MULTILINE);

    private final PythonTracer tracer;
    private final TraceLimits limits;
    private final ObjectMapper objectMapper;
    private final String sandboxType;
    private final String configuredExecutable;
    private final String dockerImage;
    private final String dockerMemory;
    private final String dockerCpus;
    private final String dockerNetwork;

    /** Resolved lazily on first use; volatile is enough (idempotent probe). */
    private volatile String resolvedInterpreter;

    PythonExecutionHandler(PythonTracer tracer,
                            TraceLimits limits,
                            ObjectMapper objectMapper,
                            @Value("${execution.sandbox.type}") String sandboxType,
                            @Value("${execution.python.executable:}") String configuredExecutable,
                            @Value("${execution.sandbox.docker.image}") String dockerImage,
                            @Value("${execution.sandbox.docker.memory}") String dockerMemory,
                            @Value("${execution.sandbox.docker.cpus}") String dockerCpus,
                            @Value("${execution.sandbox.docker.network}") String dockerNetwork) {
        this.tracer = tracer;
        this.limits = limits;
        this.objectMapper = objectMapper;
        this.sandboxType = sandboxType;
        this.configuredExecutable = configuredExecutable;
        this.dockerImage = dockerImage;
        this.dockerMemory = dockerMemory;
        this.dockerCpus = dockerCpus;
        this.dockerNetwork = dockerNetwork;
    }

    public ExecutionResponse execute(String sourceCode) {
        long tStart = System.nanoTime();
        WrapResult wrap = wrapIfNeeded(sourceCode);

        Path workDir;
        try {
            workDir = Files.createTempDirectory("codesense-pyexec-");
            widenForContainerUser(workDir);
            Files.writeString(workDir.resolve("main.py"), wrap.source(), StandardCharsets.UTF_8);
            Files.writeString(workDir.resolve("tracer.py"), tracer.tracerScript(), StandardCharsets.UTF_8);
            // As a file, not an argv literal: Windows argv quoting strips the
            // JSON's double-quotes when passed inline.
            Files.writeString(workDir.resolve("limits.json"), limitsJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to prepare Python execution work directory", e);
        }

        try {
            ProcessResult result = runTracerProcess(workDir);
            ExecutionTrace trace;
            if (result.hardKilled()) {
                // Process never emitted its JSON (blocked in native code past the
                // grace window). Same contract as the Java tracer's timeout: a
                // normal TIMED_OUT outcome, not an error response.
                trace = new ExecutionTrace(List.of(), ExecutionOutcome.TIMED_OUT, null,
                        "", false, 0, result.elapsedMillis());
            } else {
                trace = tracer.parse(result.stdout(), result.stderr());
            }

            log.info("Python execution timing: wrap+setup={}ms run={}ms total={}ms (steps={}, wrapped={}, outcome={})",
                    (result.startedAtNanos() - tStart) / 1_000_000,
                    result.elapsedMillis(),
                    (System.nanoTime() - tStart) / 1_000_000,
                    trace.totalStepsCaptured(),
                    wrap.wrapped(),
                    trace.outcome());

            return new ExecutionResponse(trace, wrap.source(), wrap.wrapped(), Instant.now());
        } finally {
            deleteRecursively(workDir);
        }
    }

    // ------------------------------------------------------------------
    // Wrapping: bare function definitions or a bare LeetCode-style
    // "class Solution: def method(self, ...):" with no top-level call get a
    // deterministic driver appended, mirroring DeterministicWrapperGenerator's
    // spirit on the Java side (no LLM involved). The class-method path is
    // Python's answer to Java's instance-method handling there - Java finds
    // it via brace matching, Python via indentation (no braces to match on).
    // ------------------------------------------------------------------

    private WrapResult wrapIfNeeded(String source) {
        if (hasTopLevelStatement(source)) {
            return new WrapResult(source, false);
        }
        Optional<GeneratedCall> call = synthesizeCall(source);
        if (call.isEmpty()) {
            return new WrapResult(source, false);
        }
        GeneratedCall generated = call.get();
        StringBuilder driver = new StringBuilder(source.stripTrailing())
                .append("\n\n\nif __name__ == \"__main__\":\n");
        if (generated.setup() != null) {
            driver.append("    ").append(generated.setup()).append("\n");
        }
        driver.append("    print(").append(generated.expression()).append(")\n");
        return new WrapResult(driver.toString(), true);
    }

    /**
     * True when any column-0 line is an executable statement rather than a
     * def/class/import/decorator/comment. Column-0 is the right discriminator:
     * Python top-level statements cannot be indented.
     */
    private boolean hasTopLevelStatement(String source) {
        boolean inTripleQuote = false;
        String tripleDelim = null;
        for (String line : source.split("\n", -1)) {
            String stripped = line.strip();
            if (inTripleQuote) {
                if (stripped.contains(tripleDelim)) {
                    inTripleQuote = false;
                }
                continue;
            }
            if (stripped.startsWith("\"\"\"") || stripped.startsWith("'''")) {
                tripleDelim = stripped.substring(0, 3);
                // one-line docstring ("""...""") doesn't open a block
                if (!(stripped.length() >= 6 && stripped.endsWith(tripleDelim))) {
                    inTripleQuote = true;
                }
                continue;
            }
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                continue; // blank or indented (inside a def/class body)
            }
            if (stripped.startsWith("#") || stripped.startsWith("@")
                    || stripped.startsWith("def ") || stripped.startsWith("class ")
                    || stripped.startsWith("import ") || stripped.startsWith("from ")) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Tries a bare module-level function first (the common non-LeetCode
     * case), then a class-based "Solution" method. The two are mutually
     * exclusive by construction - {@link #DEF_PATTERN} is anchored to column
     * 0, so it can never match a method indented inside a class. */
    private Optional<GeneratedCall> synthesizeCall(String source) {
        Optional<GeneratedCall> functionCall = synthesizeModuleFunctionCall(source);
        if (functionCall.isPresent()) {
            return functionCall;
        }
        return synthesizeClassMethodCall(source);
    }

    /** Synthesizes a call to the first module-level def using small ints for
     * each positional parameter (3, 4, 5, ...). Skips *args/**kwargs and
     * parameters with defaults - they don't need arguments. */
    private Optional<GeneratedCall> synthesizeModuleFunctionCall(String source) {
        Matcher m = DEF_PATTERN.matcher(source);
        if (!m.find()) {
            return Optional.empty();
        }
        String name = m.group(1);
        String paramList = m.group(2).strip();
        List<String> args = new ArrayList<>();
        if (!paramList.isEmpty()) {
            int next = 3;
            for (String rawParam : paramList.split(",")) {
                String p = rawParam.strip();
                if (p.isEmpty() || p.startsWith("*") || p.contains("=")) {
                    continue;
                }
                if (p.equals("self") || p.equals("cls")) {
                    // Method inside a class pasted bare - can't drive it deterministically.
                    return Optional.empty();
                }
                args.add(String.valueOf(next++));
            }
        }
        return Optional.of(new GeneratedCall(name + "(" + String.join(", ", args) + ")"));
    }

    // ------------------------------------------------------------------
    // Class-method wrapping: "class Solution: def method(self, ...):" with
    // no top-level driver. Python's analogue of DeterministicWrapperGenerator's
    // instance-method handling, done via indentation scanning since Python
    // has no braces to match on.
    // ------------------------------------------------------------------

    private static final Pattern CLASS_HEADER_PATTERN =
            Pattern.compile("^class\\s+(\\w+)\\s*(?:\\([^)]*\\))?\\s*:", Pattern.MULTILINE);
    private static final Pattern METHOD_DEF_LINE_PATTERN =
            Pattern.compile("^def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:->\\s*[^:]+)?\\s*:");
    // Data-structure helper classes (ListNode/TreeNode/Node) are never the
    // entry point themselves - skip past them to find the real Solution class.
    private static final Set<String> HELPER_CLASS_NAMES = Set.of("ListNode", "TreeNode", "Node");

    private static final Pattern OPTIONAL_TYPE = Pattern.compile("^Optional\\[(.+)]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_LIST_INT_TYPE = Pattern.compile("^List\\[List\\[int]]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_INT_TYPE = Pattern.compile("^List\\[int]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_STR_TYPE = Pattern.compile("^List\\[str]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_FLOAT_TYPE = Pattern.compile("^List\\[float]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIST_OF_LISTNODE_TYPE =
            Pattern.compile("^List\\[(?:Optional\\[ListNode]|ListNode)]$", Pattern.CASE_INSENSITIVE);

    private record PyMethod(String name, String rawParams) {
    }

    /**
     * Requires the class to have exactly one candidate method (non-{@code
     * __init__}, not leading-underscore) - the LeetCode convention of a
     * single public entry point. Ambiguous (0 or 2+ candidates) or a
     * parameter type this handler doesn't recognize both fall through to the
     * next class (there's usually only one), and ultimately to "no wrap" if
     * nothing matches - there's no LLM safety net for Python the way Java's
     * deterministic wrapper has, so staying conservative here matters more.
     */
    private Optional<GeneratedCall> synthesizeClassMethodCall(String source) {
        String[] lines = source.split("\n", -1);
        Matcher classMatcher = CLASS_HEADER_PATTERN.matcher(source);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            if (HELPER_CLASS_NAMES.contains(className)) {
                continue;
            }
            int classLine = lineIndexAt(source, classMatcher.start());
            Optional<PyMethod> method = findSolePublicMethod(lines, classLine);
            if (method.isEmpty()) {
                continue;
            }
            Optional<String> args = buildArgs(method.get());
            if (args.isEmpty()) {
                continue;
            }
            String varName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
            String setup = varName + " = " + className + "()";
            String expr = varName + "." + method.get().name() + "(" + args.get() + ")";
            return Optional.of(new GeneratedCall(expr, setup));
        }
        return Optional.empty();
    }

    private int lineIndexAt(String source, int charOffset) {
        int line = 0;
        for (int i = 0; i < charOffset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Walks lines after the class header to find its direct methods - no
     * braces to match, so "direct member of this class" is determined by
     * indentation: the first non-blank line after the header establishes the
     * body's indent level, and only lines at exactly that indent are
     * considered (deeper means nested inside a method; shallower means the
     * class body has ended). Triple-quoted docstrings are skipped so a
     * mentioned "def" inside one can't be mistaken for a real method.
     */
    private Optional<PyMethod> findSolePublicMethod(String[] lines, int classLine) {
        Integer methodIndent = null;
        boolean inTripleQuote = false;
        String tripleDelim = null;
        List<PyMethod> found = new ArrayList<>();
        for (int i = classLine + 1; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.strip();
            if (inTripleQuote) {
                if (stripped.contains(tripleDelim)) {
                    inTripleQuote = false;
                }
                continue;
            }
            if (stripped.isEmpty()) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (methodIndent == null) {
                methodIndent = indent;
            }
            if (indent < methodIndent) {
                break; // dedented out of the class body
            }
            if (stripped.startsWith("\"\"\"") || stripped.startsWith("'''")) {
                String delim = stripped.substring(0, 3);
                if (!(stripped.length() >= 6 && stripped.endsWith(delim))) {
                    inTripleQuote = true;
                    tripleDelim = delim;
                }
                continue;
            }
            if (indent != methodIndent) {
                continue; // nested deeper than the class's direct members
            }
            Matcher dm = METHOD_DEF_LINE_PATTERN.matcher(stripped);
            if (dm.find()) {
                String name = dm.group(1);
                if (!name.equals("__init__") && !name.startsWith("_")) {
                    found.add(new PyMethod(name, dm.group(2)));
                }
            }
        }
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    /** Builds the comma-joined argument list for a method call, skipping
     * {@code self}/{@code cls}, {@code *args}/{@code **kwargs}, and
     * defaulted params (same convention as the module-function path).
     * Returns empty if any remaining parameter's type annotation isn't one
     * this handler recognizes. */
    private Optional<String> buildArgs(PyMethod method) {
        List<String> args = new ArrayList<>();
        int nextInt = 3;
        for (String rawParam : splitTopLevelPy(method.rawParams(), ',')) {
            String p = rawParam.strip();
            if (p.isEmpty() || p.startsWith("*") || p.contains("=")) {
                continue;
            }
            if (p.equals("self") || p.equals("cls")) {
                continue;
            }
            int colon = p.indexOf(':');
            String type = colon >= 0 ? p.substring(colon + 1).strip().replaceAll("\\s+", "") : null;
            Optional<String> sample = type == null
                    ? Optional.of(String.valueOf(nextInt++))
                    : pythonSampleArgFor(type);
            if (sample.isEmpty()) {
                return Optional.empty();
            }
            args.add(sample.get());
        }
        return Optional.of(String.join(", ", args));
    }

    /** Type-aware sample argument synthesis for the common LeetCode parameter
     * shapes. Recurses into {@code Optional[X]} (always synthesizes a real X,
     * never None - a None argument would make most solutions do nothing,
     * exactly the "no output" bug this whole feature exists to fix). */
    private Optional<String> pythonSampleArgFor(String type) {
        Matcher optional = OPTIONAL_TYPE.matcher(type);
        if (optional.matches()) {
            return pythonSampleArgFor(optional.group(1));
        }
        if (LIST_LIST_INT_TYPE.matcher(type).matches()) {
            return Optional.of("[[1, 2], [3, 4], [5, 6]]");
        }
        if (LIST_INT_TYPE.matcher(type).matches()) {
            return Optional.of("[4, 2, 7, 1, 9]");
        }
        if (LIST_STR_TYPE.matcher(type).matches()) {
            return Optional.of("[\"alpha\", \"beta\", \"gamma\"]");
        }
        if (LIST_FLOAT_TYPE.matcher(type).matches()) {
            return Optional.of("[4.0, 2.0, 7.0]");
        }
        if (LIST_OF_LISTNODE_TYPE.matcher(type).matches()) {
            return Optional.of("[" + buildLinkedListExpr(new int[]{1, 4, 5}) + ", "
                    + buildLinkedListExpr(new int[]{1, 3, 4}) + ", "
                    + buildLinkedListExpr(new int[]{2, 6}) + "]");
        }
        return switch (type) {
            case "int" -> Optional.of("7");
            case "float", "double" -> Optional.of("7.0");
            case "bool" -> Optional.of("True");
            case "str" -> Optional.of("\"abc\"");
            case "ListNode" -> Optional.of(buildLinkedListExpr(new int[]{1, 2, 3}));
            case "TreeNode" -> Optional.of(buildTreeExpr());
            default -> Optional.empty();
        };
    }

    /** Builds a nested-constructor linked list expression, e.g.
     * {@code ListNode(1, ListNode(2, ListNode(3, None)))} - no helper
     * function needed the way Java's buildList() is, since Python allows
     * arbitrarily nested expressions inline. */
    private String buildLinkedListExpr(int[] values) {
        String expr = "None";
        for (int i = values.length - 1; i >= 0; i--) {
            expr = "ListNode(" + values[i] + ", " + expr + ")";
        }
        return expr;
    }

    private String buildTreeExpr() {
        return "TreeNode(5, TreeNode(3, TreeNode(1), TreeNode(4)), TreeNode(8))";
    }

    /** Bracket-depth-aware comma split (Python type hints use square brackets
     * for generics, e.g. {@code List[int]}) - a plain split(",") would break
     * on {@code lists: List[Optional[ListNode]]}. */
    private List<String> splitTopLevelPy(String s, char delimiter) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(') {
                depth++;
            } else if (c == ']' || c == ')') {
                depth--;
            } else if (c == delimiter && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    // ------------------------------------------------------------------
    // Process launch + collection
    // ------------------------------------------------------------------

    private ProcessResult runTracerProcess(Path workDir) {
        List<String> command = "docker".equalsIgnoreCase(sandboxType)
                ? dockerCommand(workDir)
                : localCommand(workDir);

        long startedAt = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(command).directory(workDir.toFile()).start();
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to launch Python interpreter process", e);
        }

        try {
            process.getOutputStream().close(); // immediate EOF: input() -> EOFError, not a hang
        } catch (IOException ignored) {
            // stream already closed by a fast-exiting process
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread outPump = pump(process.getInputStream(), stdout, "py-stdout-pump");
        Thread errPump = pump(process.getErrorStream(), stderr, "py-stderr-pump");

        long hardTimeoutMs = limits.timeoutSeconds().toMillis() + HARD_KILL_GRACE_MS;
        boolean finished;
        try {
            finished = process.waitFor(hardTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ExecutionFailedException("Interrupted while waiting for Python execution", e);
        }
        if (!finished) {
            process.destroyForcibly();
        }
        joinQuietly(outPump);
        joinQuietly(errPump);

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new ProcessResult(
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                !finished,
                elapsedMs,
                startedAt);
    }

    private List<String> localCommand(Path workDir) {
        return List.of(resolveInterpreter(),
                workDir.resolve("tracer.py").toString(),
                workDir.resolve("main.py").toString(),
                workDir.resolve("limits.json").toString());
    }

    /** Same lockdown flags as {@link DockerSandboxRunner} minus the port
     * publish (nothing attaches to a Python run; stdout is the transport). */
    private List<String> dockerCommand(Path workDir) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("--network");
        command.add(dockerNetwork);
        command.add("--memory");
        command.add(dockerMemory);
        command.add("--memory-swap");
        command.add(dockerMemory);
        command.add("--cpus");
        command.add(dockerCpus);
        command.add("--pids-limit");
        command.add("128");
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,size=16m,mode=1777");
        command.add("-v");
        command.add(workDir.toAbsolutePath() + ":/work:ro");
        command.add("--name");
        command.add("codesense-pyexec-" + UUID.randomUUID());
        command.add("--entrypoint");
        command.add("python3");
        command.add(dockerImage);
        command.add("/work/tracer.py");
        command.add("/work/main.py");
        command.add("/work/limits.json");
        return command;
    }

    private String limitsJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxSteps", limits.maxSteps());
        m.put("timeoutSeconds", limits.timeoutSeconds().toSeconds());
        m.put("maxStackDepth", limits.maxStackDepth());
        m.put("maxObjectDepth", limits.maxObjectDepth());
        m.put("maxArrayElements", limits.maxArrayElements());
        m.put("maxObjectFields", limits.maxObjectFields());
        m.put("maxStringLength", limits.maxStringLength());
        m.put("maxConsoleOutputBytes", limits.maxConsoleOutputBytes());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize trace limits", e);
        }
    }

    private String resolveInterpreter() {
        String cached = resolvedInterpreter;
        if (cached != null) {
            return cached;
        }
        List<String> candidates = configuredExecutable.isBlank()
                ? INTERPRETER_CANDIDATES
                : List.of(configuredExecutable);
        for (String candidate : candidates) {
            if (probeInterpreter(candidate)) {
                log.info("Resolved Python interpreter: {}", candidate);
                resolvedInterpreter = candidate;
                return candidate;
            }
        }
        throw new ExecutionFailedException(
                "No Python interpreter found (tried " + String.join(", ", candidates)
                        + "). Install Python 3 or set EXECUTION_PYTHON_EXECUTABLE.");
    }

    private boolean probeInterpreter(String executable) {
        try {
            Process probe = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true).start();
            if (!probe.waitFor(5, TimeUnit.SECONDS)) {
                probe.destroyForcibly();
                return false;
            }
            return probe.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Thread pump(InputStream in, ByteArrayOutputStream sink, String name) {
        Thread t = new Thread(() -> {
            try (in) {
                in.transferTo(sink);
            } catch (IOException ignored) {
                // stream closed by process death - the collected bytes still count
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Same POSIX-permission widening as JavaSourceCompiler.compile(): in docker
     * mode the work dir is bind-mounted read-only into a container running as a
     * different OS user ("sandbox"), and the createTempDirectory default of 700
     * makes it unreadable there (found the hard way on the first Linux deploy -
     * presents as the container not seeing the files at all). No-op on Windows. */
    private static void widenForContainerUser(Path workDir) {
        try {
            if (workDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(workDir,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        } catch (IOException e) {
            log.warn("Could not widen work dir permissions for container user", e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup; temp dir reaping will catch stragglers
                }
            });
        } catch (IOException ignored) {
            // dir already gone
        }
    }

    private record WrapResult(String source, boolean wrapped) {
    }

    /** @param setup a statement to run before the call (e.g. instantiating
     *                the Solution class) - null when the call needs none
     *                (the bare module-function case). */
    private record GeneratedCall(String expression, String setup) {
        GeneratedCall(String expression) {
            this(expression, null);
        }
    }

    private record ProcessResult(String stdout, String stderr, boolean hardKilled,
                                 long elapsedMillis, long startedAtNanos) {
    }
}
