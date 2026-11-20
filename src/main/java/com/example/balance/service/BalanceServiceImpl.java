package com.example.balance.service;

import com.example.balance.domain.*;
import com.example.balance.dto.ReconciliationReport;
import com.example.balance.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class BalanceServiceImpl implements BalanceService {
    private static final Logger log = LoggerFactory.getLogger(BalanceServiceImpl.class);
    private static final String SYSTEM = "SYSTEM_CLEARING";
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LedgerRepository ledger;

    public BalanceServiceImpl(AccountRepository accounts, TransactionRepository transactions, LedgerRepository ledger) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ledger = ledger;
    }

    @Override
    @Transactional
    public void createAccount(String id, long opening) {
        if (id == null || id.isBlank() || SYSTEM.equals(id) || opening < 0)
            throw new InvalidOperationException("Invalid accountId or opening balance");
        if (accounts.existsByAccountId(id)) throw new AccountAlreadyExistsException(id);
        accounts.saveAndFlush(new Account(id, "WALLET", "CREDIT", 0));
        if (opening > 0) {
            String key = "OPENING-" + id;
            String fp = "OPENING|" + id + "|" + opening;
            FinancialTransaction tx = createTransaction(key, "OPENING", fp);
            Account target = lock(id);
            Account sys = systemAccount();
            post(tx, sys, "DEBIT", opening);
            post(tx, target, "CREDIT", opening);
        }
        log.info("Account created accountId={} openingBalance={}", id, opening);
    }

    @Override
    @Transactional
    public void credit(String id, long amount, String txId) {
        validate(id, amount, txId);
        log.info("Credit requested accountId={} transactionId={} amount={}", id, txId, amount);
        if (isDuplicate(txId, "CREDIT|" + id + "|" + amount, "CREDIT")) {
            log.info("Duplicate credit ignored transactionId={}", txId);
            return;
        }
        FinancialTransaction tx = transactions.findByTransactionId(txId).orElseThrow();
        Account wallet = lock(id);
        post(tx, systemAccount(), "DEBIT", amount);
        post(tx, wallet, "CREDIT", amount);
        log.info("Credit completed accountId={} transactionId={}", id, txId);
    }

    @Override
    @Transactional
    public void debit(String id, long amount, String txId) {
        validate(id, amount, txId);
        log.info("Debit requested accountId={} transactionId={} amount={}", id, txId, amount);
        if (isDuplicate(txId, "DEBIT|" + id + "|" + amount, "DEBIT")) {
            log.info("Duplicate debit ignored transactionId={}", txId);
            return;
        }
        FinancialTransaction tx = transactions.findByTransactionId(txId).orElseThrow();
        Account wallet = lock(id);
        if (wallet.getBalance() < amount) {
            log.warn("Debit rejected due to insufficient balance accountId={} transactionId={}", id, txId);
            throw new InsufficientBalanceException(id);
        }
        post(tx, wallet, "DEBIT", amount);
        post(tx, systemAccount(), "CREDIT", amount);
        log.info("Debit completed accountId={} transactionId={}", id, txId);
    }

    @Override
    @Transactional
    public void transfer(String from, String to, long amount, String txId) {
        validate(from, amount, txId);
        if (to == null || to.isBlank() || from.equals(to))
            throw new InvalidOperationException("Source and destination must be different valid accounts");
        log.info("Transfer requested source={} destination={} transactionId={} amount={}", from, to, txId, amount);
        if (isDuplicate(txId, "TRANSFER|" + from + "|" + to + "|" + amount, "TRANSFER")) {
            log.info("Duplicate transfer ignored transactionId={}", txId);
            return;
        }
        FinancialTransaction tx = transactions.findByTransactionId(txId).orElseThrow();
        String first = from.compareTo(to) < 0 ? from : to;
        String second = from.compareTo(to) < 0 ? to : from;
        Account a = lock(first);
        Account b = lock(second);
        Account source = from.equals(first) ? a : b;
        Account destination = to.equals(first) ? a : b;
        if (source.getBalance() < amount) {
            log.warn("Transfer rejected due to insufficient balance source={} transactionId={}", from, txId);
            throw new InsufficientBalanceException(from);
        }
        post(tx, source, "DEBIT", amount);
        post(tx, destination, "CREDIT", amount);
        log.info("Transfer completed source={} destination={} transactionId={}", from, to, txId);
    }

    @Override
    @Transactional(readOnly = true)
    public long balance(String id) {
        return accounts.findByAccountId(id).orElseThrow(() -> new AccountNotFoundException(id)).getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        List<ReconciliationReport.AccountDifference> differences = new ArrayList<>();
        List<LedgerRepository.BalanceAggregate> aggregates = ledger.aggregateBalances().stream()
                .filter(row -> !SYSTEM.equals(row.getAccountId())).toList();
        for (var row : aggregates) {
            long ledgerBalance = "CREDIT".equals(row.getNormalBalanceSide()) ? Math.subtractExact(row.getCredits(), row.getDebits()) : Math.subtractExact(row.getDebits(), row.getCredits());
            if (ledgerBalance != row.getStoredBalance())
                differences.add(new ReconciliationReport.AccountDifference(row.getAccountId(), row.getStoredBalance(), ledgerBalance, Math.subtractExact(row.getStoredBalance(), ledgerBalance)));
        }
        ReconciliationReport report = new ReconciliationReport(Instant.now(), aggregates.size(), differences.size(), List.copyOf(differences));
        if (!differences.isEmpty())
            log.error("Ledger reconciliation found {} inconsistent accounts out of {}", differences.size(), aggregates.size());
        else log.info("Ledger reconciliation completed successfully accounts={}", aggregates.size());
        return report;
    }

    private boolean isDuplicate(String key, String fingerprint, String type) {
        int inserted = transactions.insertIfAbsent(key, type, fingerprint);
        if (inserted == 1) return false;
        String existing = transactions.findByTransactionId(key).orElseThrow().getRequestFingerprint();
        if (!fingerprint.equals(existing)) {
            log.warn("Idempotency conflict transactionId={}", key);
            throw new IdempotencyConflictException(key);
        }
        return true;
    }

    private FinancialTransaction createTransaction(String key, String type, String fingerprint) {
        if (transactions.insertIfAbsent(key, type, fingerprint) == 0) throw new IdempotencyConflictException(key);
        return transactions.findByTransactionId(key).orElseThrow();
    }

    private Account lock(String id) {
        return accounts.findByAccountIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private Account systemAccount() {
        return accounts.findByAccountId(SYSTEM).orElseThrow(() -> new IllegalStateException("SYSTEM_CLEARING account is missing"));
    }

    private void post(FinancialTransaction tx, Account account, String side, long amount) {
        long delta = account.getNormalBalanceSide().equals(side) ? amount : -amount;
        if ("WALLET".equals(account.getAccountType())) {
            long next = Math.addExact(account.getBalance(), delta);
            if (next < 0) throw new InsufficientBalanceException(account.getAccountId());
            account.changeBalance(delta);
        }
        ledger.save(new LedgerEntry(tx, account, side, amount));
    }

    private void validate(String id, long amount, String tx) {
        if (id == null || id.isBlank()) throw new InvalidOperationException("accountId is required");
        if (amount <= 0) throw new InvalidOperationException("Amount must be positive");
        if (tx == null || tx.isBlank()) throw new InvalidOperationException("transactionId is required");
    }
}
