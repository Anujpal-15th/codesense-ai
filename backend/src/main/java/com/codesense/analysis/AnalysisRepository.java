package com.codesense.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findAllByOrderByCreatedAtDesc();

    /**
     * History-list projection: a single flat SELECT of only the scalar columns
     * the list needs. Because it's a constructor expression (not an entity
     * fetch), Hibernate never joins/loads the bugs/edgeCases/learningTips
     * element-collection tables or the other large TEXT columns — which is the
     * whole point (the full-entity {@link #findAllByOrderByCreatedAtDesc()} was
     * the source of the slow history load).
     */
    @Query("""
            SELECT new com.codesense.analysis.AnalysisSummaryResponse(
                a.id, a.pattern, a.timeComplexity, a.spaceComplexity, a.isOptimal,
                a.codeSnippet, a.explanation, a.createdAt)
            FROM Analysis a
            ORDER BY a.createdAt DESC
            """)
    List<AnalysisSummaryResponse> findAllSummaries();
}
