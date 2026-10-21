package triage.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import triage.persistence.DecisionAction;
import triage.persistence.TicketEntity;
import triage.persistence.TicketSource;
import triage.persistence.TicketStatus;
import triage.tickets.TicketService;

/** Agent panel API (no auth: documented limitation). */
@RestController
@RequestMapping("/api")
public class TicketController {

    public record DecisionRequest(@NotNull DecisionAction action, @Size(max = 4000) String finalText, @Size(max = 1000) String note) {}

    public record RetriageRequest(String model, String classifier) {}

    public record OperatorTicketRequest(@NotBlank @Size(max = 2000) String text, String email) {}

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/tickets")
    public TicketService.Page<TicketService.TicketRow> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Boolean needsHuman,
            @RequestParam(defaultValue = "priority") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return tickets.list(new TicketService.TicketFilter(status, blank(category), blank(priority), needsHuman, sort,
                Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
    }

    /** An agent files a ticket on the customer's behalf (phone, email). */
    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(@Valid @RequestBody OperatorTicketRequest req) {
        TicketEntity t = tickets.create(req.text(), req.email(), null, TicketSource.OPERATOR);
        return Map.of("id", t.getId(), "status", t.getStatus().name());
    }

    @GetMapping("/tickets/{id}")
    public TicketService.TicketDetail get(@PathVariable long id) {
        return tickets.detail(id);
    }

    @PostMapping("/tickets/{id}/retriage")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> retriage(@PathVariable long id, @RequestBody(required = false) RetriageRequest req) {
        tickets.retriage(id, req == null ? null : req.model(), req == null ? null : req.classifier());
        return Map.of("id", id, "queued", true);
    }

    @PostMapping("/tickets/{id}/decision")
    public TicketService.TicketDetail decide(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        return tickets.decide(id, req.action(), req.finalText(), req.note());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return tickets.stats();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
