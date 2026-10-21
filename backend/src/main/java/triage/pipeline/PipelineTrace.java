package triage.pipeline;

import java.util.List;

public record PipelineTrace(List<StepTrace> steps, long totalMs, int llmCalls, int retries) {}
