package triage.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRunEntity, Long> {

    List<EvalRunEntity> findAllByOrderByIdDesc();
}
