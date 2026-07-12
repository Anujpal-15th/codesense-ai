package com.codesense.analysis;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    /** Opaque client-generated id, no login - see Analysis.userId. */
    private static final String USER_ID_HEADER = "X-User-Id";

    private final AnalysisService analysisService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse create(@Valid @RequestBody AnalysisRequest request,
                                    @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return analysisService.analyze(request.codeSnippet(), request.language(), userId);
    }

    @GetMapping
    public List<AnalysisResponse> history(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return analysisService.getHistory(userId);
    }

    /**
     * Lightweight list view. Declared before the {@code /{id}} mapping for
     * clarity - Spring matches the literal {@code /summary} segment ahead of the
     * {@code /{id}} path variable regardless, so there's no clash.
     */
    @GetMapping("/summary")
    public List<AnalysisSummaryResponse> summary(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return analysisService.getHistorySummaries(userId);
    }

    @GetMapping("/{id}")
    public AnalysisResponse getById(@PathVariable Long id,
                                     @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return analysisService.getById(id, userId);
    }
}
