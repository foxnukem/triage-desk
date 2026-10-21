package triage.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCaseResultRepository extends JpaRepository<EvalCaseResultEntity, Long> {

    List<EvalCaseResultEntity> findByRunIdOrderByCaseIdAsc(Long runId);
}
