package com.example.balance.reconciliation;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reconciliation.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
    private final ReconciliationRunner runner;
    public ReconciliationScheduler(ReconciliationRunner runner) { this.runner = runner; }

    @Scheduled(cron = "${app.reconciliation.scheduler.cron:0 0 2 * * *}", zone = "${app.reconciliation.scheduler.zone:UTC}")
    @SchedulerLock(name = "dailyLedgerReconciliation", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    public void execute() {
        try { runner.runNow(); }
        catch (RuntimeException ex) { log.error("Scheduled reconciliation failed", ex); }
    }
}
