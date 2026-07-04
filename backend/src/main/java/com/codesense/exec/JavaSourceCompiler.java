package com.codesense.exec;

import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compiles a single submitted Java source file in-memory/on-disk (a temp dir),
 * with debug info enabled (required for JDI variable-name resolution) and
 * annotation processing disabled (compiling untrusted source shouldn't run
 * arbitrary annotation-processor code).
 *
 * <p>Named {@code JavaSourceCompiler} rather than {@code JavaCompiler} to avoid
 * colliding with {@link javax.tools.JavaCompiler}, which this class wraps.
 */
@Service
class JavaSourceCompiler {

    private static final String MAIN_CLASS_NAME = "Main";
    private static final Pattern MAIN_CLASS_PATTERN = Pattern.compile("\\bclass\\s+Main\\b");

    boolean hasMainClass(String sourceCode) {
        return MAIN_CLASS_PATTERN.matcher(sourceCode).find();
    }

    Path compile(String sourceCode) {
        if (!MAIN_CLASS_PATTERN.matcher(sourceCode).find()) {
            throw new CompilationFailedException(
                    "Submitted code must define `public class Main` with a `public static void main(String[] args)` method");
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("codesense-exec-");
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to create temp compile directory", e);
        }

        Path sourceFile = workDir.resolve(MAIN_CLASS_NAME + ".java");
        try {
            Files.writeString(sourceFile, sourceCode);
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to write submitted source to disk", e);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ExecutionFailedException(
                    "No system Java compiler available (is a JDK, not just a JRE, on the classpath?)");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));

            List<String> options = List.of("-g", "-proc:none", "-d", workDir.toString());

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits);

            if (!task.call()) {
                throw new CompilationFailedException(formatDiagnostics(diagnostics));
            }
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to close compiler file manager", e);
        }

        return workDir;
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(Locale.getDefault()))
                .collect(Collectors.joining("\n"));
    }
}
