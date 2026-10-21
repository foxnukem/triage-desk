package triage.pipeline;

import java.util.List;

public record StepTrace(
        String name,
        int attempt,
        long durationMs,
        Status status,
        List<String> errors,
        Object output,
        List<ToolCall> toolCalls,
        String note
) {

    public enum Status { OK, INVALID, ERROR, FAILED, OVERRIDE, INFO, SKIPPED }

    public record ToolCall(String name, String arguments, String result, long durationMs) {}

    public static StepTrace info(String name, Status status, long durationMs, Object output, String note) {
        return new StepTrace(name, 1, durationMs, status, List.of(), output, List.of(), note);
    }
}
