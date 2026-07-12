package com.codesense.exec;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse create(@Valid @RequestBody ExecutionRequest request,
                                     @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return executionService.execute(request.sourceCode(), request.language(), userId);
    }
}
