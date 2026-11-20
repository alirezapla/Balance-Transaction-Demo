package com.example.balance.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "financial_transaction", uniqueConstraints = @UniqueConstraint(name = "uk_financial_transaction_transaction_id", columnNames = "transaction_id"))
public class FinancialTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "transaction_id", nullable = false, length = 150)
    private String transactionId;
    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;
    @Column(name = "request_fingerprint", nullable = false, length = 512)
    private String requestFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FinancialTransaction() {
    }

    public FinancialTransaction(String transactionId, String transactionType, String requestFingerprint) {
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.requestFingerprint = requestFingerprint;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }
}
