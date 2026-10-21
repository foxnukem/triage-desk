package triage.ai;

import dev.langchain4j.model.chat.ChatModel;

/** Chat model by name, so one running app can triage and evaluate with different models. */
public interface ChatModelProvider {

    ChatModel get(String modelName);

    String defaultModel();
}
