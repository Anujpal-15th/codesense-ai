package com.codesense.analysis;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(Long id) {
        super("No analysis found with id " + id);
    }
}
