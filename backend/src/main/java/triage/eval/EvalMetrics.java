package triage.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import triage.ai.Category;

/** Aggregates scored cases into the metrics of spec section 6. Ratios are null when undefined. */
public final class EvalMetrics {

    private EvalMetrics() {}

    public static Map<String, Object> compute(List<CaseResult> all) {
        List<CaseResult> ok = all.stream().filter(c -> c.error() == null).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cases", all.size());
        m.put("errors", all.size() - ok.size());

        // Category
        m.put("categoryAccuracy", share(ok, c -> c.scores().categoryCorrect()));
        m.put("categoryMacroF1", macroF1(ok, false));
        m.put("confusion", confusion(ok));

        // Priority
        m.put("priorityExact", share(ok, c -> c.scores().priorityExact()));
        m.put("priorityWithin1", share(ok, c -> c.scores().priorityWithin1()));
        List<CaseResult> p1 = ok.stream().filter(c -> "P1".equals(c.expectedText("priority"))).toList();
        m.put("p1Recall", share(p1, c -> "P1".equals(c.actual().priority())));
        List<CaseResult> p12 = ok.stream().filter(c -> List.of("P1", "P2").contains(c.expectedText("priority"))).toList();
        m.put("p1p2RecallNoLowerThanP2", share(p12, c -> List.of("P1", "P2").contains(c.actual().priority())));

        // Router counterfactual: the classifier's own answer before rule overrides.
        Map<String, Object> modelOnly = new LinkedHashMap<>();
        modelOnly.put("categoryAccuracy", share(ok, c -> c.scores().modelCategoryCorrect()));
        modelOnly.put("categoryMacroF1", macroF1(ok, true));
        modelOnly.put("priorityExact", share(ok, c -> c.scores().modelPriorityExact()));
        modelOnly.put("p1Recall", share(p1, c -> "P1".equals(c.actual().modelPriority())));
        modelOnly.put("p1p2RecallNoLowerThanP2", share(p12, c -> List.of("P1", "P2").contains(c.actual().modelPriority())));
        modelOnly.put("overrides", ok.stream().filter(c -> c.result().ruleOverride() != null).count());
        m.put("beforeRuleOverrides", modelOnly);

        // Entities
        m.put("entities", entities(ok, false));
        // Validation counterfactual: the extractor's first answer, before grounding retries and the order check.
        m.put("entitiesFirstAttempt", entities(ok, true));
        int entFirst = ok.stream().mapToInt(c -> c.scores().entitiesFirstAttempt()).sum();
        int ungFirst = ok.stream().mapToInt(c -> c.scores().ungroundedFirstAttempt()).sum();
        int entFinal = ok.stream().mapToInt(c -> c.scores().entitiesFinal()).sum();
        int ungFinal = ok.stream().mapToInt(c -> c.scores().ungroundedFinal()).sum();
        m.put("hallucinatedEntitiesFirstAttempt", ratio(ungFirst, entFirst));
        m.put("hallucinatedEntitiesFinal", ratio(ungFinal, entFinal));

        // Schema validity
        m.put("schemaValidFirstAttempt", share(ok, c -> c.scores().allStepsValidFirstAttempt()));
        m.put("schemaValidAfterRetries", share(ok, c -> c.scores().allStepsSucceeded()));
        m.put("retriesPerCase", ok.isEmpty() ? null : ok.stream().mapToInt(c -> c.result().trace().retries()).average().orElse(0));

        // Escalation
        List<CaseResult> esc = ok.stream().filter(c -> c.scores().escalationCorrect() != null).toList();
        long tp = esc.stream().filter(c -> c.actual().needsHuman() && expectedHuman(c)).count();
        long fp = esc.stream().filter(c -> c.actual().needsHuman() && !expectedHuman(c)).count();
        long fn = esc.stream().filter(c -> !c.actual().needsHuman() && expectedHuman(c)).count();
        m.put("escalationPrecision", ratio(tp, tp + fp));
        m.put("escalationRecall", ratio(tp, tp + fn));

        // Reply
        List<CaseResult> cit = ok.stream().filter(c -> c.scores().citationHit() != null).toList();
        m.put("citationHitRate", share(cit, c -> c.scores().citationHit()));
        List<CaseResult> drafted = ok.stream().filter(c -> c.actual().reply() != null).toList();
        m.put("drafts", drafted.size());
        m.put("policyViolationRate", share(drafted, c -> c.scores().policyViolation()));
        m.put("policyViolationRateBeforeCritic", share(drafted, c -> c.scores().policyViolationBeforeCritic()));

        // Other
        List<CaseResult> sent = ok.stream().filter(c -> c.scores().sentimentCorrect() != null).toList();
        m.put("sentimentAccuracyLabelled", share(sent, c -> c.scores().sentimentCorrect()));
        List<CaseResult> multi = ok.stream().filter(c -> c.expected().path("multiIntent").asBoolean(false)).toList();
        m.put("multiIntentRecall", share(multi, c -> Boolean.TRUE.equals(c.actual().multiIntent())));

        // Latency
        List<Long> lat = ok.stream().map(CaseResult::latencyMs).sorted().toList();
        m.put("latencyP50Ms", percentile(lat, 50));
        m.put("latencyP95Ms", percentile(lat, 95));
        m.put("llmCallsPerCase", ok.isEmpty() ? null : ok.stream().mapToInt(c -> c.result().trace().llmCalls()).average().orElse(0));
        m.put("llmCallsPerCaseWithoutRetries", ok.isEmpty() ? null
                : ok.stream().mapToInt(c -> c.result().trace().llmCalls() - c.result().trace().retries()).average().orElse(0));

        // Calibration: is self-reported confidence related to correctness?
        m.put("confidenceBins", calibration(ok));

        Map<String, Object> slices = new LinkedHashMap<>();
        for (String s : List.of("standard", "hard", "adversarial")) {
            List<CaseResult> part = ok.stream().filter(c -> s.equals(c.slice())).toList();
            if (!part.isEmpty()) {
                slices.put(s, Map.of("cases", part.size(),
                        "categoryAccuracy", share(part, c -> c.scores().categoryCorrect()),
                        "priorityExact", share(part, c -> c.scores().priorityExact())));
            }
        }
        m.put("bySlice", slices);
        return m;
    }

    private static boolean expectedHuman(CaseResult c) {
        return c.expected().path("needsHuman").asBoolean(false);
    }

    private static Object entityValue(CaseResult c, String field, boolean firstAttempt) {
        if (firstAttempt) {
            var d = c.result() == null ? null : c.result().diagnostics();
            var e = d == null ? null : d.firstExtraction();
            if (e == null) {
                return null;
            }
            return switch (field) {
                case "orderId" -> e.orderId();
                case "email" -> e.email();
                default -> e.amount();
            };
        }
        return switch (field) {
            case "orderId" -> c.actual().orderId();
            case "email" -> c.actual().email();
            default -> c.actual().amount();
        };
    }

    private static Map<String, Object> entities(List<CaseResult> ok, boolean firstAttempt) {
        Map<String, Object> out = new LinkedHashMap<>();
        int tpAll = 0;
        int fpAll = 0;
        int fnAll = 0;
        for (String field : List.of("orderId", "email", "amount")) {
            int tp = 0;
            int fp = 0;
            int fn = 0;
            for (CaseResult c : ok) {
                if (!c.expects(field)) {
                    continue;
                }
                String exp = c.expectedText(field);
                Object act = entityValue(c, field, firstAttempt);
                if (act != null && EvalScorer.entityEquals(field, exp, act)) {
                    tp++;
                } else {
                    if (act != null) {
                        fp++;
                    }
                    if (exp != null) {
                        fn++;
                    }
                }
            }
            out.put(field, Map.of("precision", nz(ratio(tp, tp + fp)), "recall", nz(ratio(tp, tp + fn)), "tp", tp, "fp", fp, "fn", fn));
            tpAll += tp;
            fpAll += fp;
            fnAll += fn;
        }
        out.put("precision", ratio(tpAll, tpAll + fpAll));
        out.put("recall", ratio(tpAll, tpAll + fnAll));
        return out;
    }

    static Double macroF1(List<CaseResult> ok, boolean beforeOverrides) {
        if (ok.isEmpty()) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (Category cat : Category.values()) {
            String k = cat.name();
            long tp = ok.stream().filter(c -> k.equals(c.expectedText("category")) && k.equals(pred(c, beforeOverrides))).count();
            long fp = ok.stream().filter(c -> !k.equals(c.expectedText("category")) && k.equals(pred(c, beforeOverrides))).count();
            long fn = ok.stream().filter(c -> k.equals(c.expectedText("category")) && !k.equals(pred(c, beforeOverrides))).count();
            if (tp + fp + fn == 0) {
                continue;
            }
            sum += tp == 0 ? 0 : 2.0 * tp / (2.0 * tp + fp + fn);
            n++;
        }
        return n == 0 ? null : sum / n;
    }

    private static String pred(CaseResult c, boolean beforeOverrides) {
        return beforeOverrides ? c.actual().modelCategory() : c.actual().category();
    }

    private static Map<String, Map<String, Long>> confusion(List<CaseResult> ok) {
        Map<String, Map<String, Long>> m = new LinkedHashMap<>();
        for (Category e : Category.values()) {
            Map<String, Long> row = new LinkedHashMap<>();
            for (Category p : Category.values()) {
                row.put(p.name(), ok.stream().filter(c -> e.name().equals(c.expectedText("category"))
                        && p.name().equals(c.actual().category())).count());
            }
            m.put(e.name(), row);
        }
        return m;
    }

    private static List<Map<String, Object>> calibration(List<CaseResult> ok) {
        double[][] bins = {{0, 0.6}, {0.6, 0.8}, {0.8, 0.9}, {0.9, 0.95}, {0.95, 1.0001}};
        List<Map<String, Object>> out = new ArrayList<>();
        for (double[] b : bins) {
            List<CaseResult> in = ok.stream().filter(c -> c.actual().confidence() != null
                    && c.actual().confidence() >= b[0] && c.actual().confidence() < b[1]).toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", b[0]);
            row.put("to", Math.min(b[1], 1.0));
            row.put("cases", in.size());
            row.put("categoryAccuracy", share(in, c -> c.scores().categoryCorrect()));
            out.add(row);
        }
        return out;
    }

    private static Double share(List<CaseResult> list, Predicate<CaseResult> p) {
        return list.isEmpty() ? null : (double) list.stream().filter(p).count() / list.size();
    }

    private static Double ratio(long a, long b) {
        return b == 0 ? null : (double) a / b;
    }

    private static Object nz(Double d) {
        return d == null ? "n/a" : d;
    }

    private static Long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return null;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
