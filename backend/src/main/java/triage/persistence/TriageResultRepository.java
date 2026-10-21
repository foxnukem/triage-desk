package triage.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageResultRepository extends JpaRepository<TriageResultEntity, Long> {

    List<TriageResultEntity> findByTicketIdOrderByIdDesc(Long ticketId);
}
