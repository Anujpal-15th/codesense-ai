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
            "Only Java code is currently supported. Python, JavaScript, and other languages are not yet available.";

    public static final String INSTRUCTION_ERROR =
            "Please paste actual Java code, not instructions. CodeSense analyzes code — it doesn't generate it.";

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

    /** Throws {@link InvalidSubmissionException} if the submission isn't analyzable Java. */
    public void validate(String rawCode) {
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
