package com.codesense.exec;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether submitted source already has a directly-runnable entry
 * point - a top-level class with a real {@code public static void
 * main(String[] args)} method - and if so, which class. Replaces {@code
 * JavaSourceCompiler}'s old name-based check ({@code class Main} appearing
 * anywhere in the raw text), which was confirmed to both under- and
 * over-trigger:
 * <ul>
 *   <li>a valid {@code main} in a class not literally named {@code Main}
 *       was invisible to it, causing unnecessary (and in one case actively
 *       wrong) wrapping;</li>
 *   <li>the literal text {@code class Main} appearing in a comment or a
 *       differently-named nested class fooled it into skipping wrapping
 *       entirely, producing a cryptic JVM launch failure instead of running
 *       the user's real program.</li>
 * </ul>
 *
 * <p>Uses {@link JavaSourceScanner} for masking and top-level class
 * boundaries, so comments/string/char literals can never be mistaken for
 * real code here.
 */
@Service
class EntryPointDetector {

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "((?:(?:public|private|protected|static|final|synchronized|abstract|native|strictfp)\\s+)*)"
                    + "(?:<[^>]*>\\s+)?"
                    + "([\\w\\[\\]<>,.]+?)\\s+"
                    + "(\\w+)\\s*"
                    + "\\(([^)]*)\\)"
                    + "\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*");

    private static final Pattern MAIN_PARAMS_PATTERN =
            Pattern.compile("^(?:final\\s+)?String\\s*\\[\\s*\\]\\s*\\w+$");

    /**
     * @return the name of the class already holding a valid entry point, if
     * exactly one (unambiguous) such class exists; empty if none do, meaning
     * the caller should wrap the submission instead of compiling it as-is.
     */
    Optional<String> findEntryPointClass(String sourceCode) {
        String masked = JavaSourceScanner.maskNonCode(sourceCode);
        List<JavaSourceScanner.TopLevelClass> classes = JavaSourceScanner.findTopLevelClasses(masked);

        List<JavaSourceScanner.TopLevelClass> candidates = new ArrayList<>();
        for (JavaSourceScanner.TopLevelClass tc : classes) {
            if (hasValidMainMethod(masked, tc)) {
                candidates.add(tc);
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0).name());
        }

        // More than one class each has its own valid main() - rare (e.g. two
        // full solutions pasted at once). Java permits at most one public
        // top-level class per file, so preferring the public candidate
        // resolves to at most one match; if none of the candidates is public,
        // fall back to the first declared, for predictable (if not "smart")
        // behavior rather than an arbitrary pick.
        return candidates.stream()
                .filter(JavaSourceScanner.TopLevelClass::isPublic)
                .findFirst()
                .or(() -> candidates.stream().findFirst())
                .map(JavaSourceScanner.TopLevelClass::name);
    }

    private boolean hasValidMainMethod(String masked, JavaSourceScanner.TopLevelClass tc) {
        int depth = 0;
        int i = tc.braceOpen() + 1;
        int bodyEnd = tc.braceClose();
        while (i < bodyEnd) {
            char c = masked.charAt(i);
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                depth--;
                i++;
                continue;
            }
            if (depth == 0) {
                if (JavaSourceScanner.matchesKeywordAt(masked, i, "class")
                        || JavaSourceScanner.matchesKeywordAt(masked, i, "interface")
                        || JavaSourceScanner.matchesKeywordAt(masked, i, "enum")) {
                    int braceOpen = masked.indexOf('{', i);
                    if (braceOpen >= 0 && braceOpen < bodyEnd) {
                        i = JavaSourceScanner.findMatchingBrace(masked, braceOpen) + 1;
                        continue;
                    }
                }
                Matcher m = METHOD_PATTERN.matcher(masked);
                m.region(i, bodyEnd);
                if (m.lookingAt()) {
                    String modifiers = m.group(1) == null ? "" : m.group(1);
                    String returnType = m.group(2).trim().replaceAll("\\s+", "");
                    String methodName = m.group(3);
                    String rawParams = m.group(4).trim();
                    int sigEnd = m.end();
                    int braceOpen = masked.indexOf('{', sigEnd);
                    int semi = masked.indexOf(';', sigEnd);
                    if (braceOpen < 0 || (semi >= 0 && semi < braceOpen)) {
                        i = semi >= 0 ? semi + 1 : sigEnd;
                        continue;
                    }
                    int braceClose = JavaSourceScanner.findMatchingBrace(masked, braceOpen);
                    if (isMainSignature(modifiers, returnType, methodName, rawParams)) {
                        return true;
                    }
                    i = braceClose + 1;
                    continue;
                }
            }
            i++;
        }
        return false;
    }

    private boolean isMainSignature(String modifiers, String returnType, String methodName, String rawParams) {
        boolean isPublic = Pattern.compile("\\bpublic\\b").matcher(modifiers).find();
        boolean isStatic = Pattern.compile("\\bstatic\\b").matcher(modifiers).find();
        return isPublic
                && isStatic
                && methodName.equals("main")
                && returnType.equals("void")
                && MAIN_PARAMS_PATTERN.matcher(rawParams).matches();
    }
}
