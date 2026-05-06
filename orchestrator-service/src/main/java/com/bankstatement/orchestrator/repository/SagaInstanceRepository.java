package com.bankstatement.orchestrator.repository;

import com.bankstatement.orchestrator.model.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    List<SagaInstance> findBySourceAccountIdOrderByStartedAtDesc(String sourceAccountId);
}
