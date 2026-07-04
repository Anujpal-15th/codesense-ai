package com.codesense.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared lightweight structural scanning primitives for Java source text -
 * masking comments/string/char literals so brace-depth tracking and keyword
 * matching aren't confused by braces/parens/keywords appearing inside
 * literals, and finding top-level class boundaries. Used by both {@link
 * DeterministicWrapperGenerator} (deciding how to synthesize a driver) and
 * {@link EntryPointDetector} (deciding whether one is needed at all) so
 * there is exactly one implementation of "what does this source's class
 * structure look like" - previously each had its own copy, and only one of
 * them masked out comments/strings, which let a comment merely mentioning
 * "class Main" fool entry-point detection (confirmed live before this fix).
 */
final class JavaSourceScanner {

    private static final Set<String> MODIFIER_WORDS =
            Set.of("public", "private", "protected", "static", "final", "abstract", "strictfp");

    private JavaSourceScanner() {
    }

    record TopLevelClass(String name, boolean isPublic, int braceOpen, int braceClose) {
    }

    static List<TopLevelClass> findTopLevelClasses(String masked) {
        List<TopLevelClass> result = new ArrayList<>();
        int depth = 0;
        int n = masked.length();
        int i = 0;
        while (i < n) {
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
            if (depth == 0 && matchesKeywordAt(masked, i, "class")) {
                Matcher m = Pattern.compile("class\\s+(\\w+)").matcher(masked);
                m.region(i, n);
                if (m.lookingAt()) {
                    String className = m.group(1);
                    int modifiersStart = findModifiersStart(masked, i);
                    boolean isPublic = Pattern.compile("\\bpublic\\b")
                            .matcher(masked.substring(modifiersStart, i)).find();
                    int braceOpen = masked.indexOf('{', m.end());
                    if (braceOpen < 0) {
                        break;
                    }
                    int braceClose = findMatchingBrace(masked, braceOpen);
                    result.add(new TopLevelClass(className, isPublic, braceOpen, braceClose));
                    i = braceClose + 1;
                    continue;
                }
            }
            i++;
        }
        return result;
    }

    static boolean matchesKeywordAt(String s, int i, String keyword) {
        if (!s.startsWith(keyword, i)) {
            return false;
        }
        if (i > 0 && Character.isJavaIdentifierPart(s.charAt(i - 1))) {
            return false;
        }
        int end = i + keyword.length();
        return end >= s.length() || !Character.isJavaIdentifierPart(s.charAt(end));
    }

    static int findModifiersStart(String masked, int keywordIndex) {
        int i = keywordIndex;
        while (i > 0) {
            int back = i;
            while (back > 0 && Character.isWhitespace(masked.charAt(back - 1))) back--;
            int wordEnd = back;
            while (back > 0 && Character.isJavaIdentifierPart(masked.charAt(back - 1))) back--;
            if (back == wordEnd) {
                break;
            }
            String word = masked.substring(back, wordEnd);
            if (MODIFIER_WORDS.contains(word)) {
                i = back;
            } else {
                break;
            }
        }
        return i;
    }

    static int findMatchingBrace(String masked, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return masked.length() - 1;
    }

    static String maskNonCode(String src) {
        StringBuilder masked = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    masked.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                masked.append("  ");
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    masked.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    masked.append("  ");
                    i += 2;
                }
            } else if (c == '"') {
                masked.append(' ');
                i++;
                while (i < n && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        masked.append("  ");
                        i += 2;
                        continue;
                    }
                    masked.append(' ');
                    i++;
                }
                if (i < n) {
                    masked.append(' ');
                    i++;
                }
            } else if (c == '\'') {
                masked.append(' ');
                i++;
                while (i < n && src.charAt(i) != '\'') {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        masked.append("  ");
                        i += 2;
                        continue;
                    }
                    masked.append(' ');
                    i++;
                }
                if (i < n) {
                    masked.append(' ');
                    i++;
                }
            } else {
                masked.append(c == '\n' ? '\n' : c);
                i++;
            }
        }
        return masked.toString();
    }
}
