package triage.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import triage.TriageDeskApplication;

/**
 * Command-line eval: {@code ./mvnw -pl backend compile exec:java -Deval.model=qwen3:8b}.
 * <ul>
 *   <li>{@code -Deval.config=full} (default), a comma list of presets, or {@code ablation} for all six</li>
 *   <li>{@code -Deval.compareModel=qwen2.5:7b-instruct} for the compare-model preset</li>
 *   <li>{@code -Deval.label=demo}: run label (suffix when several presets run)</li>
 *   <li>{@code -Deval.table=12,13,14}: only write the ablation table from stored runs (no LLM calls)</li>
 *   <li>{@code -Deval.limit=10}, {@code -Deval.slice=hard}, {@code -Deval.cases=t011,t034}, {@code -Deval.reply=false}</li>
 * </ul>
 */
public final class EvalCli {

    private EvalCli() {}

    public static void main(String[] args) {
        String model = System.getProperty("eval.model", "qwen3:8b");
        String compareModel = System.getProperty("eval.compareModel", "qwen2.5:7b-instruct");
        String configArg = System.getProperty("eval.config", "full");
        Integer limit = Integer.getInteger("eval.limit");
        String slice = System.getProperty("eval.slice");
        String casesArg = System.getProperty("eval.cases");
        List<String> caseIds = casesArg == null ? null : Arrays.stream(casesArg.split(",")).map(String::strip).toList();
        boolean reply = Boolean.parseBoolean(System.getProperty("eval.reply", "true"));
        String label = System.getProperty("eval.label");

        List<String> presets = configArg.equals("ablation") ? EvalConfig.ABLATION
                : Arrays.stream(configArg.split(",")).map(String::strip).toList();
        List<EvalConfig> configs = presets.stream()
                .map(p -> EvalConfig.preset(p, model, compareModel).withCases(limit, slice, caseIds).withReply(reply))
                .map(c -> label == null ? c : c.withLabel(presets.size() == 1 ? label : c.label() + "-" + label))
                .toList();

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TriageDeskApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "triage.eval.mark-interrupted=false")
                .run(args);
        int exit = 0;
        try {
            ReportWriter writer = ctx.getBean(ReportWriter.class);
            String table = System.getProperty("eval.table");
            if (table != null) {
                System.out.println("Ablation table: " + writer.writeAblation(storedColumns(ctx, table)));
                return;
            }
            EvalRunner runner = ctx.getBean(EvalRunner.class);
            List<EvalRunner.RunOutcome> outcomes = new ArrayList<>();
            for (EvalConfig cfg : configs) {
                EvalRunner.RunOutcome out = runner.run(cfg);
                outcomes.add(out);
                System.out.printf("%n== %s (run %d): category %s, priority %s, p50 %s ms -> %s%n", cfg.label(), out.runId(),
                        ReportWriter.fmt("x", out.metrics().get("categoryAccuracy")),
                        ReportWriter.fmt("x", out.metrics().get("priorityExact")),
                        out.metrics().get("latencyP50Ms"), out.reportFiles());
            }
            if (configs.size() > 1) {
                System.out.println("Ablation table: " + writer.writeAblation(outcomes, configs));
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            exit = 1;
        } finally {
            ctx.close();
        }
        System.exit(exit);
    }

    private static List<ReportWriter.Column> storedColumns(ConfigurableApplicationContext ctx, String ids) {
        var runs = ctx.getBean(triage.persistence.EvalRunRepository.class);
        var mapper = ctx.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        var caseRepo = ctx.getBean(triage.persistence.EvalCaseResultRepository.class);
        List<ReportWriter.Column> cols = new ArrayList<>();
        java.util.Map<Long, List<CaseResult>> casesByRun = new java.util.LinkedHashMap<>();
        for (String id : ids.split(",")) {
            var run = runs.findById(Long.parseLong(id.strip())).orElseThrow(() -> new IllegalArgumentException("no run " + id));
            if (run.getMetrics() == null) {
                throw new IllegalArgumentException("run " + id + " has no metrics (status " + run.getStatus() + ")");
            }
            try {
                EvalConfig cfg = mapper.readValue(run.getConfig(), EvalConfig.class);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> metrics = mapper.readValue(run.getMetrics(), java.util.Map.class);
                cols.add(new ReportWriter.Column(cfg.label(), cfg.model(), run.getId(), metrics, false));
                List<CaseResult> cases = new ArrayList<>();
                for (var c : caseRepo.findByRunIdOrderByCaseIdAsc(run.getId())) {
                    cases.add(mapper.readValue(c.getDetail(), CaseResult.class));
                }
                casesByRun.put(run.getId(), cases);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return withSubsetBaseline(cols, casesByRun);
    }

    /**
     * Runs on a subset are not comparable with a full run on all cases, so for each distinct subset
     * the full run's metrics are recomputed on exactly those cases ("full@25").
     */
    private static List<ReportWriter.Column> withSubsetBaseline(List<ReportWriter.Column> cols,
                                                                java.util.Map<Long, List<CaseResult>> cases) {
        var full = cols.stream().filter(c -> c.label().equals("full")).findFirst();
        if (full.isEmpty()) {
            return cols;
        }
        List<CaseResult> fullCases = cases.get(full.get().runId());
        List<ReportWriter.Column> out = new ArrayList<>(cols);
        java.util.Set<java.util.Set<String>> done = new java.util.HashSet<>();
        for (var c : cols) {
            java.util.Set<String> ids = new java.util.TreeSet<>(cases.get(c.runId()).stream().map(CaseResult::caseId).toList());
            if (ids.size() >= fullCases.size() || !done.add(ids)) {
                continue;
            }
            List<CaseResult> subset = fullCases.stream().filter(fc -> ids.contains(fc.caseId())).toList();
            int at = out.indexOf(full.get()) + 1;
            while (at < out.size() && out.get(at).derived()) {
                at++;
            }
            out.add(at, new ReportWriter.Column("full@" + subset.size(), full.get().model(), full.get().runId(),
                    EvalMetrics.compute(subset), true));
        }
        return out;
    }
}
