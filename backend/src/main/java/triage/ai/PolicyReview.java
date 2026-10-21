package triage.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.NotNull;

public record PolicyReview(
        @JsonProperty(required = true) @Description("true if the draft breaks no policy rule")
        boolean compliant,
        @JsonProperty(required = true) @Description("each broken rule in a few words; empty if compliant")
        @NotNull List<String> violations,
        @JsonProperty(required = true) @Description("true if the customer demands something the policy does not offer")
        boolean customerAsksOutOfPolicy
) {}
