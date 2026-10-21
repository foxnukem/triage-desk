package triage.pipeline;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import triage.ai.ReplyDraft;

/** ReplyAgent output plus what its tools returned in this request (for the citation check). */
public record AgentReply(
        @Valid @NotNull ReplyDraft draft,
        List<StepTrace.ToolCall> toolCalls,
        Set<String> retrieved,
        boolean searchCalled
) {}
