package triage.tickets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import triage.pipeline.TriagePipeline;
import triage.pipeline.TriageResult;
import triage.persistence.TicketEntity;
import triage.persistence.TicketRepository;
import triage.persistence.TicketStatus;
import triage.persistence.TriageResultEntity;
import triage.persistence.TriageResultRepository;

/** Runs the pipeline off the request thread; the LLM call is outside any DB transaction. */
@Component
public class TriageWorker {

    private static final Logger log = LoggerFactory.getLogger(TriageWorker.class);

    private final TriagePipeline pipeline;
    private final TicketRepository tickets;
    private final TriageResultRepository results;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public TriageWorker(TriagePipeline pipeline, TicketRepository tickets, TriageResultRepository results,
                        TransactionTemplate tx, ObjectMapper mapper) {
        this.pipeline = pipeline;
        this.tickets = tickets;
        this.results = results;
        this.tx = tx;
        this.mapper = mapper;
    }

    @Async("triageExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTriageRequested(TriageRequested event) {
        process(event.ticketId(), event.model(), event.classifier());
    }

    public void process(long ticketId, String model, String classifier) {
        TicketEntity ticket = tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        try {
            var options = pipeline.defaultOptions();
            if (model != null && !model.isBlank()) {
                options = options.withModel(model);
            }
            options = options.withClassifier(classifier == null || classifier.isBlank()
                    ? pipeline.assignClassifier(ticketId) : classifier);
            TriageResult result = pipeline.run(ticket.getText(), options);
            String json = mapper.writeValueAsString(result);
            tx.executeWithoutResult(s -> {
                var c = result.classification();
                TriageResultEntity saved = results.save(new TriageResultEntity(ticketId, c.category().name(),
                        c.priority().name(), result.queue(), result.needsHuman(), result.model(), json));
                TicketEntity t = tickets.findById(ticketId).orElseThrow();
                t.setCurrentResultId(saved.getId());
                t.setTriageError(null);
                if (t.getStatus() == TicketStatus.NEW || t.getStatus() == TicketStatus.TRIAGED
                        || t.getStatus() == TicketStatus.IN_REVIEW) {
                    t.setStatus(result.needsHuman() ? TicketStatus.IN_REVIEW : TicketStatus.TRIAGED);
                }
            });
            log.info("ticket {} triaged in {} ms: {} {} needsHuman={}", ticketId, result.trace().totalMs(),
                    result.classification().category(), result.classification().priority(), result.needsHuman());
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("triage of ticket {} failed: {}", ticketId, e.getMessage());
            tx.executeWithoutResult(s -> tickets.findById(ticketId).ifPresent(t -> t.setTriageError(e.getMessage())));
        }
    }
}
