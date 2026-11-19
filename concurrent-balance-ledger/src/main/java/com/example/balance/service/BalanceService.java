package com.example.balance.service;

import com.example.balance.dto.ReconciliationReport;
public interface BalanceService {
    void createAccount(String id,long opening);
    void credit(String id,long amount,String tx);
    void debit(String id,long amount,String tx);
    void transfer(String from,String to,long amount,String tx);
    long balance(String id);
    ReconciliationReport reconcile();
}
