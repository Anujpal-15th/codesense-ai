package com.codesense.analysis;

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

    public AnalysisResponse analyze(String codeSnippet) {
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

    public List<AnalysisResponse> getHistory() {
        return analysisRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    public AnalysisResponse getById(Long id) {
        Analysis analysis = analysisRepository.findById(id)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
        return AnalysisResponse.from(analysis);
    }
}
