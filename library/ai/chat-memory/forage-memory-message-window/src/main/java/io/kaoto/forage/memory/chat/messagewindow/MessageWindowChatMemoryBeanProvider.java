package io.kaoto.forage.memory.chat.messagewindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.ai.ChatMemoryBeanProvider;
import io.kaoto.forage.core.ai.MaxMessagesAware;
import io.kaoto.forage.core.annotations.ForageBean;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

@ForageBean(
        value = "message-window",
        components = {"camel-langchain4j-agent"},
        feature = "Memory",
        configClass = MessageWindowConfig.class,
        description = "In-memory storage with configurable message window size")
public class MessageWindowChatMemoryBeanProvider implements ChatMemoryBeanProvider, MaxMessagesAware {
    private static final Logger LOG = LoggerFactory.getLogger(MessageWindowChatMemoryBeanProvider.class);

    private volatile Integer maxMessagesOverride;

    public MessageWindowChatMemoryBeanProvider() {
        // Required for ServiceLoader-based instantiation
    }

    @Override
    public void withMaxMessages(int maxMessages) {
        this.maxMessagesOverride = maxMessages;
    }

    @Override
    public ChatMemoryProvider create() {
        return create(null);
    }

    @Override
    public ChatMemoryProvider create(String id) {
        MessageWindowConfig config = new MessageWindowConfig(id);
        int maxMessages = maxMessagesOverride != null ? maxMessagesOverride : config.maxMessages();
        LOG.trace("Creating MessageWindowChatMemoryFactory with prefix={}, maxMessages={}", id, maxMessages);
        PersistentChatMemoryStore store = new PersistentChatMemoryStore();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(store)
                .build();
    }
}
