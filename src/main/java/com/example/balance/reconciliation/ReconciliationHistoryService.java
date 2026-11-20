package com.example.balance.reconciliation;

import com.example.balance.domain.*;
import com.example.balance.dto.ReconciliationReport;
import com.example.balance.dto.ReconciliationRunDtos.RunDetails;
import com.example.balance.dto.ReconciliationRunDtos.RunSummary;
import com.example.balance.exception.ReconciliationRunNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReconciliationHistoryService {
    private final ReconciliationRunRepository runs;
    private final ReconciliationDifferenceRepository differences;

    public ReconciliationHistoryService(ReconciliationRunRepository runs, ReconciliationDifferenceRepository differences) {
        this.runs = runs;
        this.differences = differences;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startRun() {
        return runs.saveAndFlush(new ReconciliationRun()).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRun(Long runId, ReconciliationReport report) {
        ReconciliationRun run = runs.findById(runId).orElseThrow();
        run.complete(report.accountCount(), report.inconsistentAccountCount());
        differences.saveAll(report.differences().stream().map(d -> new ReconciliationDifference(run, d.accountId(), d.storedBalance(), d.ledgerBalance(), d.difference())).toList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(Long runId, Throwable error) {
        runs.findById(runId).ifPresent(run -> run.fail(error.getMessage()));
    }

    @Transactional(readOnly = true)
    public List<RunSummary> history() {
        return runs.findTop100ByOrderByStartedAtDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public RunDetails details(Long id) {
        ReconciliationRun run = runs.findById(id).orElseThrow(() -> new ReconciliationRunNotFoundException(id));
        List<ReconciliationReport.AccountDifference> items = differences.findByRunIdOrderByAccountIdAsc(id).stream()
                .map(d -> new ReconciliationReport.AccountDifference(d.getAccountId(), d.getStoredBalance(), d.getLedgerBalance(), d.getDifference())).toList();
        return new RunDetails(summary(run), items);
    }

    private RunSummary summary(ReconciliationRun r) {
        return new RunSummary(r.getId(), r.getStatus(), r.getStartedAt(), r.getCompletedAt(), r.getAccountCount(), r.getInconsistentAccountCount(), r.getErrorMessage());
    }
}
