package triage.tickets;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(long id) {
        super("ticket " + id + " not found");
    }
}
