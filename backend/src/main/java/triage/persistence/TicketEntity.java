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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket")
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String text;

    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    private String triageError;

    private Long currentResultId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected TicketEntity() {}

    public TicketEntity(String text, String customerEmail, TicketSource source) {
        this.text = text;
        this.customerEmail = customerEmail;
        this.source = source;
        this.status = TicketStatus.NEW;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public String getCustomerEmail() { return customerEmail; }
    public TicketSource getSource() { return source; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public String getTriageError() { return triageError; }
    public void setTriageError(String triageError) { this.triageError = triageError; }
    public Long getCurrentResultId() { return currentResultId; }
    public void setCurrentResultId(Long currentResultId) { this.currentResultId = currentResultId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
