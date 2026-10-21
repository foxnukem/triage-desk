package triage.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import triage.pipeline.StepTrace.Status;

class StepRunnerTest {

    record Answer(@NotBlank String text) {}

    private final StepRunner runner = new StepRunner(Validation.buildDefaultValidatorFactory().getValidator());
    private final Trace trace = new Trace();
    private final List<String> feedbacks = new ArrayList<>();

    private StepRunner.Attempt<Answer> scripted(Answer... answers) {
        Iterator<Answer> it = List.of(answers).iterator();
        return fb -> {
            feedbacks.add(fb);
            return it.next();
        };
    }

    /** Business rule: the answer must not mention a made-up order. */
    private static List<String> noInventedOrder(Answer a) {
        return a.text().contains("GK-999999") ? List.of("orderId GK-999999 does not appear in the ticket") : List.of();
    }

    @Test
    void invalidTwiceThenValidTakesThreeAttempts() {
        var out = runner.run("step", trace, scripted(new Answer(""), new Answer("GK-999999"), new Answer("ok")),
                StepRunnerTest::noInventedOrder, true, 2);

        assertThat(out.succeeded()).isTrue();
        assertThat(out.attempts()).isEqualTo(3);
        assertThat(out.firstAttemptValid()).isFalse();
        assertThat(out.value().text()).isEqualTo("ok");
        // Errors of each attempt are fed back into the next prompt.
        assertThat(feedbacks.get(0)).isEmpty();
        assertThat(feedbacks.get(1)).contains("text:").contains("rejected");
        assertThat(feedbacks.get(2)).contains("GK-999999");

        PipelineTrace t = trace.finish();
        assertThat(t.steps()).extracting(StepTrace::status).containsExactly(Status.INVALID, Status.INVALID, Status.OK);
        assertThat(t.llmCalls()).isEqualTo(3);
    }

    @Test
    void invalidThreeTimesFails() {
        var out = runner.run("step", trace, scripted(new Answer("GK-999999"), new Answer("GK-999999"), new Answer("GK-999999")),
                StepRunnerTest::noInventedOrder, true, 2);

        assertThat(out.succeeded()).isFalse();
        assertThat(out.value()).isNull();
        assertThat(out.lastValue().text()).isEqualTo("GK-999999");
        assertThat(out.lastErrors()).singleElement().asString().contains("does not appear");
        assertThat(trace.finish().steps()).extracting(StepTrace::status)
                .containsExactly(Status.INVALID, Status.INVALID, Status.INVALID, Status.FAILED);
    }

    @Test
    void validationOffMakesOneCallAndAcceptsAnything() {
        var out = runner.run("step", trace, scripted(new Answer("")), StepRunnerTest::noInventedOrder, false, 2);

        assertThat(out.succeeded()).isTrue();
        assertThat(out.attempts()).isEqualTo(1);
        assertThat(out.value().text()).isEmpty();
    }

    @Test
    void unparseableAnswerIsRetried() {
        Iterator<Object> script = List.<Object>of(new IllegalStateException("Cannot parse JSON"), new Answer("ok")).iterator();
        var out = runner.run("step", trace, fb -> {
            Object next = script.next();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (Answer) next;
        }, a -> List.of(), true, 2);

        assertThat(out.succeeded()).isTrue();
        assertThat(trace.finish().steps()).extracting(StepTrace::status).containsExactly(Status.ERROR, Status.OK);
    }

    @Test
    void infrastructureFailureIsNotRetried() {
        StepRunner.Attempt<Answer> down = fb -> {
            feedbacks.add(fb);
            throw new RuntimeException("Ollama unreachable", new ConnectException("Connection refused"));
        };

        assertThatThrownBy(() -> runner.run("classify", trace, down, a -> List.of(), true, 2))
                .isInstanceOf(StepRunner.InfrastructureException.class)
                .hasMessageContaining("classify");
        assertThat(feedbacks).hasSize(1);
    }
}
