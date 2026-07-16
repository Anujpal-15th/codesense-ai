package com.codesense.analysis;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cheap, deterministic post-LLM sanity check on a complexity-<em>improvement</em>
 * claim (the {@code suggestedTimeComplexity} / {@code efficiencySuggestions}
 * pair). Sibling of {@link PatternEvidenceGuard}, but deliberately far more
 * narrowly scoped, because the two problems are fundamentally different:
 *
 * <p>A pattern label ("Sliding Window") has a structural fingerprint in the
 * code that is <em>present</em> (a loop) - so a guard can verify it locally and
 * syntactically. An improvement claim is a claim about a better solution that,
 * by definition, is <em>not</em> in the code; confirming "a faster algorithm
 * exists for this problem" requires knowing the problem's inherent lower bound,
 * which has no syntactic fingerprint. Bubble sort (genuinely improvable to
 * O(n log n)) and Max-Points-on-a-Line (inherently O(n^2), not improvable) are
 * near-identical nested-loop shapes - no regex can tell them apart. So this
 * guard does NOT attempt to validate correctness of improvement claims in
 * general.
 *
 * <p>It fires only on the narrow subclass that is <em>provably incoherent</em>
 * from arithmetic or self-contradiction alone, needing zero problem knowledge:
 * <ol>
 *   <li><b>Not actually an improvement</b> - the model flagged the code
 *       suboptimal ({@code isOptimal == false}) but the suggested complexity is
 *       not strictly better than the current one (equal, or even worse - e.g.
 *       O(n) "improved" to O(n^2)).</li>
 *   <li><b>Suggests an already-present technique</b> - the efficiency advice
 *       says "use hashing"/"use sorting" but the code already uses that exact
 *       technique, making the advice self-contradictory.</li>
 * </ol>
 *
 * <p>When it fires, the caller drops the fabricated number/mechanism and
 * presents the result neutrally (see {@code AnalysisService}). It deliberately
 * does NOT assert the code is optimal as a blanket default - an unverifiable
 * claim (e.g. Max Points via "sorting/geometry", which needs problem knowledge)
 * is left untouched here and handled by the prompt instead, because a
 * structural guard there would be as likely to be wrong as the model, and would
 * wrongly suppress genuinely-correct suggestions like bubble sort's.
 */
@Component
class ComplexityClaimGuard {

    private static final Pattern HASH_PRESENT = Pattern.compile(
            "\\b(Map|HashMap|Set|HashSet|Hashtable|TreeMap|TreeSet|LinkedHashMap|LinkedHashSet)\\b"
                    + "|\\.put\\(|\\.containsKey\\(|\\.getOrDefault\\(");
    private static final Pattern HASH_PRESENT_PY = Pattern.compile(
            "\\{|\\bdict\\b|\\bset\\b|\\bdefaultdict\\b|\\bCounter\\b|\\bOrderedDict\\b");
    private static final Pattern SORT_CALL_PRESENT = Pattern.compile(
            "\\bArrays\\.sort\\(|\\bCollections\\.sort\\(|\\.sort\\(");
    // Python: list.sort() (already covered by `.sort(`) or the sorted() builtin.
    private static final Pattern SORT_CALL_PRESENT_PY = Pattern.compile("\\.sort\\(|\\bsorted\\s*\\(");

    /**
     * @return a verdict; {@code incoherent() == true} means the improvement
     * claim is provably broken and should be neutralized. {@code false} means
     * "leave the claim alone" - either it is coherent, or it is merely
     * unverifiable (which this guard does not touch, on purpose).
     */
    ComplexityClaimVerdict check(String code, String currentTime, String suggestedTime,
                                 boolean isOptimal, String efficiencySuggestions, String language) {
        // isOptimal == true: suggestedTimeComplexity is a confirmation that
        // equals the current complexity, and efficiencySuggestions is praise -
        // there is no improvement claim to scrutinize. Never fire.
        if (isOptimal) {
            return ComplexityClaimVerdict.ok();
        }

        // (1) Claimed suboptimal, but the suggested bound is not strictly
        // better than the current one. Only compares when BOTH complexities map
        // to a known rank - an unrecognized shape (e.g. O(m * n)) is left alone.
        int currentRank = rank(currentTime);
        int suggestedRank = rank(suggestedTime);
        if (currentRank >= 0 && suggestedRank >= 0 && suggestedRank >= currentRank) {
            return ComplexityClaimVerdict.incoherent(
                    "suggested " + suggestedTime + " is not better than current " + currentTime);
        }

        // (2) The advice names a technique the code already uses.
        if (suggestsAlreadyPresentTechnique(code, efficiencySuggestions, language)) {
            return ComplexityClaimVerdict.incoherent("suggested technique already present in code");
        }

        return ComplexityClaimVerdict.ok();
    }

    private boolean suggestsAlreadyPresentTechnique(String code, String suggestion, String language) {
        if (code == null || suggestion == null) {
            return false;
        }
        boolean python = "python".equalsIgnoreCase(language);
        String s = suggestion.toLowerCase(Locale.ROOT);
        if (s.contains("hash") && (python ? HASH_PRESENT_PY : HASH_PRESENT).matcher(code).find()) {
            return true;
        }
        // Only a real sort *call* counts as "sorting already present" - a manual
        // comparison sort (e.g. bubble sort) contains no sort call, so advising
        // "use mergesort" on it is NOT self-contradictory and must pass through.
        if (s.contains("sort") && (python ? SORT_CALL_PRESENT_PY : SORT_CALL_PRESENT).matcher(code).find()) {
            return true;
        }
        return false;
    }

    /**
     * Maps a Big-O string to an ordinal rank for comparison. Returns -1 for any
     * shape not in the known ladder, which callers treat as "cannot compare"
     * (never as evidence of incoherence). Gaps left between ranks intentionally.
     */
    private static int rank(String raw) {
        if (raw == null) {
            return -1;
        }
        String s = raw.toLowerCase(Locale.ROOT).trim()
                .replace("²", "^2").replace("³", "^3");
        s = s.replaceAll("\\s+", "");
        if (s.startsWith("o(")) {
            s = s.substring(2);
        }
        s = s.replace("(", "").replace(")", "")
                .replace("*", "").replace("·", "").replace("×", "");
        switch (s) {
            case "1":
                return 0;
            case "logn": case "log2n": case "lgn":
                return 10;
            case "sqrtn": case "n^0.5": case "n^1/2":
                return 20;
            case "n":
                return 30;
            case "nlogn": case "nlog2n": case "nlgn":
                return 40;
            case "n^2": case "nn":
                return 50;
            case "n^2logn":
                return 55;
            case "n^3": case "nnn":
                return 60;
            case "2^n":
                return 90;
            case "n!":
                return 100;
            default:
                return -1;
        }
    }

    record ComplexityClaimVerdict(boolean incoherent, String reason) {
        static ComplexityClaimVerdict ok() {
            return new ComplexityClaimVerdict(false, null);
        }

        static ComplexityClaimVerdict incoherent(String reason) {
            return new ComplexityClaimVerdict(true, reason);
        }
    }
}
