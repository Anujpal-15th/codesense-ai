package com.codesense.exec;

import com.codesense.validation.CodeSubmissionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ExecutionService {

    /** The name given to a synthesized driver class - only ever used for code
     * we generate ourselves (wrapping), never assumed for the user's own code. */
    private static final String GENERATED_MAIN_CLASS_NAME = "Main";

    private final JavaSourceCompiler compiler;
    private final EntryPointDetector entryPointDetector;
    private final DeterministicWrapperGenerator deterministicWrapperGenerator;
    private final SolutionWrapperGenerator wrapperGenerator;
    private final SandboxRunner sandboxRunner;
    private final JavaTracer tracer;
    private final PythonExecutionHandler pythonExecutionHandler;
    private final CodeSubmissionValidator submissionValidator;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final ObjectMapper objectMapper;
    private final Duration readinessTimeout;

    public ExecutionService(JavaSourceCompiler compiler,
                             EntryPointDetector entryPointDetector,
                             DeterministicWrapperGenerator deterministicWrapperGenerator,
                             SolutionWrapperGenerator wrapperGenerator,
                             SandboxRunner sandboxRunner,
                             JavaTracer tracer,
                             PythonExecutionHandler pythonExecutionHandler,
                             CodeSubmissionValidator submissionValidator,
                             ExecutionHistoryRepository executionHistoryRepository,
                             ObjectMapper objectMapper,
                             @Value("${execution.sandbox.readiness-timeout-seconds}") long readinessTimeoutSeconds) {
        this.compiler = compiler;
        this.entryPointDetector = entryPointDetector;
        this.deterministicWrapperGenerator = deterministicWrapperGenerator;
        this.wrapperGenerator = wrapperGenerator;
        this.sandboxRunner = sandboxRunner;
        this.tracer = tracer;
        this.pythonExecutionHandler = pythonExecutionHandler;
        this.submissionValidator = submissionValidator;
        this.executionHistoryRepository = executionHistoryRepository;
        this.objectMapper = objectMapper;
        this.readinessTimeout = Duration.ofSeconds(readinessTimeoutSeconds);
    }

    /**
     * @param language "java" or "python"; null/blank defaults to "java" so
     *                 pre-language clients (and the regression suite's plain
     *                 {@code {sourceCode}} payloads) keep working unchanged.
     * @param userId   opaque client-generated id from the X-User-Id header, or
     *                 null if absent. Only persisted to history when present -
     *                 see {@link #persistIfOwned}, mirroring AnalysisService's
     *                 same no-login, userId-scoped pattern.
     */
    public ExecutionResponse execute(String sourceCode, String language, String userId) {
        String lang = language == null || language.isBlank()
                ? "java"
                : language.trim().toLowerCase(java.util.Locale.ROOT);

        // Reject wrong-language code and instruction-only payloads before we
        // spawn anything. Throws InvalidSubmissionException -> 400.
        submissionValidator.validate(sourceCode, lang);
        log.debug("Execution request: language={} userId={}", lang, userId);

        ExecutionResponse response = "python".equals(lang)
                ? pythonExecutionHandler.execute(sourceCode)
                : executeJava(sourceCode);
        persistIfOwned(response, sourceCode, lang, userId);
        return response;
    }

    /**
     * Best-effort: a history-save failure must never take down a response the
     * user's execution already earned on its own merits, so any exception here
     * (serialization, DB hiccup) is logged and swallowed, not rethrown - exactly
     * the same "the real work already succeeded" reasoning as
     * {@link com.codesense.exec.PythonExecutionHandler} not letting a
     * best-effort cleanup step fail the request. Skipped entirely for anonymous
     * callers (no X-User-Id) since there's no history list a no-login row could
     * ever appear in.
     */
    private void persistIfOwned(ExecutionResponse response, String sourceCode, String language, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            ExecutionHistory history = new ExecutionHistory();
            history.setUserId(userId);
            history.setLanguage(language);
            history.setSourceCode(sourceCode);
            history.setExecutedSourceCode(response.executedSourceCode());
            history.setWasWrapped(response.wasWrapped());
            history.setOutcome(response.trace().outcome().name());
            history.setConsoleOutput(response.trace().consoleOutput());
            history.setTotalStepsCaptured(response.trace().totalStepsCaptured());
            history.setTraceJson(objectMapper.writeValueAsString(response.trace()));
            executionHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to persist execution history (execution itself already succeeded)", e);
        }
    }

    /** @param userId no header sent -> empty list, not an error - see
     *                {@code AnalysisService#getHistorySummaries}. */
    public List<ExecutionHistorySummaryResponse> getHistorySummaries(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return executionHistoryRepository.findSummariesByUserId(userId);
    }

    /** @param userId must match the row's owner or this 404s exactly like a
     *                nonexistent id - see {@code AnalysisService#getById}. */
    public ExecutionHistoryDetailResponse getById(Long id, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ExecutionNotFoundException(id);
        }
        ExecutionHistory history = executionHistoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ExecutionNotFoundException(id));
        ExecutionTrace trace;
        try {
            trace = objectMapper.readValue(history.getTraceJson(), ExecutionTrace.class);
        } catch (Exception e) {
            throw new ExecutionFailedException("Stored execution trace is corrupted", e);
        }
        return ExecutionHistoryDetailResponse.from(history, trace);
    }

    private ExecutionResponse executeJava(String sourceCode) {
        long tStart = System.nanoTime();
        Optional<String> existingEntryPoint = entryPointDetector.findEntryPointClass(sourceCode);

        String mainClassName;
        String executedSourceCode;
        boolean needsWrapping;
        Path classDir = null;
        long compileNanos = 0;

        if (existingEntryPoint.isPresent()) {
            mainClassName = existingEntryPoint.get();
            executedSourceCode = sourceCode;
            needsWrapping = false;
        } else {
            mainClassName = GENERATED_MAIN_CLASS_NAME;
            needsWrapping = true;
            // The deterministic wrapper synthesizes arguments without proving
            // the result compiles (e.g. a bare snippet using Map/List/Set with
            // no import) - so compile it eagerly and treat a compile failure
            // as "deterministic wrap doesn't apply", falling back to the LLM
            // wrapper instead of surfacing the error to the user. A compile
            // failure on the LLM path (or the unwrapped path below) still
            // surfaces normally - by then it's the user's own code at fault.
            Optional<String> deterministic = deterministicWrapperGenerator.tryWrap(sourceCode);
            if (deterministic.isPresent()) {
                try {
                    long c0 = System.nanoTime();
                    classDir = compiler.compile(deterministic.get(), mainClassName);
                    compileNanos = System.nanoTime() - c0;
                } catch (CompilationFailedException e) {
                    log.info("Deterministic wrapper output failed to compile, falling back to LLM wrapper: {}",
                            firstLine(e.getMessage()));
                }
            }
            executedSourceCode = classDir != null ? deterministic.get() : wrapperGenerator.wrap(sourceCode);
        }

        if (classDir == null) {
            long c0 = System.nanoTime();
            classDir = compiler.compile(executedSourceCode, mainClassName);
            compileNanos = System.nanoTime() - c0;
        }
        long tCompiled = System.nanoTime();
        long tWrapped = tCompiled - compileNanos;

        ExecutionTrace trace;
        long tSandboxUp;
        try {
            SandboxHandle handle = sandboxRunner.start(classDir, mainClassName, readinessTimeout);
            tSandboxUp = System.nanoTime();
            try (handle) {
                trace = tracer.trace(handle, mainClassName);
            }
        } finally {
            // All outcomes - NORMAL, EXCEPTION, TIMED_OUT, and infra failures
            // (sandbox launch / JDI attach) - are done with the compiled
            // classes once we get here; without this, every request leaked a
            // codesense-exec-* temp directory.
            compiler.cleanupWorkDir(classDir);
        }
        long tTraced = System.nanoTime();

        log.info("Execution timing: detect+wrap={}ms compile={}ms sandboxStart={}ms trace={}ms total={}ms (steps={}, wrapped={})",
                (tWrapped - tStart) / 1_000_000,
                (tCompiled - tWrapped) / 1_000_000,
                (tSandboxUp - tCompiled) / 1_000_000,
                (tTraced - tSandboxUp) / 1_000_000,
                (tTraced - tStart) / 1_000_000,
                trace.totalStepsCaptured(),
                needsWrapping);

        return new ExecutionResponse(trace, executedSourceCode, needsWrapping, Instant.now());
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }
}
