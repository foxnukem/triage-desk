package triage.eval;

import java.time.OffsetDateTime;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import triage.persistence.EvalRunRepository;

/** Eval runs started from the UI execute in the background, one at a time. */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final EvalRunner runner;
    private final EvalRunRepository runs;
    private final Executor executor;

    public EvalService(EvalRunner runner, EvalRunRepository runs, @Qualifier("evalExecutor") Executor executor) {
        this.runner = runner;
        this.runs = runs;
        this.executor = executor;
    }

    public long start(EvalConfig cfg) {
        long id = runner.start(cfg);
        executor.execute(() -> {
            try {
                runner.execute(id, cfg);
            } catch (RuntimeException e) {
                log.warn("eval run {} failed: {}", id, e.getMessage());
            }
        });
        return id;
    }

    @org.springframework.beans.factory.annotation.Value("${triage.eval.mark-interrupted:true}")
    private boolean markInterrupted;

    /**
     * Runs left RUNNING by a previous web process will never finish. The eval CLI turns this off
     * ({@code triage.eval.mark-interrupted=false}): a run may be in progress in another process.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markInterrupted() {
        if (!markInterrupted) {
            return;
        }
        runs.findAll().stream().filter(r -> "RUNNING".equals(r.getStatus())).forEach(r -> {
            r.setStatus("FAILED");
            r.setError("interrupted (application restarted)");
            r.setFinishedAt(OffsetDateTime.now());
        });
    }
}
