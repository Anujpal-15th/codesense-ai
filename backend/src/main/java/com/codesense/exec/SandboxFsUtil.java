package com.codesense.exec;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Work-dir prep/cleanup shared by both execution paths ({@link JavaSourceCompiler}
 * for Java, {@link PythonExecutionHandler} for Python) - each bind-mounts a
 * host temp dir read-only into the Docker sandbox container, which runs as a
 * different, non-root OS user, and each needs the same widen-then-eventually-
 * delete lifecycle around that dir. Was implemented independently (and
 * slightly inconsistently - Python's original delete was a single silent
 * best-effort pass, Java's retried 3x on Windows file-lock contention) in
 * each class before this extraction.
 */
@Slf4j
final class SandboxFsUtil {

    private SandboxFsUtil() {
    }

    /**
     * Widens a freshly created temp dir (default 700 on POSIX) to 755 so the
     * non-root sandbox user inside the Docker container can read (but not
     * write) it. Without this, JDI attach / the Python interpreter fails to
     * see the files even though they exist on the host filesystem - a
     * different-OS-user permission gap found on the first Linux deploy.
     * No-op on Windows (NTFS has no "posix" FileAttributeView), where
     * local-process mode runs compiler/interpreter and debuggee as the same
     * OS user anyway. Throws (rather than logging and continuing) on a real
     * POSIX failure: proceeding with wrong permissions doesn't fail here, it
     * fails later as a much more confusing "file not found" inside the
     * container, or worse, silently succeeds against a stale cached image
     * that happened to already have the right permissions.
     */
    static void widenForContainerUser(Path dir) {
        if (!dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to set sandbox work dir permissions", e);
        }
    }

    /**
     * Best-effort recursive delete, retrying briefly: the debuggee process is
     * killed via a fire-and-forget destroyForcibly(), so on Windows it can
     * still hold locks on its files for a moment after the sandbox handle
     * closes. A dir that still can't be deleted after the retries is logged
     * and abandoned - never worth failing the request over.
     */
    static void deleteTreeWithRetry(Path dir) {
        if (dir == null) {
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
                if (deleteTree(dir)) {
                    return;
                }
            } catch (IOException | UncheckedIOException e) {
                // locked file mid-walk; retry
            }
        }
        log.warn("Could not delete sandbox work dir after retries (leaked): {}", dir);
    }

    private static boolean deleteTree(Path dir) throws IOException {
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
}
