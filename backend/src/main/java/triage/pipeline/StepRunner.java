package triage.pipeline;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Runs one LLM step: call → Bean Validation → business rules. On failure the errors go back
 * into the prompt as feedback and the step is retried, up to {@code maxRetries} times.
 * Every attempt lands in the trace. Infrastructure failures (Ollama down) are not retried.
 */
@Component
public class StepRunner {

    @FunctionalInterface
    public interface Attempt<T> {
        T call(String feedback);
    }

    /** {@code value} is null when the step failed; {@code lastValue} keeps the last parsed answer anyway. */
    public record Outcome<T>(T value, T lastValue, boolean succeeded, int attempts, boolean firstAttemptValid,
                             T firstValue, List<String> lastErrors) {}

    public static class InfrastructureException extends RuntimeException {
        public InfrastructureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Validator validator;

    public StepRunner(Validator validator) {
        this.validator = validator;
    }

    public <T> Outcome<T> run(String step, Trace trace, Attempt<T> call, Function<T, List<String>> businessRules,
                              Function<T, Object> traceOutput, Function<T, List<StepTrace.ToolCall>> toolCalls,
                              boolean validate, int maxRetries) {
        int maxAttempts = validate ? 1 + maxRetries : 1;
        String feedback = "";
        T last = null;
        T first = null;
        boolean firstValid = false;
        List<String> errors = List.of();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long t0 = System.nanoTime();
            T value = null;
            try {
                value = call.call(feedback);
                errors = validate ? validate(value, businessRules) : List.of();
            } catch (RuntimeException e) {
                if (isInfrastructure(e)) {
                    trace.add(new StepTrace(step, attempt, ms(t0), StepTrace.Status.ERROR, List.of(describe(e)), null, List.of(), "infrastructure"));
                    throw new InfrastructureException(step + ": " + describe(e), e);
                }
                errors = List.of(describe(e));
            } finally {
                trace.llmCall();
            }
            if (attempt == 1) {
                first = value;
                firstValid = errors.isEmpty();
            }
            StepTrace.Status status = errors.isEmpty() ? StepTrace.Status.OK
                    : value == null ? StepTrace.Status.ERROR : StepTrace.Status.INVALID;
            trace.add(new StepTrace(step, attempt, ms(t0), status, errors,
                    value == null ? null : traceOutput.apply(value),
                    value == null ? List.of() : toolCalls.apply(value), null));
            if (value != null) {
                last = value;
            }
            if (errors.isEmpty()) {
                return new Outcome<>(value, value, true, attempt, firstValid, first, List.of());
            }
            feedback = feedback(errors);
        }
        trace.add(new StepTrace(step, maxAttempts, 0, StepTrace.Status.FAILED, errors, null, List.of(),
                validate ? "retries exhausted" : "no retries (validation off)"));
        return new Outcome<>(null, last, false, maxAttempts, firstValid, first, errors);
    }

    public <T> Outcome<T> run(String step, Trace trace, Attempt<T> call, Function<T, List<String>> businessRules,
                              boolean validate, int maxRetries) {
        return run(step, trace, call, businessRules, v -> v, v -> List.of(), validate, maxRetries);
    }

    private <T> List<String> validate(T value, Function<T, List<String>> businessRules) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            errors.add("empty answer");
            return errors;
        }
        for (ConstraintViolation<T> v : validator.validate(value)) {
            errors.add(v.getPropertyPath() + ": " + v.getMessage() + " (got " + v.getInvalidValue() + ")");
        }
        errors.sort(null);
        errors.addAll(businessRules.apply(value));
        return errors;
    }

    static String feedback(List<String> errors) {
        StringBuilder sb = new StringBuilder("Your previous answer was rejected:\n");
        errors.forEach(e -> sb.append("- ").append(e).append('\n'));
        sb.append("Answer again with corrected JSON.");
        return sb.toString();
    }

    static boolean isInfrastructure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpException || t instanceof TimeoutException || t instanceof InternalServerException
                    || t instanceof ModelNotFoundException || t instanceof AuthenticationException
                    || t instanceof UnresolvedModelServerException || t instanceof ConnectException
                    || t instanceof java.net.http.HttpTimeoutException
                    || (t instanceof IOException && !(t instanceof com.fasterxml.jackson.core.JacksonException))) {
                return true;
            }
        }
        return false;
    }

    static String describe(Throwable e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        msg = msg.replaceAll("\\s+", " ");
        return msg.length() > 400 ? msg.substring(0, 400) + "…" : msg;
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }
}
