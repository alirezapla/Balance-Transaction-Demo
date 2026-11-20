package com.example.balance.exception;

public class ReconciliationRunNotFoundException extends RuntimeException {
    public ReconciliationRunNotFoundException(Long id) {
        super("Reconciliation run not found: " + id);
    }
}
