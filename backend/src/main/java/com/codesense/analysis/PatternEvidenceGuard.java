package com.codesense.analysis;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheap, deterministic post-LLM sanity check on the pattern label: if the
 * model names a well-known pattern but the code plainly lacks that pattern's
 * most basic structural prerequisite (e.g. "Sliding Window" on code with no
 * loop at all - a real failure observed live: an empty main() and a plain
 * recursive factorial were both labeled "Sliding Window" by a small model),
 * the label should not be asserted confidently.
 *
 * <p>Deliberately conservative in both directions:
 * <ul>
 *   <li>Only patterns with an unambiguous, cheap-to-check prerequisite are
 *       guarded; any label outside the known set (including the Fix #3
 *       escape hatches) passes through untouched.</li>
 *   <li>Checks run on the raw snippet, unmasked - a comment mentioning
 *       "while" can only ADD evidence (preventing a trip), never cause a
 *       false trip, so the guard can never wrongly overrule a correct
 *       label because of a comment.</li>
 * </ul>
 *
 * <p>Chosen over the re-prompt alternative because a second LLM round trip
 * costs 4-7s whenever triggered; this is pure regex, microseconds, and the
 * honest "unclear" fallback matches the project's never-fabricate principle.
 */
@Component
class PatternEvidenceGuard {

    static final String UNCLEAR_PATTERN_LABEL = "Pattern unclear - manual review suggested";

    private static final Pattern LOOP = Pattern.compile("\\b(for|while)\\s*\\(");
    private static final Pattern HALVING = Pattern.compile("\\bmid\\b|/\\s*2|>>>?\\s*1");
    private static final Pattern ARRAY_OR_TABLE = Pattern.compile("\\[|\\bMap\\b|\\bHashMap\\b|\\bmemo\\b|\\bdp\\b|\\bcache\\b");
    private static final Pattern WORKLIST_TYPE = Pattern.compile("\\b(Stack|Queue|Deque|ArrayDeque|LinkedList|PriorityQueue)\\b");
    private static final Pattern HASH_EVIDENCE = Pattern.compile(
            "\\b(Map|HashMap|Set|HashSet|Hashtable|TreeMap|TreeSet|LinkedHashMap|LinkedHashSet)\\b"
                    + "|\\.put\\(|\\.containsKey\\(|\\.getOrDefault\\(");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "\\b[\\w\\[\\]<>]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");

    /**
     * @return true if the labeled pattern's basic structural prerequisite is
     * present in the code (or the label isn't one we know how to check) -
     * i.e. true means "do not overrule the LLM".
     */
    boolean isSupported(String codeSnippet, String patternLabel) {
        if (patternLabel == null || codeSnippet == null) {
            return true;
        }
        String label = patternLabel.toLowerCase(Locale.ROOT);

        if (label.contains("sliding window") || label.contains("two pointer")) {
            return LOOP.matcher(codeSnippet).find();
        }
        if (label.contains("binary search")) {
            return LOOP.matcher(codeSnippet).find() || HALVING.matcher(codeSnippet).find();
        }
        if (label.contains("dynamic programming")) {
            return ARRAY_OR_TABLE.matcher(codeSnippet).find();
        }
        if (label.contains("dfs") || label.contains("bfs")
                || label.contains("depth-first") || label.contains("breadth-first")) {
            return WORKLIST_TYPE.matcher(codeSnippet).find() || hasSelfRecursiveCall(codeSnippet);
        }
        if (label.contains("hash")) {
            return HASH_EVIDENCE.matcher(codeSnippet).find();
        }
        return true;
    }

    /**
     * Approximate recursion detection: any declared method whose name appears
     * again followed by "(" elsewhere (i.e. at least one call besides the
     * declaration itself). Slightly over-approximates (a call from main also
     * counts) - acceptable, since over-approximating evidence only makes the
     * guard MORE conservative about overruling the LLM, never less.
     */
    private boolean hasSelfRecursiveCall(String code) {
        Set<String> declaredNames = new HashSet<>();
        Matcher decl = METHOD_DECLARATION.matcher(code);
        while (decl.find()) {
            declaredNames.add(decl.group(1));
        }
        for (String name : declaredNames) {
            Matcher call = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(code);
            int count = 0;
            while (call.find()) {
                count++;
            }
            if (count >= 2) {
                return true;
            }
        }
        return false;
    }
}
