package com.example.balance.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "reconciliation_difference", indexes = @Index(name = "idx_reconciliation_difference_run", columnList = "reconciliation_run_id"))
public class ReconciliationDifference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    private ReconciliationRun run;
    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;
    @Column(name = "stored_balance", nullable = false)
    private long storedBalance;
    @Column(name = "ledger_balance", nullable = false)
    private long ledgerBalance;
    @Column(nullable = false)
    private long difference;

    protected ReconciliationDifference() {}
    public ReconciliationDifference(ReconciliationRun run, String accountId, long storedBalance, long ledgerBalance, long difference) {
        this.run = run; this.accountId = accountId; this.storedBalance = storedBalance;
        this.ledgerBalance = ledgerBalance; this.difference = difference;
    }
    public String getAccountId() { return accountId; }
    public long getStoredBalance() { return storedBalance; }
    public long getLedgerBalance() { return ledgerBalance; }
    public long getDifference() { return difference; }
}
