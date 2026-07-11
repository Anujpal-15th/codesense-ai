package com.codesense.exec;

import com.codesense.validation.CodeSubmissionValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
    private final CodeSubmissionValidator submissionValidator;
    private final Duration readinessTimeout;

    public ExecutionService(JavaSourceCompiler compiler,
                             EntryPointDetector entryPointDetector,
                             DeterministicWrapperGenerator deterministicWrapperGenerator,
                             SolutionWrapperGenerator wrapperGenerator,
                             SandboxRunner sandboxRunner,
                             JavaTracer tracer,
                             CodeSubmissionValidator submissionValidator,
                             @Value("${execution.sandbox.readiness-timeout-seconds}") long readinessTimeoutSeconds) {
        this.compiler = compiler;
        this.entryPointDetector = entryPointDetector;
        this.deterministicWrapperGenerator = deterministicWrapperGenerator;
        this.wrapperGenerator = wrapperGenerator;
        this.sandboxRunner = sandboxRunner;
        this.tracer = tracer;
        this.submissionValidator = submissionValidator;
        this.readinessTimeout = Duration.ofSeconds(readinessTimeoutSeconds);
    }

    public ExecutionResponse execute(String sourceCode) {
        // Reject non-Java code and instruction-only payloads before we compile
        // and spawn a JVM. Throws InvalidSubmissionException -> 400.
        submissionValidator.validate(sourceCode);

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
