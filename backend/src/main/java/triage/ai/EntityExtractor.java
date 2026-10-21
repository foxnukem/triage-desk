package triage.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EntityExtractor {

    @SystemMessage(fromResource = "/prompts/extractor.txt")
    @UserMessage(fromResource = "/prompts/ticket-user.txt")
    Result<Extraction> extract(@V("ticket") String ticket, @V("feedback") String feedback);
}
