package com.example.balance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    interface BalanceAggregate {
        String getAccountId();
        String getNormalBalanceSide();
        Long getStoredBalance();
        Long getDebits();
        Long getCredits();
    }

    @Query(value = "select a.account_id as \"accountId\", a.normal_balance_side as \"normalBalanceSide\", a.balance as \"storedBalance\"," +
            " coalesce(sum(case when e.entry_side='DEBIT' then e.amount else 0 end),0) as \"debits\"," +
            " coalesce(sum(case when e.entry_side='CREDIT' then e.amount else 0 end),0) as \"credits\"" +
            " from account a left join ledger_entry e on e.account_id=a.id group by a.id,a.account_id,a.normal_balance_side,a.balance", nativeQuery = true)
    List<BalanceAggregate> aggregateBalances();
}
