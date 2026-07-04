package com.codesense.analysis;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String codeSnippet;

    private String pattern;

    private String timeComplexity;

    private String spaceComplexity;

    private boolean isOptimal;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String readability;

    @Column(columnDefinition = "TEXT")
    private String structure;

    @Column(columnDefinition = "TEXT")
    private String styleSuggestions;

    @Column(columnDefinition = "TEXT")
    private String suggestedTimeComplexity;

    @Column(columnDefinition = "TEXT")
    private String efficiencySuggestions;

    private Integer overallScore;

    private String codeQuality;

    private String maintainability;

    @ElementCollection
    @CollectionTable(name = "analysis_bugs", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "bug", columnDefinition = "TEXT")
    private List<String> bugs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "analysis_edge_cases", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "edge_case", columnDefinition = "TEXT")
    private List<String> edgeCases = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "analysis_learning_tips", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "tip", columnDefinition = "TEXT")
    private List<String> learningTips = new ArrayList<>();

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
