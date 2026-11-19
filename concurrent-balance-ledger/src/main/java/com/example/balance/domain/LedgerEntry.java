package com.example.balance.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="ledger_entry", indexes={@Index(name="idx_ledger_account", columnList="account_id,id"), @Index(name="idx_ledger_tx", columnList="financial_transaction_id")})
public class LedgerEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="financial_transaction_id", nullable=false)
    private FinancialTransaction transaction;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="account_id", nullable=false)
    private Account account;
    @Column(name="entry_side", nullable=false, length=10)
    private String entrySide;
    @Column(nullable=false)
    private long amount;
    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt=Instant.now();
    protected LedgerEntry() {}
    public LedgerEntry(FinancialTransaction transaction, Account account, String entrySide, long amount) {
        this.transaction=transaction; this.account=account; this.entrySide=entrySide; this.amount=amount;
    }
}
