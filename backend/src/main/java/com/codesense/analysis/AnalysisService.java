package com.codesense.analysis;

import com.codesense.validation.CodeSubmissionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String CUSTOM_DATA_STRUCTURE_HINT = """


            [Static analysis note: this code defines a custom self-referential data \
            structure (a class with a Node/next/left/head-style field referencing \
            another class declared in this snippet) and does not use any java.util \
            collection. If no genuine algorithmic pattern applies beyond building or \
            traversing this structure, use "Custom data structure implementation" as \
            the pattern - do not force-fit a named pattern onto it.]""";

    private static final String NO_VERIFIED_IMPROVEMENT =
            "The current complexity appears appropriate for this problem; no verified improvement was identified.";

    private final AnalysisRepository analysisRepository;
    private final LlmClient llmClient;
    private final CustomDataStructureDetector customDataStructureDetector;
    private final PatternEvidenceGuard patternEvidenceGuard;
    private final ComplexityClaimGuard complexityClaimGuard;
    private final CodeSubmissionValidator submissionValidator;

    /**
     * @param language "java" or "python"; null/blank defaults to "java" for
     *                 pre-language clients. The LLM prompt is language-agnostic
     *                 (a DSA coach), so only the validation gate branches here.
     * @param userId   opaque client-generated id from the X-User-Id header, or
     *                 null if the caller didn't send one - stored as-is (a null
     *                 userId just means this row never shows up in any history
     *                 list, same as the pre-existing rows from before this
     *                 column existed).
     */
    public AnalysisResponse analyze(String codeSnippet, String language, String userId) {
        String lang = language == null || language.isBlank()
                ? "java"
                : language.trim().toLowerCase(java.util.Locale.ROOT);
        // Reject wrong-language code and natural-language instructions before
        // the LLM call (prompt-injection guard). InvalidSubmissionException -> 400.
        submissionValidator.validate(codeSnippet, lang);

        String llmInput = customDataStructureDetector.looksLikeCustomDataStructure(codeSnippet)
                ? codeSnippet + CUSTOM_DATA_STRUCTURE_HINT
                : codeSnippet;
        AnalysisResult result = llmClient.analyze(llmInput);

        long guardStart = System.nanoTime();
        boolean patternSupported = patternEvidenceGuard.isSupported(codeSnippet, result.pattern());
        long guardMicros = (System.nanoTime() - guardStart) / 1_000;
        log.info("Pattern evidence guard: label='{}' supported={} cost={}us",
                result.pattern(), patternSupported, guardMicros);

        String pattern = patternSupported
                ? result.pattern()
                : PatternEvidenceGuard.UNCLEAR_PATTERN_LABEL;
        String explanation = patternSupported
                ? result.explanation()
                : "[Automated check: the model labeled this \"" + result.pattern()
                        + "\" but the code lacks that pattern's basic structure, so the label was withheld.] "
                        + result.explanation();

        long claimStart = System.nanoTime();
        ComplexityClaimGuard.ComplexityClaimVerdict claimVerdict = complexityClaimGuard.check(
                codeSnippet, result.timeComplexity(), result.suggestedTimeComplexity(),
                result.isOptimal(), result.efficiencySuggestions());
        long claimMicros = (System.nanoTime() - claimStart) / 1_000;
        log.info("Complexity claim guard: incoherent={} reason={} cost={}us",
                claimVerdict.incoherent(), claimVerdict.reason(), claimMicros);

        // When the improvement claim is provably incoherent, drop the fabricated
        // number and mechanism and present it neutrally - deliberately WITHOUT
        // asserting optimality as a blanket default (that would wrongly suppress
        // valid suggestions like bubble sort's O(n log n)). Unverifiable-but-not-
        // incoherent claims are left untouched here; the prompt handles those.
        String suggestedTime = result.suggestedTimeComplexity();
        String efficiency = result.efficiencySuggestions();
        if (claimVerdict.incoherent()) {
            suggestedTime = null;
            efficiency = NO_VERIFIED_IMPROVEMENT;
        }

        Analysis analysis = new Analysis();
        analysis.setUserId(userId);
        analysis.setCodeSnippet(codeSnippet);
        analysis.setPattern(pattern);
        analysis.setTimeComplexity(result.timeComplexity());
        analysis.setSpaceComplexity(result.spaceComplexity());
        analysis.setOptimal(result.isOptimal());
        analysis.setExplanation(explanation);
        analysis.setReadability(result.readability());
        analysis.setStructure(result.structure());
        analysis.setStyleSuggestions(result.styleSuggestions());
        analysis.setSuggestedTimeComplexity(suggestedTime);
        analysis.setEfficiencySuggestions(efficiency);
        analysis.setOverallScore(result.overallScore());
        analysis.setCodeQuality(result.codeQuality());
        analysis.setMaintainability(result.maintainability());
        analysis.setBugs(result.bugs() != null ? result.bugs() : List.of());
        analysis.setEdgeCases(result.edgeCases() != null ? result.edgeCases() : List.of());
        analysis.setLearningTips(result.learningTips() != null ? result.learningTips() : List.of());

        Analysis saved = analysisRepository.save(analysis);
        return AnalysisResponse.from(saved);
    }

    /**
     * @param userId no header sent -> empty list, not an error and not "every
     *               user's history": there's no login, so an absent userId
     *               can't be trusted to mean anything more specific.
     */
    public List<AnalysisResponse> getHistory(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    /**
     * Lightweight history list for the list view - see
     * {@link AnalysisRepository#findSummariesByUserId(String)}. The full record
     * is fetched per-row via {@link #getById(Long, String)} when a card is opened.
     *
     * @param userId no header sent -> empty list (see {@link #getHistory(String)}).
     */
    public List<AnalysisSummaryResponse> getHistorySummaries(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return analysisRepository.findSummariesByUserId(userId);
    }

    /** @param userId must match the row's owner or this 404s exactly like a
     *                nonexistent id - a wrong/missing userId never leaks
     *                whether the id exists for someone else. Guarded explicitly
     *                against null/blank before it ever reaches the repository:
     *                Spring Data JPA derived queries turn "= null" into
     *                "IS NULL", so a bare findByIdAndUserId(id, null) would
     *                actually match the legacy rows that have no owner - the
     *                opposite of what a missing header should do. */
    public AnalysisResponse getById(Long id, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AnalysisNotFoundException(id);
        }
        Analysis analysis = analysisRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
        return AnalysisResponse.from(analysis);
    }
}
