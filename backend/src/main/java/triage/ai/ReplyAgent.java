package triage.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** Tool-using agent: searchKb + getOrder. The category picks {@code guidance}, i.e. a specialised reply prompt. */
public interface ReplyAgent {

    @SystemMessage(fromResource = "/prompts/reply-agent.txt")
    @UserMessage(fromResource = "/prompts/reply-user.txt")
    Result<ReplyDraft> draft(@V("ticket") String ticket,
                             @V("guidance") String guidance,
                             @V("language") String language,
                             @V("context") String context,
                             @V("feedback") String feedback);
}
