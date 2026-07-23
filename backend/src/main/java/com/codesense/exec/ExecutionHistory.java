package com.codesense.exec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Data;

import java.time.Instant;

/**
 * A past execution, persisted so a user can revisit its step-through later -
 * mirrors {@code com.codesense.analysis.Analysis}'s userId-scoped, no-login
 * history pattern exactly. Only saved when the caller sent an
 * {@code X-User-Id}; see {@link ExecutionService#persistIfOwned}.
 *
 * <p>Unlike {@code Analysis}, the trace itself isn't broken out into columns
 * or element-collection tables - it's a deeply nested shape (steps, each with
 * a call stack, each frame with its own variables) that's never queried
 * piecemeal, only ever fetched whole for a single row by id. A JSON blob is
 * the right fit for that access pattern; flat columns are the right fit for
 * Analysis's scalar, individually-summarized fields.
 */
@Entity
@Data
public class ExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    private String language;

    @Column(columnDefinition = "TEXT")
    private String sourceCode;

    @Column(columnDefinition = "TEXT")
    private String executedSourceCode;

    private boolean wasWrapped;

    private String outcome;

    @Column(columnDefinition = "TEXT")
    private String consoleOutput;

    private int totalStepsCaptured;

    /** The full {@link ExecutionTrace}, JSON-serialized - see class javadoc. */
    @Column(columnDefinition = "TEXT")
    private String traceJson;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
