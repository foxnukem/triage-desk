package triage.eval;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import triage.persistence.EvalCaseResultEntity;
import triage.persistence.EvalCaseResultRepository;
import triage.persistence.EvalRunEntity;
import triage.persistence.EvalRunRepository;
import triage.pipeline.StepRunner;
import triage.pipeline.TriagePipeline;
import triage.pipeline.TriageResult;

/** Runs the pipeline over the labelled dataset, scores every case, stores the run and writes reports. */
@Service
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    public record RunOutcome(long runId, Map<String, Object> metrics, List<CaseResult> cases, List<String> reportFiles) {}

    private final TriagePipeline pipeline;
    private final EvalDataset dataset;
    private final EvalScorer scorer;
    private final EvalRunRepository runs;
    private final EvalCaseResultRepository caseResults;
    private final ReportWriter reports;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public EvalRunner(TriagePipeline pipeline, EvalDataset dataset, EvalScorer scorer, EvalRunRepository runs,
                      EvalCaseResultRepository caseResults, ReportWriter reports, TransactionTemplate tx, ObjectMapper mapper) {
        this.pipeline = pipeline;
        this.dataset = dataset;
        this.scorer = scorer;
        this.runs = runs;
        this.caseResults = caseResults;
        this.reports = reports;
        this.tx = tx;
        this.mapper = mapper;
    }

    public List<EvalCase> select(EvalConfig cfg) {
        var stream = dataset.all().stream();
        if (cfg.slice() != null && !cfg.slice().isBlank()) {
            stream = stream.filter(c -> c.slice().equals(cfg.slice()));
        }
        if (cfg.caseIds() != null && !cfg.caseIds().isEmpty()) {
            stream = stream.filter(c -> cfg.caseIds().contains(c.id()));
        }
        if (cfg.limit() != null && cfg.limit() > 0) {
            stream = stream.limit(cfg.limit());
        }
        return stream.toList();
    }

    /** Creates the eval_run row; {@link #execute} fills it. Split so the API can return the id at once. */
    public long start(EvalConfig cfg) {
        EvalConfig resolved = resolve(cfg);
        int total = select(resolved).size();
        return runs.save(new EvalRunEntity(resolved.label(), json(resolved), total)).getId();
    }

    public RunOutcome run(EvalConfig cfg) {
        return execute(start(cfg), cfg);
    }

    public RunOutcome execute(long runId, EvalConfig cfg) {
        EvalConfig resolved = resolve(cfg);
        List<EvalCase> cases = select(resolved);
        List<CaseResult> results = new ArrayList<>();
        try {
            int i = 0;
            for (EvalCase c : cases) {
                i++;
                long t0 = System.nanoTime();
                CaseResult cr;
                try {
                    TriageResult r = pipeline.run(c.text(), resolved.options());
                    cr = scorer.score(c, r, r.trace().totalMs());
                } catch (StepRunner.InfrastructureException e) {
                    throw e;
                } catch (RuntimeException e) {
                    log.warn("case {} failed", c.id(), e);
                    cr = scorer.failed(c, e.getMessage(), (System.nanoTime() - t0) / 1_000_000);
                }
                results.add(cr);
                CaseResult saved = cr;
                int done = i;
                tx.executeWithoutResult(s -> {
                    caseResults.save(new EvalCaseResultEntity(runId, saved.caseId(), saved.slice(), saved.correct(),
                            saved.latencyMs(), json(saved)));
                    runs.findById(runId).ifPresent(run -> run.setDoneCases(done));
                });
                log.info("[{}] {}/{} {} {} {}ms", resolved.label(), i, cases.size(), c.id(),
                        cr.error() != null ? "ERROR" : cr.correct() ? "ok" : "miss", cr.latencyMs());
            }
            Map<String, Object> metrics = EvalMetrics.compute(results);
            List<String> files = reports.write(runId, resolved, metrics, results);
            tx.executeWithoutResult(s -> runs.findById(runId).ifPresent(run -> {
                run.setMetrics(json(metrics));
                run.setStatus("DONE");
                run.setFinishedAt(OffsetDateTime.now());
            }));
            return new RunOutcome(runId, metrics, results, files);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(s -> runs.findById(runId).ifPresent(run -> {
                run.setStatus("FAILED");
                run.setError(e.getMessage());
                run.setFinishedAt(OffsetDateTime.now());
            }));
            throw e;
        }
    }

    private EvalConfig resolve(EvalConfig cfg) {
        String model = cfg.model() == null || cfg.model().isBlank() ? pipeline.defaultOptions().model() : cfg.model();
        String label = cfg.label() == null || cfg.label().isBlank() ? "custom" : cfg.label();
        return new EvalConfig(label, model, cfg.ruleRouter(), cfg.validation(), cfg.critic(), cfg.megaPrompt(),
                cfg.reply(), cfg.limit(), cfg.slice(), cfg.caseIds(), cfg.classifier());
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
