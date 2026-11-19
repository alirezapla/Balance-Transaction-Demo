package com.example.balance;

import com.example.balance.dto.ReconciliationReport;
import com.example.balance.exception.InsufficientBalanceException;
import com.example.balance.exception.IdempotencyConflictException;
import com.example.balance.service.BalanceService;
import com.example.balance.reconciliation.ReconciliationRunner;
import com.example.balance.reconciliation.ReconciliationHistoryService;
import com.example.balance.domain.ReconciliationStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.reconciliation.scheduler.enabled=false")
class BalanceServicePostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("balance").withUsername("test").withPassword("test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired BalanceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReconciliationRunner reconciliationRunner;
    @Autowired ReconciliationHistoryService reconciliationHistory;

    @BeforeEach void cleanDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_destination_ledger_entry ON ledger_entry");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_destination_ledger_entry_fn()");
        jdbc.execute("TRUNCATE ledger_entry, financial_transaction, account RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO account(account_id,account_type,normal_balance_side,balance) VALUES('SYSTEM_CLEARING','SYSTEM','DEBIT',0)");
    }

    @Test void balanceMustNeverBecomeNegative() {
        service.createAccount("A", 1_000);
        assertThrows(InsufficientBalanceException.class, () -> service.debit("A", 1_001, "TOO-MUCH"));
        assertEquals(1_000, service.balance("A"));
        assertEquals(0, countEntries("TOO-MUCH"));
    }

    @Test void concurrentUpdatesMustNotBeLost() throws Exception {
        service.createAccount("A", 0);
        int operations = 120;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = IntStream.range(0, operations).mapToObj(i -> pool.submit(() -> {
                start.await(); service.credit("A", 10, "CREDIT-" + i); return null;
            })).toList();
            start.countDown();
            for (Future<?> future : futures) future.get(45, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(operations * 10L, service.balance("A"));
    }

    @Test void balanceProjectionMustMatchLedger() {
        service.createAccount("A", 500);
        service.credit("A", 200, "C-1");
        service.debit("A", 50, "D-1");
        assertEquals(650, service.balance("A"));
        ReconciliationReport clean = service.reconcile();
        assertEquals(0, clean.inconsistentAccountCount());

        jdbc.update("UPDATE account SET balance=balance+7 WHERE account_id='A'");
        ReconciliationReport inconsistent = service.reconcile();
        assertEquals(1, inconsistent.inconsistentAccountCount());
        assertEquals(7, inconsistent.differences().getFirst().difference());
        assertEquals(650, inconsistent.differences().getFirst().ledgerBalance());
    }

    @Test void failedTransferMustRollbackAllItsEffects() {
        service.createAccount("A", 1_000);
        service.createAccount("B", 100);
        jdbc.execute("CREATE FUNCTION fail_destination_ledger_entry_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.account_id=(SELECT id FROM account WHERE account_id='B') THEN RAISE EXCEPTION 'simulated ledger write failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_destination_ledger_entry BEFORE INSERT ON ledger_entry FOR EACH ROW EXECUTE FUNCTION fail_destination_ledger_entry_fn()");

        assertThrows(RuntimeException.class, () -> service.transfer("A", "B", 300, "BROKEN-TRANSFER"));
        assertEquals(1_000, service.balance("A"));
        assertEquals(100, service.balance("B"));
        assertEquals(0, countEntries("BROKEN-TRANSFER"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE transaction_id='BROKEN-TRANSFER'", Integer.class));
    }

    @Test void duplicateTransactionIsIdempotentAndConflictingPayloadIsRejected() {
        service.createAccount("A", 0);
        service.credit("A", 250, "IDEMPOTENT-1");
        service.credit("A", 250, "IDEMPOTENT-1");
        assertEquals(250, service.balance("A"));
        assertThrows(IdempotencyConflictException.class, () -> service.credit("A", 251, "IDEMPOTENT-1"));
        assertEquals(250, service.balance("A"));
    }

    @Test void concurrentDebitsCannotOverdrawAccount() throws Exception {
        service.createAccount("A", 1_000);
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        try {
            var futures = IntStream.range(0, attempts).mapToObj(i -> pool.submit(() -> {
                start.await();
                try { service.debit("A", 100, "DEBIT-" + i); succeeded.incrementAndGet(); }
                catch (InsufficientBalanceException ignored) { }
                return null;
            })).toList();
            start.countDown();
            for (Future<?> future : futures) future.get(45, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(10, succeeded.get());
        assertEquals(0, service.balance("A"));
    }


    @Test void reconciliationRunIsPersistedAndCanBeReported() {
        service.createAccount("A", 400);
        service.credit("A", 100, "HISTORY-CREDIT");
        jdbc.update("UPDATE account SET balance=balance+9 WHERE account_id='A'");

        Long runId = reconciliationRunner.runNow();
        var details = reconciliationHistory.details(runId);

        assertEquals(ReconciliationStatus.INCONSISTENT, details.run().status());
        assertEquals(1, details.run().accountCount());
        assertEquals(1, details.run().inconsistentAccountCount());
        assertEquals(9, details.differences().getFirst().difference());
        assertNotNull(details.run().completedAt());
        assertTrue(reconciliationHistory.history().stream().anyMatch(run -> run.id().equals(runId)));
    }

    private int countEntries(String transactionId) {
        return jdbc.queryForObject("SELECT count(*) FROM ledger_entry e JOIN financial_transaction t ON t.id=e.financial_transaction_id WHERE t.transaction_id=?", Integer.class, transactionId);
    }
}
