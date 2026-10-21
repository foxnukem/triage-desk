package triage.ai;

import java.math.BigDecimal;
import java.util.List;

import dev.langchain4j.model.output.structured.Description;

/** Baseline (ablation 6): the whole triage from one prompt, no tools, no validation. */
public record MegaTriage(
        Category category,
        Priority priority,
        String priorityReason,
        Sentiment sentiment,
        Lang language,
        double confidence,
        boolean multiIntent,
        @Description("order number from the ticket, normalised to GK- plus its six digits, or null") String orderId,
        @Description("email from the ticket, or null") String email,
        String product,
        BigDecimal amount,
        String summary,
        @Description("reply draft to the customer") String replyText,
        @Description("ids of knowledge base articles the reply relies on") List<String> citedArticles,
        boolean needsHuman
) {}
