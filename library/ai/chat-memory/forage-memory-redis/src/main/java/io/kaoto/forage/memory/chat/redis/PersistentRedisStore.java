package io.kaoto.forage.memory.chat.redis;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Redis-based implementation of {@link ChatMemoryStore} that provides persistent storage
 * for chat conversation history using Redis as the backing store.
 *
 * <p>This implementation stores chat messages as JSON-serialized data in Redis, with each
 * conversation identified by a unique memory ID. The store supports the full lifecycle
 * of chat memory operations including retrieval, updates, and deletion.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Persistent storage of chat conversations across application restarts</li>
 *   <li>Automatic JSON serialization/deserialization of chat messages</li>
 *   <li>Connection pooling via {@link JedisPool} for optimal performance</li>
 *   <li>UTF-8 encoding for proper international character support</li>
 *   <li>Robust error handling with proper resource cleanup</li>
 * </ul>
 *
 * <p><strong>Redis Key Structure:</strong>
 * Each conversation is stored with the memory ID as the Redis key, containing a JSON array
 * of serialized {@link ChatMessage} objects. Empty conversations are represented as empty lists.
 *
 * <p><strong>Thread Safety:</strong>
 * This class is thread-safe as it uses a connection pool and ensures proper resource
 * cleanup for each operation. Multiple threads can safely access different conversations
 * concurrently.
 *
 * <p><strong>Error Handling:</strong>
 * Redis connection failures and serialization errors are properly handled and logged.
 * Failed operations will not leave connections in an inconsistent state.
 *
 * @see ChatMemoryStore
 * @see JedisPool
 * @since 1.0
 */
public class PersistentRedisStore implements ChatMemoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentRedisStore.class);
    private static final String MEMORY_ID_NOT_NULL = "Memory ID cannot be null";
    private static final String EMPTY_MESSAGES_JSON = "[]";

    private final JedisPool jedisPool;

    /**
     * Creates a new Redis-based chat memory store.
     *
     * @param jedisPool the Redis connection pool to use for database operations, must not be {@code null}
     * @throws NullPointerException if jedisPool is null
     */
    public PersistentRedisStore(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "JedisPool cannot be null");
    }

    /**
     * Deletes all chat messages for the specified memory ID.
     *
     * <p>This operation removes the entire conversation history from Redis.
     * If the memory ID does not exist, this operation is idempotent and will
     * not raise an error.
     *
     * @param memoryId the unique identifier for the conversation to delete, must not be {@code null}
     * @throws NullPointerException if memoryId is null
     * @throws RuntimeException if a Redis operation fails
     */
    @Override
    public void deleteMessages(Object memoryId) {
        Objects.requireNonNull(memoryId, MEMORY_ID_NOT_NULL);

        String key = memoryId.toString();
        try (Jedis jedis = jedisPool.getResource()) {
            long deleted = jedis.del(key);
            LOG.debug("Deleted {} conversation(s) for memory ID: {}", deleted, key);
        } catch (JedisException e) {
            LOG.error("Failed to delete messages for memory ID: {}", key, e);
            throw new RuntimeException("Failed to delete chat messages from Redis", e);
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
     * @throws RuntimeException if a Redis operation or deserialization fails
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Objects.requireNonNull(memoryId, MEMORY_ID_NOT_NULL);

        String key = memoryId.toString();
        try (Jedis jedis = jedisPool.getResource()) {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = jedis.get(keyBytes);

            if (bytes == null) {
                LOG.debug("No messages found for memory ID: {}", key);
                return Collections.emptyList();
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            if (json.isEmpty() || EMPTY_MESSAGES_JSON.equals(json)) {
                return Collections.emptyList();
            }

            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(json);
            LOG.debug("Retrieved {} messages for memory ID: {}", messages.size(), key);
            return messages;
        } catch (JedisException e) {
            LOG.error("Failed to retrieve messages for memory ID: {}", key, e);
            throw new RuntimeException("Failed to retrieve chat messages from Redis", e);
        } catch (Exception e) {
            LOG.error("Failed to deserialize messages for memory ID: {}", key, e);
            throw new RuntimeException("Failed to deserialize chat messages", e);
        }
    }

    /**
     * Updates the chat messages for the specified memory ID.
     *
     * <p>This operation replaces the entire conversation history with the provided messages.
     * The messages are serialized to JSON format and stored in Redis. If the messages list
     * is empty, an empty conversation is stored (not deleted).
     *
     * @param memoryId the unique identifier for the conversation to update, must not be {@code null}
     * @param messages the complete list of messages for the conversation, must not be {@code null}
     * @throws NullPointerException if memoryId or messages is null
     * @throws RuntimeException if a Redis operation or serialization fails
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Objects.requireNonNull(memoryId, MEMORY_ID_NOT_NULL);
        Objects.requireNonNull(messages, "Messages list cannot be null");

        String key = memoryId.toString();
        try (Jedis jedis = jedisPool.getResource()) {
            String json = ChatMessageSerializer.messagesToJson(messages);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] messageBytes = json.getBytes(StandardCharsets.UTF_8);

            jedis.set(keyBytes, messageBytes);
            LOG.debug("Updated {} messages for memory ID: {}", messages.size(), key);
        } catch (JedisException e) {
            LOG.error("Failed to update messages for memory ID: {}", key, e);
            throw new RuntimeException("Failed to update chat messages in Redis", e);
        } catch (Exception e) {
            LOG.error("Failed to serialize messages for memory ID: {}", key, e);
            throw new RuntimeException("Failed to serialize chat messages", e);
        }
    }
}
