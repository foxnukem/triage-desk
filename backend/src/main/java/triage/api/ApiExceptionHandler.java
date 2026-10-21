package triage.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import triage.pipeline.StepRunner;
import triage.tickets.TicketNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(TicketNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).reduce((a, b) -> a + "; " + b).orElse("invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(StepRunner.InfrastructureException.class)
    ResponseEntity<Map<String, String>> llmDown(StepRunner.InfrastructureException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "LLM unavailable: " + e.getMessage()));
    }
}
