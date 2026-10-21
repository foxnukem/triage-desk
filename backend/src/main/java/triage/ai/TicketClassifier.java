package triage.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TicketClassifier {

    @SystemMessage(fromResource = "/prompts/classifier.txt")
    @UserMessage(fromResource = "/prompts/ticket-user.txt")
    Result<Classification> classify(@V("ticket") String ticket, @V("feedback") String feedback);
}
