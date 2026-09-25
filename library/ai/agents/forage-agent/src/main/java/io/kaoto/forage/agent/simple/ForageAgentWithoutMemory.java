package io.kaoto.forage.agent.simple;

import java.util.List;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ForageAgentWithoutMemory {

    Result<String> chat(@UserMessage String userMessage);

    @SystemMessage("{{prompt}}")
    Result<String> chat(@UserMessage String userMessage, @V("prompt") String systemMessage);

    Result<String> chat(@UserMessage String userMessage, @UserMessage List<Content> contents);

    @SystemMessage("{{prompt}}")
    Result<String> chat(
            @UserMessage String userMessage, @UserMessage List<Content> contents, @V("prompt") String systemMessage);
}
