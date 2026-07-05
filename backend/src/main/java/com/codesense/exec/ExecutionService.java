package com.codesense.exec;

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
    private final Duration readinessTimeout;

    public ExecutionService(JavaSourceCompiler compiler,
                             EntryPointDetector entryPointDetector,
                             DeterministicWrapperGenerator deterministicWrapperGenerator,
                             SolutionWrapperGenerator wrapperGenerator,
                             SandboxRunner sandboxRunner,
                             JavaTracer tracer,
                             @Value("${execution.sandbox.readiness-timeout-seconds}") long readinessTimeoutSeconds) {
        this.compiler = compiler;
        this.entryPointDetector = entryPointDetector;
        this.deterministicWrapperGenerator = deterministicWrapperGenerator;
        this.wrapperGenerator = wrapperGenerator;
        this.sandboxRunner = sandboxRunner;
        this.tracer = tracer;
        this.readinessTimeout = Duration.ofSeconds(readinessTimeoutSeconds);
    }

    public ExecutionResponse execute(String sourceCode) {
        long tStart = System.nanoTime();
        Optional<String> existingEntryPoint = entryPointDetector.findEntryPointClass(sourceCode);

        String mainClassName;
        String executedSourceCode;
        boolean needsWrapping;

        if (existingEntryPoint.isPresent()) {
            mainClassName = existingEntryPoint.get();
            executedSourceCode = sourceCode;
            needsWrapping = false;
        } else {
            mainClassName = GENERATED_MAIN_CLASS_NAME;
            executedSourceCode = deterministicWrapperGenerator.tryWrap(sourceCode)
                    .orElseGet(() -> wrapperGenerator.wrap(sourceCode));
            needsWrapping = true;
        }
        long tWrapped = System.nanoTime();

        Path classDir = compiler.compile(executedSourceCode, mainClassName);
        long tCompiled = System.nanoTime();

        ExecutionTrace trace;
        long tSandboxUp;
        SandboxHandle handle = sandboxRunner.start(classDir, mainClassName, readinessTimeout);
        tSandboxUp = System.nanoTime();
        try (handle) {
            trace = tracer.trace(handle, mainClassName);
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
}
