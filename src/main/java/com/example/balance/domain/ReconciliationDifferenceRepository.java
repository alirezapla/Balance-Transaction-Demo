package com.example.balance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconciliationDifferenceRepository extends JpaRepository<ReconciliationDifference, Long> {
    List<ReconciliationDifference> findByRunIdOrderByAccountIdAsc(Long runId);
}
