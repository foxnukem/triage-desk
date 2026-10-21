package triage.persistence;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "triage_result")
public class TriageResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    private String category;
    private String priority;
    private String queue;

    @Column(nullable = false)
    private boolean needsHuman;

    @Column(nullable = false)
    private String model;

    /** Full TriageResult incl. trace, serialised by the service's ObjectMapper. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TriageResultEntity() {}

    public TriageResultEntity(Long ticketId, String category, String priority, String queue,
                              boolean needsHuman, String model, String result) {
        this.ticketId = ticketId;
        this.category = category;
        this.priority = priority;
        this.queue = queue;
        this.needsHuman = needsHuman;
        this.model = model;
        this.result = result;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public String getQueue() { return queue; }
    public boolean isNeedsHuman() { return needsHuman; }
    public String getModel() { return model; }
    public String getResult() { return result; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
