package triage.eval;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import triage.pipeline.TriageResult;

/** One scored case; stored as eval_case_result.detail. */
public record CaseResult(
        String caseId,
        String slice,
        String lang,
        List<String> tags,
        String text,
        JsonNode expected,
        Actual actual,
        Scores scores,
        long latencyMs,
        String error,
        TriageResult result
) {

    public record Actual(String category, String priority, String sentiment, Boolean multiIntent, Double confidence,
                         String orderId, String email, BigDecimal amount, Boolean needsHuman, String escalationReason,
                         String queue, List<String> citedArticles, String reply,
                         String modelCategory, String modelPriority) {}

    public record Scores(boolean categoryCorrect, boolean priorityExact, boolean priorityWithin1,
                         Boolean sentimentCorrect, Boolean citationHit, boolean policyViolation,
                         boolean policyViolationBeforeCritic, Boolean escalationCorrect,
                         boolean allStepsValidFirstAttempt, boolean allStepsSucceeded,
                         int entitiesFirstAttempt, int ungroundedFirstAttempt, int entitiesFinal, int ungroundedFinal,
                         boolean modelCategoryCorrect, boolean modelPriorityExact) {}

    public String expectedText(String field) {
        JsonNode n = expected.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    public boolean expects(String field) {
        return expected.has(field);
    }

    public boolean correct() {
        return error == null && scores.categoryCorrect() && scores.priorityExact();
    }
}
