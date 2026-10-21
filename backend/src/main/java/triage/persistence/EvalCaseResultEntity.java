package triage.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "eval_case_result")
public class EvalCaseResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private String caseId;
    private String slice;
    private boolean correct;
    private long latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detail;

    protected EvalCaseResultEntity() {}

    public EvalCaseResultEntity(Long runId, String caseId, String slice, boolean correct, long latencyMs, String detail) {
        this.runId = runId;
        this.caseId = caseId;
        this.slice = slice;
        this.correct = correct;
        this.latencyMs = latencyMs;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getCaseId() { return caseId; }
    public String getSlice() { return slice; }
    public boolean isCorrect() { return correct; }
    public long getLatencyMs() { return latencyMs; }
    public String getDetail() { return detail; }
}
