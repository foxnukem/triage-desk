package triage.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MegaPromptTriage {

    @SystemMessage(fromResource = "/prompts/mega.txt")
    @UserMessage(fromResource = "/prompts/ticket-user.txt")
    Result<MegaTriage> triage(@V("ticket") String ticket, @V("articles") String articles, @V("feedback") String feedback);
}
