package com.codesense.exec;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles a single submitted Java source file in-memory/on-disk (a temp dir),
 * with debug info enabled (required for JDI variable-name resolution) and
 * annotation processing disabled (compiling untrusted source shouldn't run
 * arbitrary annotation-processor code).
 *
 * <p>Named {@code JavaSourceCompiler} rather than {@code JavaCompiler} to avoid
 * colliding with {@link javax.tools.JavaCompiler}, which this class wraps.
 *
 * <p>{@code mainClassName} is supplied by the caller ({@link ExecutionService},
 * via {@link EntryPointDetector} or the wrapper generators) rather than
 * assumed to always be {@code "Main"} - the class actually being compiled
 * might be the user's own already-runnable class under its real name.
 *
 * <p><b>The {@link StandardJavaFileManager} is created once and reused across
 * compilations</b> - profiling showed the compile stage dominating every
 * execution request (~360-405ms of a ~550ms total for typical snippets), and
 * most of that cost is a fresh file manager re-reading the JDK platform-class
 * image on every call; a reused manager caches it. The file manager is NOT
 * thread-safe, so {@link #compile} is synchronized - execution requests
 * serialize through compilation, which is acceptable for this single-user
 * dev tool (each compile is fast; see profiling numbers in
 * implementation-notes.md).
 */
@Slf4j
@Service
class JavaSourceCompiler {

    private final JavaCompiler compiler;
    private final StandardJavaFileManager sharedFileManager;

    JavaSourceCompiler() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        this.sharedFileManager = compiler == null
                ? null
                : compiler.getStandardFileManager(null, Locale.getDefault(), StandardCharsets.UTF_8);
    }

    synchronized Path compile(String sourceCode, String mainClassName) {
        if (compiler == null) {
            throw new ExecutionFailedException(
                    "No system Java compiler available (is a JDK, not just a JRE, on the classpath?)");
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("codesense-exec-");
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to create temp compile directory", e);
        }

        // Any failure from here on leaves a half-populated work dir behind -
        // clean it up before rethrowing so failed compiles (including the
        // deterministic-wrapper fallback probe in ExecutionService) don't
        // leak a directory per request. Successful compiles hand ownership
        // of the dir to the caller, who cleans it up after the trace.
        try {
            // Files.createTempDirectory defaults to mode 700 on POSIX
            // filesystems. The docker sandbox mode bind-mounts this dir
            // read-only into a container that runs as a different, non-root
            // user (DockerSandboxRunner -v .../:/work:ro, Dockerfile's USER
            // sandbox) - a 700 dir owned by the host process user is
            // unreadable to that container user, which surfaces as a
            // ClassNotFoundException at trace time, not a compile error.
            // Widen to 755 so the sandbox user can read (but not write) it.
            // Guarded on POSIX support so this stays a no-op on Windows dev,
            // where NTFS doesn't have a "posix" FileAttributeView and
            // local-process mode (same OS user runs both the compiler and
            // the debuggee) doesn't need this.
            if (workDir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                try {
                    // Widen from 700 to 755 so the non-root 'sandbox' user inside
                    // the Docker container (DockerSandboxRunner) can traverse and
                    // read the compiled .class files. Without this, JDI attach fails
                    // with ClassNotFoundException even though the files exist on the
                    // host filesystem - a different-OS-user permission gap.
                    Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwxr-xr-x"));
                } catch (IOException e) {
                    throw new ExecutionFailedException("Failed to set compile work dir permissions", e);
                }
            }

            Path sourceFile = workDir.resolve(mainClassName + ".java");
            try {
                Files.writeString(sourceFile, sourceCode);
            } catch (IOException e) {
                throw new ExecutionFailedException("Failed to write submitted source to disk", e);
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            Iterable<? extends JavaFileObject> compilationUnits =
                    sharedFileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));

            // -d is per-task: getTask() routes it through the (shared) file
            // manager's handleOption, which is safe here because compile() is
            // synchronized - no two tasks can interleave output locations.
            List<String> options = List.of("-g", "-proc:none", "-d", workDir.toString());

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, sharedFileManager, diagnostics, options, null, compilationUnits);

            if (!task.call()) {
                throw new CompilationFailedException(formatDiagnostics(diagnostics));
            }

            return workDir;
        } catch (RuntimeException e) {
            cleanupWorkDir(workDir);
            throw e;
        }
    }

    /**
     * Best-effort recursive delete of a compile work dir. Retries briefly:
     * the debuggee JVM is killed via a fire-and-forget destroyForcibly(), so
     * on Windows it can still hold locks on the class files for a moment
     * after {@code SandboxHandle.close()} returns. A dir that still can't be
     * deleted after the retries is logged and abandoned - never worth
     * failing the request over.
     */
    void cleanupWorkDir(Path workDir) {
        if (workDir == null) {
            return;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                if (deleteTree(workDir)) {
                    return;
                }
            } catch (IOException | UncheckedIOException e) {
                // locked file mid-walk; retry
            }
        }
        log.warn("Could not delete compile work dir after retries (leaked): {}", workDir);
    }

    private boolean deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return true;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return !Files.exists(dir);
    }

    @PreDestroy
    void closeFileManager() {
        if (sharedFileManager != null) {
            try {
                sharedFileManager.close();
            } catch (IOException ignored) {
                // shutting down anyway
            }
        }
    }

    private String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(Locale.getDefault()))
                .collect(Collectors.joining("\n"));
    }
}
