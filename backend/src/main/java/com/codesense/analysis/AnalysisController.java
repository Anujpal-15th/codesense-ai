package com.codesense.analysis;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse create(@Valid @RequestBody AnalysisRequest request) {
        return analysisService.analyze(request.codeSnippet());
    }

    @GetMapping
    public List<AnalysisResponse> history() {
        return analysisService.getHistory();
    }

    @GetMapping("/{id}")
    public AnalysisResponse getById(@PathVariable Long id) {
        return analysisService.getById(id);
    }
}
