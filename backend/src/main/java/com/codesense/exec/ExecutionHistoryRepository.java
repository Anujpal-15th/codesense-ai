package com.codesense.exec;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, Long> {

    /**
     * Scoped single-record lookup: matches only when both the id AND the
     * owning userId agree - see {@code AnalysisRepository#findByIdAndUserId}
     * for why this must be a single derived-query condition rather than a
     * plain findById() plus an application-level ownership check (a bare
     * {@code userId = null} would silently match legacy/orphaned rows via
     * Spring Data's "= null" -> "IS NULL" translation).
     */
    Optional<ExecutionHistory> findByIdAndUserId(Long id, String userId);

    @Query("""
            SELECT new com.codesense.exec.ExecutionHistorySummaryResponse(
                e.id, e.language, e.outcome, e.sourceCode, e.totalStepsCaptured, e.createdAt)
            FROM ExecutionHistory e
            WHERE e.userId = :userId
            ORDER BY e.createdAt DESC
            """)
    List<ExecutionHistorySummaryResponse> findSummariesByUserId(String userId);
}
