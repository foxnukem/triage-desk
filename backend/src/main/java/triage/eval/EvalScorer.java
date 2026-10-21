package triage.eval;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import triage.ai.Classification;
import triage.ai.Extraction;
import triage.ai.Priority;
import triage.pipeline.TriageResult;
import triage.validation.EntityGroundingValidator;

@Component
public class EvalScorer {

    private final EntityGroundingValidator grounding;

    public EvalScorer(EntityGroundingValidator grounding) {
        this.grounding = grounding;
    }

    public CaseResult score(EvalCase c, TriageResult r, long latencyMs) {
        Classification cl = r.classification();
        Extraction ex = r.extraction();
        String modelCategory = r.ruleOverride() != null ? r.ruleOverride().modelCategory().name() : cl.category().name();
        String modelPriority = r.ruleOverride() != null ? r.ruleOverride().modelPriority().name() : cl.priority().name();
        List<String> cited = r.reply() == null ? List.of() : r.reply().citedArticles();
        var actual = new CaseResult.Actual(cl.category().name(), cl.priority().name(), cl.sentiment().name(),
                cl.multiIntent(), cl.confidence(), ex.orderId(), ex.email(), ex.amount(), r.needsHuman(),
                r.escalationReason(), r.queue(), cited, r.reply() == null ? null : r.reply().text(),
                modelCategory, modelPriority);

        Priority expectedPriority = Priority.valueOf(c.expectedText("priority"));
        Priority actualPriority = cl.priority();
        Boolean sentimentCorrect = c.expects("sentiment") ? c.expectedText("sentiment").equals(cl.sentiment().name()) : null;
        List<String> rel = new java.util.ArrayList<>();
        c.expected().path("relevantArticles").forEach(n -> rel.add(n.asText()));
        Boolean citationHit = rel.isEmpty() || !r.options().reply() ? null
                : cited.stream().anyMatch(rel::contains);
        Boolean escalationCorrect = c.expects("needsHuman")
                ? c.expected().get("needsHuman").asBoolean() == r.needsHuman() : null;
        var d = r.diagnostics();
        var scores = new CaseResult.Scores(
                c.expectedText("category").equals(cl.category().name()),
                expectedPriority == actualPriority,
                expectedPriority.distance(actualPriority) <= 1,
                sentimentCorrect,
                citationHit,
                !d.policyLintFinal().isEmpty(),
                !d.policyLintBeforeCritic().isEmpty(),
                escalationCorrect,
                !d.firstAttemptValid().containsValue(false),
                !d.stepSucceeded().containsValue(false),
                d.entitiesFirstAttempt(), d.ungroundedFirstAttempt(),
                EntityGroundingValidator.countEntities(ex), d.ungroundedFinal(),
                c.expectedText("category").equals(modelCategory),
                c.expectedText("priority").equals(modelPriority));
        return new CaseResult(c.id(), c.slice(), c.lang(), c.tags(), c.text(), c.expected(), actual, scores,
                latencyMs, null, r);
    }

    public CaseResult failed(EvalCase c, String error, long latencyMs) {
        return new CaseResult(c.id(), c.slice(), c.lang(), c.tags(), c.text(), c.expected(), null, null, latencyMs, error, null);
    }

    /** Exact-match comparison used for entity precision/recall. */
    static boolean entityEquals(String field, String expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        if (field.equals("amount")) {
            return new BigDecimal(expected).compareTo((BigDecimal) actual) == 0;
        }
        return expected.equalsIgnoreCase(actual.toString());
    }
}
