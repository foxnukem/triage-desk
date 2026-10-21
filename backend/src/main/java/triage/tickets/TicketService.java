package triage.tickets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import triage.persistence.DecisionAction;
import triage.persistence.ReplyDecisionEntity;
import triage.persistence.ReplyDecisionRepository;
import triage.persistence.TicketEntity;
import triage.persistence.TicketRepository;
import triage.persistence.TicketSource;
import triage.persistence.TicketStatus;
import triage.persistence.TriageResultEntity;
import triage.persistence.TriageResultRepository;

/** Ticket lifecycle: NEW → (async triage) TRIAGED / IN_REVIEW → agent decision → RESOLVED / ESCALATED. */
@Service
public class TicketService {

    public record TicketFilter(TicketStatus status, String category, String priority, Boolean needsHuman,
                               String sort, int page, int size) {}

    public record TicketRow(long id, String status, String source, String createdAt, String updatedAt, String preview,
                            String customerEmail, String category, String priority, String queue, Boolean needsHuman,
                            boolean ruleOverride, String model, String triageError) {}

    public record Page<T>(List<T> items, long total, int page, int size) {}

    public record HistoryItem(long id, String model, String category, String priority, boolean needsHuman, String createdAt) {}

    public record Decision(long id, Long resultId, String action, String finalText, String note, String decidedAt) {}

    public record TicketDetail(long id, String status, String source, String text, String customerEmail,
                               String createdAt, String updatedAt, String triageError, Long currentResultId,
                               JsonNode result, List<HistoryItem> history, List<Decision> decisions) {}

    private final TicketRepository tickets;
    private final TriageResultRepository results;
    private final ReplyDecisionRepository decisions;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    public TicketService(TicketRepository tickets, TriageResultRepository results, ReplyDecisionRepository decisions,
                         NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events, ObjectMapper mapper) {
        this.tickets = tickets;
        this.results = results;
        this.decisions = decisions;
        this.jdbc = jdbc;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional
    public TicketEntity create(String text, String customerEmail, String orderNumber, TicketSource source) {
        String body = text.strip();
        if (orderNumber != null && !orderNumber.isBlank() && !body.contains(orderNumber.strip())) {
            body = body + "\n\nOrder number: " + orderNumber.strip();
        }
        TicketEntity t = tickets.save(new TicketEntity(body, blankToNull(customerEmail), source));
        events.publishEvent(new TriageRequested(t.getId(), null, null));
        return t;
    }

    @Transactional
    public void retriage(long id, String model, String classifier) {
        TicketEntity t = get(id);
        t.setTriageError(null);
        t.setStatus(TicketStatus.NEW);
        tickets.save(t);
        events.publishEvent(new TriageRequested(t.getId(), model, classifier));
    }

    @Transactional(readOnly = true)
    public TicketEntity get(long id) {
        return tickets.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
    }

    @Transactional
    public TicketDetail decide(long id, DecisionAction action, String finalText, String note) {
        TicketEntity t = get(id);
        Long resultId = t.getCurrentResultId();
        String text = finalText;
        if (action == DecisionAction.ACCEPTED) {
            if (resultId == null) {
                throw new IllegalStateException("ticket has no triage result to accept");
            }
            JsonNode reply = readResult(results.findById(resultId).orElseThrow()).path("reply").path("text");
            if (reply.isMissingNode() || reply.isNull()) {
                throw new IllegalStateException("triage result has no draft reply");
            }
            text = reply.asText();
        }
        if (action == DecisionAction.EDITED && (text == null || text.isBlank())) {
            throw new IllegalArgumentException("finalText is required for EDITED");
        }
        decisions.save(new ReplyDecisionEntity(id, resultId, action, text, note));
        t.setStatus(switch (action) {
            case ACCEPTED, EDITED -> TicketStatus.RESOLVED;
            case REJECTED -> TicketStatus.IN_REVIEW;
            case ESCALATED -> TicketStatus.ESCALATED;
        });
        return detail(id);
    }

    @Transactional(readOnly = true)
    public TicketDetail detail(long id) {
        TicketEntity t = get(id);
        List<TriageResultEntity> history = results.findByTicketIdOrderByIdDesc(id);
        JsonNode current = history.stream().filter(r -> r.getId().equals(t.getCurrentResultId()))
                .findFirst().map(this::readResult).orElse(null);
        return new TicketDetail(t.getId(), t.getStatus().name(), t.getSource().name(), t.getText(), t.getCustomerEmail(),
                t.getCreatedAt().toString(), t.getUpdatedAt().toString(), t.getTriageError(), t.getCurrentResultId(), current,
                history.stream().map(r -> new HistoryItem(r.getId(), r.getModel(), r.getCategory(), r.getPriority(),
                        r.isNeedsHuman(), r.getCreatedAt().toString())).toList(),
                decisions.findByTicketIdOrderByIdAsc(id).stream().map(d -> new Decision(d.getId(), d.getResultId(),
                        d.getAction().name(), d.getFinalText(), d.getNote(), d.getDecidedAt().toString())).toList());
    }

    @Transactional(readOnly = true)
    public Page<TicketRow> list(TicketFilter f) {
        List<String> where = new ArrayList<>(List.of("t.source <> 'EVAL'"));
        var params = new MapSqlParameterSource();
        if (f.status() != null) {
            where.add("t.status = :status");
            params.addValue("status", f.status().name());
        }
        if (f.category() != null) {
            where.add("r.category = :category");
            params.addValue("category", f.category());
        }
        if (f.priority() != null) {
            where.add("r.priority = :priority");
            params.addValue("priority", f.priority());
        }
        if (f.needsHuman() != null) {
            where.add("r.needs_human = :needsHuman");
            params.addValue("needsHuman", f.needsHuman());
        }
        String from = " FROM ticket t LEFT JOIN triage_result r ON r.id = t.current_result_id WHERE " + String.join(" AND ", where);
        String order = switch (f.sort() == null ? "priority" : f.sort()) {
            case "newest" -> "t.created_at DESC";
            case "oldest" -> "t.created_at ASC";
            default -> "r.priority ASC NULLS FIRST, t.created_at ASC";
        };
        params.addValue("limit", f.size()).addValue("offset", (long) f.page() * f.size());
        Long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);
        List<TicketRow> rows = jdbc.query("""
                SELECT t.id, t.status, t.source, t.created_at, t.updated_at, left(t.text, 160) AS preview, t.customer_email,
                       t.triage_error, r.category, r.priority, r.queue, r.needs_human, r.model,
                       coalesce(jsonb_typeof(r.result -> 'ruleOverride') = 'object', false) AS rule_override
                """ + from + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params, (rs, i) -> new TicketRow(
                rs.getLong("id"), rs.getString("status"), rs.getString("source"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toString(),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toString(),
                rs.getString("preview"), rs.getString("customer_email"), rs.getString("category"), rs.getString("priority"),
                rs.getString("queue"), (Boolean) rs.getObject("needs_human"), rs.getBoolean("rule_override"),
                rs.getString("model"), rs.getString("triage_error")));
        return new Page<>(rows, total == null ? 0 : total, f.page(), f.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        jdbc.query("SELECT status, count(*) AS n FROM ticket WHERE source <> 'EVAL' GROUP BY status", rs -> {
            byStatus.put(rs.getString("status"), rs.getLong("n"));
        });
        out.put("byStatus", byStatus);
        long accepted = decisions.countByAction(DecisionAction.ACCEPTED);
        long edited = decisions.countByAction(DecisionAction.EDITED);
        long rejected = decisions.countByAction(DecisionAction.REJECTED);
        long reviewed = accepted + edited + rejected;
        out.put("decisions", Map.of("ACCEPTED", accepted, "EDITED", edited, "REJECTED", rejected,
                "ESCALATED", decisions.countByAction(DecisionAction.ESCALATED)));
        // Live quality signal: drafts sent without edits.
        out.put("acceptedWithoutEdits", reviewed == 0 ? null : (double) accepted / reviewed);
        out.put("arms", arms());
        return out;
    }

    /**
     * Live A/B by classifier arm. Tickets count by their current result; decisions by the result the
     * agent actually judged, so a re-triage after the decision does not move it to the other arm.
     */
    private Map<String, Map<String, Object>> arms() {
        Map<String, Map<String, Object>> arms = new java.util.LinkedHashMap<>();
        java.util.function.Function<String, Map<String, Object>> arm = name -> arms.computeIfAbsent(name, k -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("tickets", 0L);
            m.put("needsHuman", 0L);
            for (DecisionAction a : DecisionAction.values()) {
                m.put(a.name(), 0L);
            }
            return m;
        });
        arm.apply("llm");
        jdbc.query("""
                SELECT coalesce(r.result -> 'options' ->> 'classifier', 'llm') AS arm, count(*) AS n,
                       count(*) FILTER (WHERE r.needs_human) AS human
                FROM ticket t JOIN triage_result r ON r.id = t.current_result_id
                WHERE t.source <> 'EVAL' GROUP BY 1""", rs -> {
            Map<String, Object> m = arm.apply(rs.getString("arm"));
            m.put("tickets", rs.getLong("n"));
            m.put("needsHuman", rs.getLong("human"));
        });
        jdbc.query("""
                SELECT coalesce(r.result -> 'options' ->> 'classifier', 'llm') AS arm, d.action, count(*) AS n
                FROM reply_decision d JOIN triage_result r ON r.id = d.result_id GROUP BY 1, 2""", rs -> {
            arm.apply(rs.getString("arm")).put(rs.getString("action"), rs.getLong("n"));
        });
        arms.values().forEach(m -> {
            long acc = (Long) m.get("ACCEPTED");
            long rev = acc + (Long) m.get("EDITED") + (Long) m.get("REJECTED");
            m.put("acceptedWithoutEdits", rev == 0 ? null : (double) acc / rev);
        });
        return arms;
    }

    /** What the customer sees: no category, no draft. */
    public static String customerStatus(TicketStatus s) {
        return switch (s) {
            case NEW -> "RECEIVED";
            case TRIAGED, IN_REVIEW, ESCALATED -> "IN_PROGRESS";
            case RESOLVED -> "RESOLVED";
        };
    }

    private JsonNode readResult(TriageResultEntity r) {
        try {
            return mapper.readTree(r.getResult());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
