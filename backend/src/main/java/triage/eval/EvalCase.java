package triage.eval;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** One row of eval/tickets.jsonl. An entity key absent from {@code expected} is not scored. */
public record EvalCase(String id, String slice, String lang, List<String> tags, String text, JsonNode expected, Integer demo) {

    public String expectedText(String field) {
        JsonNode n = expected.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    public boolean expects(String field) {
        return expected.has(field);
    }
}
