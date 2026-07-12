package com.codesense.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    /** Scoped history list for a single user (see {@link Analysis#getUserId()}). */
    List<Analysis> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Scoped single-record lookup: matches only when both the id AND the owning
     * userId agree, so requesting someone else's analysis id (or any id when
     * userId is null/blank) can never leak a record - it just looks not-found.
     */
    Optional<Analysis> findByIdAndUserId(Long id, String userId);

    /**
     * History-list projection: a single flat SELECT of only the scalar columns
     * the list needs. Because it's a constructor expression (not an entity
     * fetch), Hibernate never joins/loads the bugs/edgeCases/learningTips
     * element-collection tables or the other large TEXT columns — which is the
     * whole point (the full-entity {@link #findByUserIdOrderByCreatedAtDesc}
     * was the source of the slow history load before this projection existed).
     */
    @Query("""
            SELECT new com.codesense.analysis.AnalysisSummaryResponse(
                a.id, a.pattern, a.timeComplexity, a.spaceComplexity, a.isOptimal,
                a.codeSnippet, a.explanation, a.createdAt)
            FROM Analysis a
            WHERE a.userId = :userId
            ORDER BY a.createdAt DESC
            """)
    List<AnalysisSummaryResponse> findSummariesByUserId(String userId);
}
