package com.codesense.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final LlmClient llmClient;

    public AnalysisResponse analyze(String codeSnippet) {
        AnalysisResult result = llmClient.analyze(codeSnippet);

        Analysis analysis = new Analysis();
        analysis.setCodeSnippet(codeSnippet);
        analysis.setPattern(result.pattern());
        analysis.setTimeComplexity(result.timeComplexity());
        analysis.setSpaceComplexity(result.spaceComplexity());
        analysis.setOptimal(result.isOptimal());
        analysis.setExplanation(result.explanation());
        analysis.setReadability(result.readability());
        analysis.setStructure(result.structure());
        analysis.setStyleSuggestions(result.styleSuggestions());
        analysis.setSuggestedTimeComplexity(result.suggestedTimeComplexity());
        analysis.setEfficiencySuggestions(result.efficiencySuggestions());
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
