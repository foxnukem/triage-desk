package triage.pipeline;

import triage.ai.Classification;
import triage.ai.Extraction;
import triage.ai.ReplyDraft;

public record TriageResult(
        Classification classification,
        Extraction extraction,
        ReplyDraft reply,
        String queue,
        boolean needsHuman,
        String escalationReason,
        RuleOverride ruleOverride,
        String model,
        PipelineOptions options,
        Diagnostics diagnostics,
        PipelineTrace trace
) {}
