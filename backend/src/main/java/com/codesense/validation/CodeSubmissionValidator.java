package com.codesense.validation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-side twin of the frontend guard (frontend/src/lib/codeValidation.js).
 * Runs before any LLM call or code execution so a non-Java payload or a
 * natural-language instruction never reaches a provider or the sandbox — the
 * frontend blocks these too, but the backend is public-facing and can't trust
 * that the request came through our UI. Keep the two implementations in sync.
 */
@Component
public class CodeSubmissionValidator {

    public static final String LANGUAGE_ERROR =
            "This doesn't look like Java. Switch the language selector if you're writing Python, or paste Java code.";

    public static final String INSTRUCTION_ERROR =
            "Please paste actual Java code, not instructions. CodeSense analyzes code — it doesn't generate it.";

    public static final String PYTHON_INSTRUCTION_ERROR =
            "Please paste actual Python code, not instructions. CodeSense analyzes code — it doesn't generate it.";

    public static final String JAVA_IN_PYTHON_ERROR =
            "This looks like Java code. Switch the language selector to Java, or paste Python code.";

    private static final Pattern CODE_SYNTAX = Pattern.compile("[{}()\\[\\];]");
    private static final Pattern TYPE_DECL = Pattern.compile("\\b(class|interface|enum)\\s+\\w+");
    private static final Pattern ACCESS_KEYWORD = Pattern.compile("\\b(public|private|protected|static|void)\\b");

    private static final Pattern INSTRUCTION_VERBS = Pattern.compile(
            "^(create|write|implement|generate|make|build|code|develop|design|produce|give|show|provide|"
                    + "explain|solve|add|help|please|convert|reverse|sort|find|calculate|compute)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JAVA_TYPE_TOKENS = Pattern.compile(
            "\\b(public|private|protected|static|void|int|long|double|float|boolean|char|String)\\b");
    private static final Pattern BRACE_OR_SEMI = Pattern.compile("[{};]");

    private static final List<Pattern> NON_JAVA_SIGNALS = List.of(
            Pattern.compile("\\bdef\\s+\\w+\\s*\\("),                                   // Python function
            Pattern.compile("\\belif\\b"),                                              // Python
            Pattern.compile("\\bfrom\\s+[\\w.]+\\s+import\\b"),                          // Python from-import
            Pattern.compile("\\bimport\\s+(numpy|pandas|os|sys|re|math|random|collections|json|typing|itertools)\\b"),
            Pattern.compile("(^|\\n)[ \\t]*(if|elif|else|for|while|def|class|try|except|finally|with)\\b[^\\n{;]*:[ \\t]*$",
                    Pattern.MULTILINE),                                                 // Python colon block
            Pattern.compile("(?<![.\\w])print\\s*\\("),                                 // Python print()
            Pattern.compile("\\bconsole\\s*\\.\\s*(log|error|warn|info)\\s*\\("),        // JS console
            Pattern.compile("\\bfunction\\s+\\w*\\s*\\("),                              // JS function decl
            Pattern.compile("=>"),                                                      // JS arrow
            Pattern.compile("\\b(const|let)\\s+\\w+\\s*=")                               // JS declarations
    );

    /** Signals that clearly identify Java when the user selected Python. */
    private static final List<Pattern> JAVA_IN_PYTHON_SIGNALS = List.of(
            Pattern.compile("\\b(public|private|protected)\\s+(static\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\("),
            Pattern.compile("\\bSystem\\s*\\.\\s*out\\s*\\."),
            Pattern.compile("\\b(public|private)\\s+(class|interface|enum)\\s+\\w+"),
            Pattern.compile("\\bnew\\s+\\w+\\s*(<[^>]*>)?\\s*\\(")
    );

    /** Anything that reads as executable Python rather than prose. */
    private static final Pattern PYTHON_CODE_SIGNAL = Pattern.compile(
            "(^|\\n)[ \\t]*(def|class|import|from|for|while|if|return|print|with|try)\\b"
                    + "|[=()\\[\\]:]");

    /** Backward-compatible entry point - validates as Java. */
    public void validate(String rawCode) {
        validate(rawCode, "java");
    }

    /** Throws {@link InvalidSubmissionException} if the submission isn't
     * analyzable code in the selected language ("java" or "python"). */
    public void validate(String rawCode, String language) {
        if ("python".equalsIgnoreCase(language)) {
            validatePython(rawCode);
            return;
        }
        validateJava(rawCode);
    }

    private void validatePython(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            throw new InvalidSubmissionException(PYTHON_INSTRUCTION_ERROR);
        }
        // Prose guard first: "create a merge sort" has no code signal at all.
        if (!PYTHON_CODE_SIGNAL.matcher(code).find() && looksLikeInstruction(code)) {
            throw new InvalidSubmissionException(PYTHON_INSTRUCTION_ERROR);
        }
        // Java pasted while Python is selected would only produce a confusing
        // SyntaxError from the interpreter - name the actual problem instead.
        if (JAVA_IN_PYTHON_SIGNALS.stream().anyMatch(p -> p.matcher(code).find())
                && code.contains(";") && code.contains("{")) {
            throw new InvalidSubmissionException(JAVA_IN_PYTHON_ERROR);
        }
    }

    private void validateJava(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            throw new InvalidSubmissionException(INSTRUCTION_ERROR);
        }

        String masked = maskLiteralsAndComments(code);

        boolean hasCodeSyntax = CODE_SYNTAX.matcher(masked).find()
                || TYPE_DECL.matcher(masked).find()
                || ACCESS_KEYWORD.matcher(masked).find();

        // 1. Prompt-injection / prose: no real code, reads like an instruction.
        if (!hasCodeSyntax && looksLikeInstruction(code)) {
            throw new InvalidSubmissionException(INSTRUCTION_ERROR);
        }

        // 2. Wrong language: explicit Python/JS syntax, or nothing Java-shaped.
        if (usesNonJavaSyntax(masked) || !looksLikeJava(masked)) {
            throw new InvalidSubmissionException(LANGUAGE_ERROR);
        }
    }

    private boolean looksLikeInstruction(String code) {
        if (INSTRUCTION_VERBS.matcher(code).find()) {
            return true;
        }
        String[] words = code.trim().split("\\s+");
        if (words.length < 2) {
            return false;
        }
        long alphaWords = java.util.Arrays.stream(words)
                .filter(w -> w.matches("[A-Za-z][A-Za-z'-]*"))
                .count();
        return (double) alphaWords / words.length > 0.6;
    }

    private boolean usesNonJavaSyntax(String masked) {
        return NON_JAVA_SIGNALS.stream().anyMatch(p -> p.matcher(masked).find());
    }

    private boolean looksLikeJava(String masked) {
        return TYPE_DECL.matcher(masked).find()
                || (JAVA_TYPE_TOKENS.matcher(masked).find() && BRACE_OR_SEMI.matcher(masked).find());
    }

    // Length-preserving mask of string/char literals and comments, so a `=>` or
    // `print(` inside a Java string can't trip the language signals above.
    private String maskLiteralsAndComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        String state = "code"; // code | line | block | string | char
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            switch (state) {
                case "code" -> {
                    if (c == '/' && next == '/') { state = "line"; out.append("  "); i++; }
                    else if (c == '/' && next == '*') { state = "block"; out.append("  "); i++; }
                    else if (c == '"') { state = "string"; out.append(' '); }
                    else if (c == '\'') { state = "char"; out.append(' '); }
                    else out.append(c);
                }
                case "line" -> {
                    if (c == '\n') { state = "code"; out.append('\n'); } else out.append(' ');
                }
                case "block" -> {
                    if (c == '*' && next == '/') { state = "code"; out.append("  "); i++; }
                    else out.append(c == '\n' ? '\n' : ' ');
                }
                case "string" -> {
                    if (c == '\\') { out.append("  "); i++; }
                    else if (c == '"') { state = "code"; out.append(' '); }
                    else out.append(c == '\n' ? '\n' : ' ');
                }
                case "char" -> {
                    if (c == '\\') { out.append("  "); i++; }
                    else if (c == '\'') { state = "code"; out.append(' '); }
                    else out.append(' ');
                }
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
