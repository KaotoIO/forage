package io.kaoto.forage.memory.chat.infinispan;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * Infinispan-based implementation of {@link ChatMemoryStore} that provides persistent storage
 * for chat conversation history using Infinispan as the backing store.
 *
 * <p>This implementation stores chat messages as JSON-serialized data in Infinispan, with each
 * conversation identified by a unique memory ID. The store supports the full lifecycle
 * of chat memory operations including retrieval, updates, and deletion.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Persistent storage of chat conversations across application restarts</li>
 *   <li>Automatic JSON serialization/deserialization of chat messages</li>
 *   <li>Distributed caching via Infinispan for scalability and high availability</li>
 *   <li>UTF-8 encoding for proper international character support</li>
 *   <li>Robust error handling with proper resource cleanup</li>
 * </ul>
 *
 * <p><strong>Infinispan Key Structure:</strong>
 * Each conversation is stored with the memory ID as the cache key, containing a JSON string
 * of serialized {@link ChatMessage} objects. Empty conversations are represented as empty lists.
 *
 * <p><strong>Thread Safety:</strong>
 * This class is thread-safe as it uses Infinispan's thread-safe {@link RemoteCache} operations.
 * Multiple threads can safely access different conversations concurrently.
 *
 * <p><strong>Error Handling:</strong>
 * Infinispan connection failures and serialization errors are properly handled and logged.
 * Failed operations will not leave the cache in an inconsistent state.
 *
 * @see ChatMemoryStore
 * @see RemoteCacheManager
 * @since 1.0
 */
public class PersistentInfinispanStore implements ChatMemoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentInfinispanStore.class);
    private static final String MEMORY_ID_NOT_NULL = "Memory ID cannot be null";
    private static final String EMPTY_MESSAGES_JSON = "[]";

    private final RemoteCache<String, String> cache;

    /**
     * Creates a new Infinispan-based chat memory store.
     *
     * @param cache the Infinispan remote cache to use for storing chat messages, must not be {@code null}
     * @throws NullPointerException if cache is null
     */
    public PersistentInfinispanStore(RemoteCache<String, String> cache) {
        this.cache = Objects.requireNonNull(cache, "RemoteCache cannot be null");
    }

    /**
     * Deletes all chat messages for the specified memory ID.
     *
     * <p>This operation removes the entire conversation history from Infinispan.
     * If the memory ID does not exist, this operation is idempotent and will
     * not raise an error.
     *
     * @param memoryId the unique identifier for the conversation to delete, must not be {@code null}
     * @throws NullPointerException if memoryId is null
     * @throws RuntimeException if an Infinispan operation fails
     */
    @Override
    public void deleteMessages(Object memoryId) {
        Objects.requireNonNull(memoryId, MEMORY_ID_NOT_NULL);

        String key = memoryId.toString();
        try {
            String removed = cache.remove(key);
            if (removed != null) {
                LOG.debug("Deleted conversation for memory ID: {}", key);
            } else {
                LOG.debug("No conversation found to delete for memory ID: {}", key);
            }
        } catch (Exception e) {
            LOG.error("Failed to delete messages for memory ID: {}", key, e);
            throw new RuntimeException("Failed to delete chat messages from Infinispan", e);
        }
    }

    /**
     * Retrieves all chat messages for the specified memory ID.
     *
     * <p>Returns the complete conversation history as a list of {@link ChatMessage} objects.
     * If no conversation exists for the given memory ID, an empty list is returned.
     *
     * @param memoryId the unique identifier for the conversation to retrieve, must not be {@code null}
     * @return a list of chat messages for the conversation, never {@code null} but may be empty
     * @throws NullPointerException if memoryId is null
     * @throws RuntimeException if an Infinispan operation or deserialization fails
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Objects.requireNonNull(memoryId, MEMORY_ID_NOT_NULL);

        String key = memoryId.toString();
        try {
            String json = cache.get(key);

            if (json == null) {
                LOG.debug("No messages found for memory ID: {}", key);
                return Collections.emptyList();
            }

            if (json.isEmpty() || EMPTY_MESSAGES_JSON.equals(json)) {
                return Collections.emptyList();
            }

            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(json);
            LOG.debug("Retrieved {} messages for memory ID: {}", messages.size(), key);
            return messages;
        } catch (Exception e) {
            LOG.error("Failed to retrieve messages for memory ID: {}", key, e);
            if (e.getMessage() != null && e.getMessage().contains("deserialization")) {
                throw new RuntimeException("Failed to deserialize chat messages", e);
            }
            throw new RuntimeException("Failed to retrieve chat messages from Infinispan", e);
        }
    }

    /**
     * Updates the chat messages for the specified memory ID.
     *
     * <p>This operation replaces the entire conversation history with the provided messages.
     * The messages are serialized to JSON format and stored in Infinispan. If the messages list
     * is empty, an empty conversation is stored (not deleted).
     *
     * @param memoryId the unique identifier for the conversation to update, must not be {@code null}
     * @param messages the complete list of messages for the conversation, must not be {@code null}
     * @throws NullPointerException if memoryId or messages is null
     * @throws RuntimeException if an Infinispan operation or serialization fails
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Objects.requireNonNull(memoryId, MEMORY_ID_NOT_NULL);
        Objects.requireNonNull(messages, "Messages list cannot be null");

        String key = memoryId.toString();
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            cache.put(key, json);
            LOG.debug("Updated {} messages for memory ID: {}", messages.size(), key);
        } catch (Exception e) {
            LOG.error("Failed to update messages for memory ID: {}", key, e);
            if (e.getMessage() != null && e.getMessage().contains("serialization")) {
                throw new RuntimeException("Failed to serialize chat messages", e);
            }
            throw new RuntimeException("Failed to update chat messages in Infinispan", e);
        }
    }
}
