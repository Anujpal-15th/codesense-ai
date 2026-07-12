package com.codesense.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Python counterpart of {@link JavaTracer}. Where the Java tracer drives a live
 * JDI session, Python tracing is delegated to {@code resources/python/tracer.py}
 * (a {@code sys.settrace} harness run alongside the user program) which emits a
 * completed trace as a single JSON document on its real stdout - the user
 * program's own prints never reach real stdout, the harness captures them into
 * the JSON's consoleOutput/consoleOutputDelta fields. That JSON is shaped
 * exactly like {@link ExecutionTrace} (including {@link TraceValue}'s
 * {@code valueKind} discriminator), so parsing is a plain Jackson bind - the
 * frontend renders both languages' traces identically with no translation
 * layer.
 */
@Service
class PythonTracer {

    private static final String TRACER_RESOURCE = "python/tracer.py";

    private final ObjectMapper objectMapper;
    private final String tracerScript;

    PythonTracer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tracerScript = loadTracerScript();
    }

    /** The tracer harness source, written into each execution's work dir. */
    String tracerScript() {
        return tracerScript;
    }

    /**
     * Parses the harness's stdout into an {@link ExecutionTrace}.
     *
     * @throws ExecutionFailedException when stdout is not the expected JSON
     *                                  (harness crash, interpreter startup failure)
     */
    ExecutionTrace parse(String stdoutJson, String stderrDiagnostics) {
        String body = stdoutJson == null ? "" : stdoutJson.trim();
        if (body.isEmpty() || !body.startsWith("{")) {
            throw new ExecutionFailedException(
                    "Python tracer produced no trace." + diagnosticSuffix(stderrDiagnostics));
        }
        try {
            return objectMapper.readValue(body, ExecutionTrace.class);
        } catch (IOException e) {
            throw new ExecutionFailedException(
                    "Python tracer output was not parseable." + diagnosticSuffix(stderrDiagnostics), e);
        }
    }

    private static String diagnosticSuffix(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return " (no stderr diagnostics captured)";
        }
        String trimmed = stderr.strip();
        return " stderr: " + (trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed);
    }

    private static String loadTracerScript() {
        try (InputStream in = new ClassPathResource(TRACER_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing bundled resource " + TRACER_RESOURCE
                    + " - the Python execution feature cannot work without it", e);
        }
    }
}
