package com.example.balance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    Optional<FinancialTransaction> findByTransactionId(String transactionId);
    @Modifying
    @Query(value = "insert into financial_transaction(transaction_id, transaction_type, request_fingerprint, created_at) values (:key,:type,:fingerprint,now()) on conflict (transaction_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("key") String key, @Param("type") String type, @Param("fingerprint") String fingerprint);
}
