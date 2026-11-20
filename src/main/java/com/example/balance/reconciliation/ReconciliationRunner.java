package com.example.balance.reconciliation;

import com.example.balance.dto.ReconciliationReport;
import com.example.balance.service.BalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationRunner {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationRunner.class);
    private final ReconciliationHistoryService history;
    private final BalanceService balanceService;

    public ReconciliationRunner(ReconciliationHistoryService history, BalanceService balanceService) {
        this.history = history;
        this.balanceService = balanceService;
    }

    public Long runNow() {
        Long runId = history.startRun();
        log.info("Reconciliation run started runId={}", runId);
        try {
            ReconciliationReport report = balanceService.reconcile();
            history.completeRun(runId, report);
            log.info("Reconciliation run completed runId={} accounts={} inconsistencies={}", runId,
                    report.accountCount(), report.inconsistentAccountCount());
        } catch (RuntimeException ex) {
            history.failRun(runId, ex);
            log.error("Reconciliation run failed runId={}", runId, ex);
            throw ex;
        }
        return runId;
    }
}
