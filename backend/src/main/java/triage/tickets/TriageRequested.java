package triage.tickets;

/** {@code model} / {@code classifier} null means the defaults (classifier: the ticket's A/B arm). */
public record TriageRequested(long ticketId, String model, String classifier) {}
