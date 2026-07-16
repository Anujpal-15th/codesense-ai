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

    // Java loops always parenthesize the condition (`for (`, `while (`); Python
    // never does (`for x in`, `while cond:`). Checking the wrong language's form
    // is the classic cause of a correct label being wrongly withheld, so every
    // structural check below is picked per language.
    private static final Pattern LOOP_JAVA = Pattern.compile("\\b(for|while)\\s*\\(");
    private static final Pattern LOOP_PY = Pattern.compile("\\b(for|while)\\b");
    // `//?\s*2` matches both Java `/2` and Python floor-division `//2`.
    private static final Pattern HALVING = Pattern.compile("\\bmid\\b|//?\\s*2|>>>?\\s*1");
    private static final Pattern ARRAY_OR_TABLE = Pattern.compile("\\[|\\bMap\\b|\\bHashMap\\b|\\bmemo\\b|\\bdp\\b|\\bcache\\b");
    private static final Pattern ARRAY_OR_TABLE_PY = Pattern.compile("\\[|\\bmemo\\b|\\bdp\\b|\\bcache\\b|\\bdict\\b|\\{");
    private static final Pattern WORKLIST_TYPE = Pattern.compile("\\b(Stack|Queue|Deque|ArrayDeque|LinkedList|PriorityQueue)\\b");
    // Python worklists are plain lists/deques driven by append/pop, or heapq.
    private static final Pattern WORKLIST_PY = Pattern.compile(
            "\\bdeque\\b|\\bheapq\\b|\\bstack\\b|\\bqueue\\b|\\.append\\(|\\.pop\\(|\\.popleft\\(");
    private static final Pattern HASH_EVIDENCE = Pattern.compile(
            "\\b(Map|HashMap|Set|HashSet|Hashtable|TreeMap|TreeSet|LinkedHashMap|LinkedHashSet)\\b"
                    + "|\\.put\\(|\\.containsKey\\(|\\.getOrDefault\\(");
    // Python hashing: dict/set literals (`{...}` - braces only ever mean a
    // dict/set/f-string in Python, never a code block), dict()/set() builders,
    // or the collections helpers.
    private static final Pattern HASH_PY = Pattern.compile(
            "\\{|\\bdict\\b|\\bset\\b|\\bdefaultdict\\b|\\bCounter\\b|\\bOrderedDict\\b");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "\\b[\\w\\[\\]<>]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern DEF_PYTHON = Pattern.compile("\\bdef\\s+(\\w+)\\s*\\(");

    /**
     * @param language "java" or "python" - selects which language's structural
     *                 fingerprint to look for (see the per-language patterns).
     * @return true if the labeled pattern's basic structural prerequisite is
     * present in the code (or the label isn't one we know how to check) -
     * i.e. true means "do not overrule the LLM".
     */
    boolean isSupported(String codeSnippet, String patternLabel, String language) {
        if (patternLabel == null || codeSnippet == null) {
            return true;
        }
        String label = patternLabel.toLowerCase(Locale.ROOT);
        boolean python = "python".equalsIgnoreCase(language);
        Pattern loop = python ? LOOP_PY : LOOP_JAVA;

        if (label.contains("sliding window") || label.contains("two pointer")) {
            return loop.matcher(codeSnippet).find();
        }
        if (label.contains("binary search")) {
            return loop.matcher(codeSnippet).find() || HALVING.matcher(codeSnippet).find();
        }
        if (label.contains("dynamic programming")) {
            return (python ? ARRAY_OR_TABLE_PY : ARRAY_OR_TABLE).matcher(codeSnippet).find();
        }
        if (label.contains("dfs") || label.contains("bfs")
                || label.contains("depth-first") || label.contains("breadth-first")) {
            return (python ? WORKLIST_PY : WORKLIST_TYPE).matcher(codeSnippet).find()
                    || hasSelfRecursiveCall(codeSnippet, python);
        }
        if (label.contains("hash")) {
            return (python ? HASH_PY : HASH_EVIDENCE).matcher(codeSnippet).find();
        }
        return true;
    }

    /**
     * Approximate recursion detection: any declared method/function whose name
     * appears again followed by "(" elsewhere (i.e. at least one call besides
     * the declaration itself). Slightly over-approximates (a call from main also
     * counts) - acceptable, since over-approximating evidence only makes the
     * guard MORE conservative about overruling the LLM, never less.
     */
    private boolean hasSelfRecursiveCall(String code, boolean python) {
        Set<String> declaredNames = new HashSet<>();
        Matcher decl = (python ? DEF_PYTHON : METHOD_DECLARATION).matcher(code);
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
