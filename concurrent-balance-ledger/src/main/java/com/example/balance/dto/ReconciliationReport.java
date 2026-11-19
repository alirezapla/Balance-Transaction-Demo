package com.example.balance.dto;

import java.time.Instant;
import java.util.List;

public record ReconciliationReport(Instant checkedAt, int accountCount, int inconsistentAccountCount, List<AccountDifference> differences) {
    public record AccountDifference(String accountId, long storedBalance, long ledgerBalance, long difference) {}
}
