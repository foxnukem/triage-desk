package triage.ai;

public record AiComponents(
        String model,
        TicketClassifier classifier,
        EntityExtractor extractor,
        ReplyAgent replyAgent,
        ReplyCritic critic,
        MegaPromptTriage mega
) {}
