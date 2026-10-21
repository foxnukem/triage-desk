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
@Table(name = "eval_run")
public class EvalRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String config;

    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metrics;

    private int totalCases;
    private int doneCases;
    private String error;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    protected EvalRunEntity() {}

    public EvalRunEntity(String label, String config, int totalCases) {
        this.label = label;
        this.config = config;
        this.totalCases = totalCases;
        this.status = "RUNNING";
    }

    @PrePersist
    void onCreate() {
        startedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public String getConfig() { return config; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetrics() { return metrics; }
    public void setMetrics(String metrics) { this.metrics = metrics; }
    public int getTotalCases() { return totalCases; }
    public int getDoneCases() { return doneCases; }
    public void setDoneCases(int doneCases) { this.doneCases = doneCases; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}
