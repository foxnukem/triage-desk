package triage.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyDecisionRepository extends JpaRepository<ReplyDecisionEntity, Long> {

    List<ReplyDecisionEntity> findByTicketIdOrderByIdAsc(Long ticketId);

    long countByAction(DecisionAction action);
}
