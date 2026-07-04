package com.codesense.exec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

@Service
public class ExecutionService {

    private static final String MAIN_CLASS_NAME = "Main";

    private final JavaSourceCompiler compiler;
    private final DeterministicWrapperGenerator deterministicWrapperGenerator;
    private final SolutionWrapperGenerator wrapperGenerator;
    private final SandboxRunner sandboxRunner;
    private final JavaTracer tracer;
    private final Duration readinessTimeout;

    public ExecutionService(JavaSourceCompiler compiler,
                             DeterministicWrapperGenerator deterministicWrapperGenerator,
                             SolutionWrapperGenerator wrapperGenerator,
                             SandboxRunner sandboxRunner,
                             JavaTracer tracer,
                             @Value("${execution.sandbox.readiness-timeout-seconds}") long readinessTimeoutSeconds) {
        this.compiler = compiler;
        this.deterministicWrapperGenerator = deterministicWrapperGenerator;
        this.wrapperGenerator = wrapperGenerator;
        this.sandboxRunner = sandboxRunner;
        this.tracer = tracer;
        this.readinessTimeout = Duration.ofSeconds(readinessTimeoutSeconds);
    }

    public ExecutionResponse execute(String sourceCode) {
        boolean needsWrapping = !compiler.hasMainClass(sourceCode);
        String executedSourceCode = sourceCode;
        if (needsWrapping) {
            executedSourceCode = deterministicWrapperGenerator.tryWrap(sourceCode)
                    .orElseGet(() -> wrapperGenerator.wrap(sourceCode));
        }

        Path classDir = compiler.compile(executedSourceCode);

        ExecutionTrace trace;
        try (SandboxHandle handle = sandboxRunner.start(classDir, MAIN_CLASS_NAME, readinessTimeout)) {
            trace = tracer.trace(handle, MAIN_CLASS_NAME);
        }

        return new ExecutionResponse(trace, executedSourceCode, needsWrapping, Instant.now());
    }
}
