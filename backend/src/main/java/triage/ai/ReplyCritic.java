package triage.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReplyCritic {

    @SystemMessage(fromResource = "/prompts/critic.txt")
    @UserMessage(fromResource = "/prompts/critic-user.txt")
    Result<PolicyReview> review(@V("ticket") String ticket, @V("draft") String draft, @V("feedback") String feedback);

    @SystemMessage(fromResource = "/prompts/rewrite.txt")
    @UserMessage(fromResource = "/prompts/rewrite-user.txt")
    Result<ReplyDraft> rewrite(@V("ticket") String ticket,
                               @V("draft") String draft,
                               @V("violations") String violations,
                               @V("allowedArticles") String allowedArticles,
                               @V("language") String language,
                               @V("feedback") String feedback);
}
