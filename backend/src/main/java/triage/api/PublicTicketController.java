package triage.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import triage.persistence.TicketEntity;
import triage.persistence.TicketSource;
import triage.tickets.TicketService;

/** Customer widget API. Exposes only "received / in progress / resolved". */
@RestController
@RequestMapping("/api/public/tickets")
public class PublicTicketController {

    public record CreateTicketRequest(
            @Email @Size(max = 255) String email,
            @Pattern(regexp = "^$|(?i)(GK-)?\\d{6}", message = "must look like GK-123456") String orderNumber,
            @NotBlank @Size(max = 2000) String text) {}

    public record TicketStatusResponse(long id, String status, String updatedAt) {}

    private final TicketService tickets;

    public PublicTicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(@Valid @RequestBody CreateTicketRequest req) {
        TicketEntity t = tickets.create(req.text(), req.email(), req.orderNumber(), TicketSource.WIDGET);
        return Map.of("id", t.getId(), "status", TicketService.customerStatus(t.getStatus()));
    }

    @GetMapping("/{id}/status")
    public TicketStatusResponse status(@PathVariable long id) {
        TicketEntity t = tickets.get(id);
        return new TicketStatusResponse(t.getId(), TicketService.customerStatus(t.getStatus()), t.getUpdatedAt().toString());
    }
}
