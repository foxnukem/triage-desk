package triage.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReplyDraft(
        @JsonProperty(required = true) @Description("the reply to the customer")
        @NotBlank @Size(max = 2000) String text,
        @JsonProperty(required = true) @Description("ids (kb-...) of the searchKb articles the reply relies on")
        @NotNull List<String> citedArticles,
        @JsonProperty(required = true) @Description("true if the reply commits Gadgetko to an action: refund, replacement, pickup, callback")
        boolean promisesAction
) {}
