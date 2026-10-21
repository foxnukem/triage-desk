package triage.persistence;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "reply_decision")
public class ReplyDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    private Long resultId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionAction action;

    private String finalText;
    private String note;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime decidedAt;

    protected ReplyDecisionEntity() {}

    public ReplyDecisionEntity(Long ticketId, Long resultId, DecisionAction action, String finalText, String note) {
        this.ticketId = ticketId;
        this.resultId = resultId;
        this.action = action;
        this.finalText = finalText;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        decidedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getResultId() { return resultId; }
    public DecisionAction getAction() { return action; }
    public String getFinalText() { return finalText; }
    public String getNote() { return note; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}
