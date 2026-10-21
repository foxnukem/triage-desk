package triage.pipeline;

import java.util.List;
import java.util.Map;

import triage.ai.Extraction;
import triage.ai.ReplyDraft;

/** Eval-facing facts about the run, kept next to the result so ablations can be computed post hoc. */
public record Diagnostics(
        Map<String, Boolean> firstAttemptValid,
        Map<String, Boolean> stepSucceeded,
        Extraction firstExtraction,
        int entitiesFirstAttempt,
        int ungroundedFirstAttempt,
        int ungroundedFinal,
        String orderCheck,
        ReplyDraft draftBeforeCritic,
        List<String> policyLintBeforeCritic,
        List<String> policyLintFinal,
        List<String> retrievedArticles
) {}
