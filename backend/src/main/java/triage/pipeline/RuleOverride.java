package triage.pipeline;

import triage.ai.Category;
import triage.ai.Priority;

/** What a rule changed relative to the LLM classifier's answer. */
public record RuleOverride(
        String ruleId,
        String reason,
        String matched,
        Category modelCategory,
        Priority modelPriority,
        boolean categoryChanged,
        boolean priorityRaised
) {}
