package com.example.balance.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "account")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_id", nullable = false, unique = true, length = 100)
    private String accountId;
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;
    @Column(name = "normal_balance_side", nullable = false, length = 10)
    private String normalBalanceSide;
    @Column(nullable = false)
    private long balance;

    protected Account() {}
    public Account(String accountId, String accountType, String normalBalanceSide, long balance) {
        this.accountId = accountId;
        this.accountType = accountType;
        this.normalBalanceSide = normalBalanceSide;
        this.balance = balance;
    }
    public void changeBalance(long delta) {
        this.balance = Math.addExact(this.balance, delta);
    }
    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getAccountType() { return accountType; }
    public String getNormalBalanceSide() { return normalBalanceSide; }
    public long getBalance() { return balance; }
}
