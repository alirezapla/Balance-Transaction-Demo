package com.example.balance.dto;

import com.example.balance.domain.ReconciliationStatus;
import java.time.Instant;
import java.util.List;

public final class ReconciliationRunDtos {
    private ReconciliationRunDtos() {}
    public record RunSummary(Long id, ReconciliationStatus status, Instant startedAt, Instant completedAt,
                             int accountCount, int inconsistentAccountCount, String errorMessage) {}
    public record RunDetails(RunSummary run, List<ReconciliationReport.AccountDifference> differences) {}
}
