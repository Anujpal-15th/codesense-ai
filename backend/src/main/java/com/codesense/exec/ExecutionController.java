package com.codesense.exec;

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
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final ExecutionService executionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse create(@Valid @RequestBody ExecutionRequest request,
                                     @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return executionService.execute(request.sourceCode(), request.language(), userId);
    }

    /** Lightweight list view - see {@code AnalysisController#summary} for why
     * this is declared before {@code /{id}} (Spring matches the literal
     * segment first regardless, but this documents the intent). */
    @GetMapping("/summary")
    public List<ExecutionHistorySummaryResponse> summary(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return executionService.getHistorySummaries(userId);
    }

    @GetMapping("/{id}")
    public ExecutionHistoryDetailResponse getById(@PathVariable Long id,
                                                   @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {
        return executionService.getById(id, userId);
    }
}
