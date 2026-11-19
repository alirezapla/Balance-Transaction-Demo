package com.example.balance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {
    List<ReconciliationRun> findTop100ByOrderByStartedAtDesc();
}
