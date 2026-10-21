package triage.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record Classification(
        @JsonProperty(required = true) @NotNull Category category,
        @JsonProperty(required = true) @NotNull Priority priority,
        @JsonProperty(required = true) @Description("one short sentence: the facts that set the priority")
        @NotBlank @Size(max = 200) String priorityReason,
        @JsonProperty(required = true) @NotNull Sentiment sentiment,
        @JsonProperty(required = true) @NotNull Lang language,
        @JsonProperty(required = true) @Description("probability 0..1 that the category is correct")
        @DecimalMin("0") @DecimalMax("1") double confidence,
        @JsonProperty(required = true) @Description("true if the ticket contains more than one separate request")
        boolean multiIntent
) {

    public Classification withCategory(Category c) {
        return new Classification(c, priority, priorityReason, sentiment, language, confidence, multiIntent);
    }

    public Classification withPriority(Priority p) {
        return new Classification(category, p, priorityReason, sentiment, language, confidence, multiIntent);
    }
}
