package com.example.balance.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_run", indexes = {
        @Index(name = "idx_reconciliation_run_started_at", columnList = "started_at"),
        @Index(name = "idx_reconciliation_run_status", columnList = "status")
})
public class ReconciliationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "account_count", nullable = false)
    private int accountCount;
    @Column(name = "inconsistent_account_count", nullable = false)
    private int inconsistentAccountCount;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    public ReconciliationRun() {
        this.status = ReconciliationStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete(int accounts, int inconsistencies) {
        this.accountCount = accounts;
        this.inconsistentAccountCount = inconsistencies;
        this.status = inconsistencies == 0 ? ReconciliationStatus.SUCCESS : ReconciliationStatus.INCONSISTENT;
        this.completedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = ReconciliationStatus.FAILED;
        this.errorMessage = message == null ? "Unexpected reconciliation failure" : message.substring(0, Math.min(message.length(), 2000));
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public ReconciliationStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getAccountCount() { return accountCount; }
    public int getInconsistentAccountCount() { return inconsistentAccountCount; }
    public String getErrorMessage() { return errorMessage; }
}
