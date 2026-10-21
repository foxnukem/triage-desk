package triage.pipeline;

import java.util.ArrayList;
import java.util.List;

/** Mutable collector for one pipeline run. */
public class Trace {

    private final long startNanos = System.nanoTime();
    private final List<StepTrace> steps = new ArrayList<>();
    private int llmCalls;

    public synchronized void add(StepTrace step) {
        steps.add(step);
    }

    public synchronized void llmCall() {
        llmCalls++;
    }

    public synchronized PipelineTrace finish() {
        int retries = (int) steps.stream().filter(s -> s.attempt() > 1 && s.status() != StepTrace.Status.FAILED).count();
        return new PipelineTrace(List.copyOf(steps), (System.nanoTime() - startNanos) / 1_000_000, llmCalls, retries);
    }
}
