package triage.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import triage.TriageProperties;

/** eval/reports/&lt;timestamp&gt;-&lt;label&gt;.json + .md, and the combined ablation table. */
@Component
public class ReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Rows of the ablation table: metric key → label. Nested keys use dots. */
    static final Map<String, String> HEADLINE = headline();

    private final Path dir;
    private final ObjectMapper mapper;

    public ReportWriter(TriageProperties props, ObjectMapper mapper) {
        this.dir = Path.of(props.eval().reportsDir());
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static Map<String, String> headline() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("categoryAccuracy", "Category accuracy");
        m.put("categoryMacroF1", "Category macro-F1");
        m.put("priorityExact", "Priority exact");
        m.put("priorityWithin1", "Priority within-1");
        m.put("p1Recall", "P1 recall");
        m.put("p1p2RecallNoLowerThanP2", "P1/P2 tickets kept ≥ P2");
        m.put("entities.precision", "Entity precision");
        m.put("entities.recall", "Entity recall");
        m.put("hallucinatedEntitiesFirstAttempt", "Hallucinated entities (1st attempt)");
        m.put("hallucinatedEntitiesFinal", "Hallucinated entities (final)");
        m.put("schemaValidFirstAttempt", "All steps valid on 1st attempt");
        m.put("schemaValidAfterRetries", "All steps valid after retries");
        m.put("escalationPrecision", "Escalation precision");
        m.put("escalationRecall", "Escalation recall");
        m.put("citationHitRate", "Citation hit rate");
        m.put("policyViolationRate", "Drafts violating policy (lint)");
        m.put("sentimentAccuracyLabelled", "Sentiment (labelled cases)");
        m.put("multiIntentRecall", "Multi-intent detected");
        m.put("latencyP50Ms", "Latency p50, s");
        m.put("latencyP95Ms", "Latency p95, s");
        m.put("llmCallsPerCase", "LLM calls per ticket");
        return m;
    }

    public List<String> write(long runId, EvalConfig cfg, Map<String, Object> metrics, List<CaseResult> cases) {
        String base = LocalDateTime.now().format(TS) + "-" + cfg.label();
        Path json = dir.resolve(base + ".json");
        Path md = dir.resolve(base + ".md");
        try {
            Files.createDirectories(dir);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("runId", runId);
            doc.put("config", cfg);
            doc.put("metrics", metrics);
            doc.put("cases", cases.stream().map(ReportWriter::slim).toList());
            mapper.writeValue(json.toFile(), doc);
            Files.writeString(md, markdown(runId, cfg, metrics, cases), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.of(json.toString(), md.toString());
    }

    /** The JSON report keeps the trace summary, not every step output. */
    private static Map<String, Object> slim(CaseResult c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseId", c.caseId());
        m.put("slice", c.slice());
        m.put("expected", c.expected());
        m.put("actual", c.actual());
        m.put("scores", c.scores());
        m.put("latencyMs", c.latencyMs());
        m.put("error", c.error());
        if (c.result() != null) {
            m.put("ruleOverride", c.result().ruleOverride());
            m.put("diagnostics", c.result().diagnostics());
            m.put("trace", c.result().trace().steps().stream().map(s -> s.name() + "#" + s.attempt() + " " + s.status()
                    + " " + s.durationMs() + "ms" + (s.errors().isEmpty() ? "" : " " + s.errors())).toList());
        }
        return m;
    }

    String markdown(long runId, EvalConfig cfg, Map<String, Object> m, List<CaseResult> cases) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Eval run ").append(runId).append(" — ").append(cfg.label()).append("\n\n");
        sb.append("Model `").append(cfg.model()).append("`, rule router ").append(onOff(cfg.ruleRouter()))
                .append(", validation ").append(onOff(cfg.validation())).append(", critic ").append(onOff(cfg.critic()))
                .append(cfg.megaPrompt() ? ", **mega-prompt baseline**" : "")
                .append(cfg.reply() ? "" : ", reply step off").append(". Cases: ").append(m.get("cases"))
                .append(", errors: ").append(m.get("errors")).append(".\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        HEADLINE.forEach((k, label) -> sb.append("| ").append(label).append(" | ").append(fmt(k, get(m, k))).append(" |\n"));
        sb.append("\nBefore rule overrides (router counterfactual): category accuracy ")
                .append(fmt("x", get(m, "beforeRuleOverrides.categoryAccuracy"))).append(", P1 recall ")
                .append(fmt("x", get(m, "beforeRuleOverrides.p1Recall"))).append(", P1/P2 kept ≥ P2 ")
                .append(fmt("x", get(m, "beforeRuleOverrides.p1p2RecallNoLowerThanP2"))).append(".  \n");
        sb.append("Drafts violating policy before the critic: ").append(fmt("x", m.get("policyViolationRateBeforeCritic"))).append(".\n\n");

        sb.append("## Category confusion (rows = expected)\n\n| | ");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Long>> conf = (Map<String, Map<String, Long>>) m.get("confusion");
        conf.keySet().forEach(k -> sb.append(abbr(k)).append(" | "));
        sb.append("\n|---|").append("---|".repeat(conf.size())).append('\n');
        conf.forEach((exp, row) -> {
            sb.append("| ").append(abbr(exp)).append(" | ");
            row.values().forEach(v -> sb.append(v == 0 ? "·" : v).append(" | "));
            sb.append('\n');
        });

        sb.append("\n## Confidence calibration\n\n| Confidence | Cases | Category accuracy |\n|---|---|---|\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bins = (List<Map<String, Object>>) m.get("confidenceBins");
        bins.forEach(b -> sb.append("| ").append(b.get("from")).append("–").append(b.get("to")).append(" | ")
                .append(b.get("cases")).append(" | ").append(fmt("x", b.get("categoryAccuracy"))).append(" |\n"));

        sb.append("\n## Cases\n\n| Case | Slice | Expected | Actual | Human (exp/act) | Cited | Latency |\n|---|---|---|---|---|---|---|\n");
        for (CaseResult c : cases) {
            if (c.error() != null) {
                sb.append("| ").append(c.caseId()).append(" | ").append(c.slice()).append(" | ")
                        .append(c.expectedText("category")).append(' ').append(c.expectedText("priority"))
                        .append(" | ERROR: ").append(c.error().replace("|", "/")).append(" | | | |\n");
                continue;
            }
            var a = c.actual();
            sb.append("| ").append(c.correct() ? "" : "**").append(c.caseId()).append(c.correct() ? "" : "**")
                    .append(" | ").append(c.slice()).append(" | ")
                    .append(c.expectedText("category")).append(' ').append(c.expectedText("priority")).append(" | ")
                    .append(a.category()).append(' ').append(a.priority())
                    .append(c.result().ruleOverride() != null ? " (rule)" : "").append(" | ")
                    .append(c.expected().path("needsHuman").asBoolean()).append(" / ").append(a.needsHuman()).append(" | ")
                    .append(String.join(", ", a.citedArticles())).append(" | ")
                    .append(String.format("%.1f s", c.latencyMs() / 1000.0)).append(" |\n");
        }
        return sb.toString();
    }

    /** One column of the ablation table: a stored run, or a counterfactual derived from the full run. */
    public record Column(String label, String model, Long runId, Map<String, Object> metrics, boolean derived) {}

    public String writeAblation(List<EvalRunner.RunOutcome> runs, List<EvalConfig> configs) {
        List<Column> cols = new java.util.ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            cols.add(new Column(configs.get(i).label(), configs.get(i).model(), runs.get(i).runId(), runs.get(i).metrics(), false));
        }
        return writeAblation(cols);
    }

    /**
     * Writes the ablation table. After a "full" column, adds no-router* and no-validation* columns derived from
     * that run's pre-override and first-attempt answers (only the metrics that can be derived exactly).
     */
    public String writeAblation(List<Column> columns) {
        List<Column> cols = new java.util.ArrayList<>();
        for (Column c : columns) {
            cols.add(c);
            if (!c.derived() && c.label().equals("full") && columns.stream().noneMatch(o -> o.label().startsWith("no-router"))) {
                // derived columns go right after the full run they come from
                cols.add(new Column("no-router*", c.model(), c.runId(), noRouter(c.metrics()), true));
            }
            if (!c.derived() && c.label().equals("full") && columns.stream().noneMatch(o -> o.label().startsWith("no-validation"))) {
                cols.add(new Column("no-validation*", c.model(), c.runId(), noValidation(c.metrics()), true));
            }
        }
        StringBuilder sb = new StringBuilder("# Ablation\n\n| Metric |");
        cols.forEach(c -> sb.append(' ').append(c.label()).append(" |"));
        sb.append("\n|---|").append("---|".repeat(cols.size())).append('\n');
        sb.append("| Cases |");
        cols.forEach(c -> sb.append(' ').append(fmt("cases", c.metrics().get("cases"))).append(" |"));
        sb.append('\n');
        HEADLINE.forEach((k, label) -> {
            sb.append("| ").append(label).append(" |");
            cols.forEach(c -> sb.append(' ').append(fmt(k, get(c.metrics(), k))).append(" |"));
            sb.append('\n');
        });
        sb.append("\nColumns: ");
        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            sb.append(i == 0 ? "" : ", ").append(c.label()).append(" = `").append(c.model()).append('`')
                    .append(c.derived() ? " (derived from run " : " (run ").append(c.runId()).append(')');
        }
        sb.append(".\n");
        if (cols.stream().anyMatch(Column::derived)) {
            sb.append("\n\\* Derived from the full run instead of re-running: no-router* uses the classifier's answer before rule ")
                    .append("overrides; no-validation* uses each step's first attempt (no retries, no order check). ")
                    .append("Metrics that cannot be derived exactly are shown as –. ")
                    .append("full@N recomputes the full run's metrics on exactly the N cases the subset runs used.\n");
        }
        Path file = dir.resolve(LocalDateTime.now().format(TS) + "-ablation.md");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file.toString();
    }

    static Map<String, Object> noRouter(Map<String, Object> full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cases", full.get("cases"));
        for (String k : List.of("categoryAccuracy", "categoryMacroF1", "priorityExact", "p1Recall", "p1p2RecallNoLowerThanP2")) {
            m.put(k, get(full, "beforeRuleOverrides." + k));
        }
        // The router does not touch extraction.
        for (String k : List.of("entities", "hallucinatedEntitiesFirstAttempt", "hallucinatedEntitiesFinal")) {
            m.put(k, full.get(k));
        }
        return m;
    }

    static Map<String, Object> noValidation(Map<String, Object> full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cases", full.get("cases"));
        m.put("entities", full.get("entitiesFirstAttempt"));
        m.put("hallucinatedEntitiesFirstAttempt", full.get("hallucinatedEntitiesFirstAttempt"));
        m.put("hallucinatedEntitiesFinal", full.get("hallucinatedEntitiesFirstAttempt"));
        m.put("schemaValidFirstAttempt", full.get("schemaValidFirstAttempt"));
        m.put("schemaValidAfterRetries", full.get("schemaValidFirstAttempt"));
        m.put("llmCallsPerCase", full.get("llmCallsPerCaseWithoutRetries"));
        return m;
    }

    @SuppressWarnings("unchecked")
    static Object get(Map<String, Object> m, String dotted) {
        Object cur = m;
        for (String part : dotted.split("\\.")) {
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(part);
        }
        return cur;
    }

    static String fmt(String key, Object v) {
        if (v == null || "n/a".equals(v)) {
            return "–";
        }
        if (key.startsWith("latency") && v instanceof Number n) {
            return String.format("%.1f", n.doubleValue() / 1000.0);
        }
        if (key.equals("cases")) {
            return v.toString();
        }
        if (key.equals("llmCallsPerCase") && v instanceof Number n) {
            return String.format("%.1f", n.doubleValue());
        }
        if (v instanceof Double d) {
            return String.format("%.0f%%", d * 100);
        }
        return v.toString();
    }

    private static String abbr(String category) {
        return switch (category) {
            case "RETURN_REFUND" -> "RET";
            case "TECHNICAL" -> "TECH";
            case "SHIPPING" -> "SHIP";
            case "BILLING" -> "BILL";
            case "ACCOUNT" -> "ACC";
            default -> category;
        };
    }

    private static String onOff(boolean b) {
        return b ? "on" : "off";
    }
}
