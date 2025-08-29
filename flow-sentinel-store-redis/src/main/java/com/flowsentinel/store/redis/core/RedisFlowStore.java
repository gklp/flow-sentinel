package com.flowsentinel.store.redis.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.config.FlowSentinelRedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A Redis-based implementation of the {@link FlowStore} interface.
 * <p>
 * This store persists flow metadata and snapshots in Redis, leveraging its time-to-live (TTL)
 * capabilities for automatic data expiration. It supports both fixed TTL and sliding expiration
 * policies, as well as an absolute cap on record lifetime.
 * </p>
 * <p>
 * To ensure atomicity for critical operations like creation and deletion, this implementation
 * uses Lua scripts executed on the Redis server.
 * </p>
 *
 * @author AI Assistant
 */
public class RedisFlowStore implements FlowStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFlowStore.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Use PEXPIRE to support millisecond precision for TTL
    private static final RedisScript<Long> CREATE_META_SCRIPT = new DefaultRedisScript<>(
            "local meta_exists = redis.call('SETNX', KEYS[1], ARGV[1]); " +
                    "if meta_exists == 1 then " +
                    "    redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                    "end; " +
                    "return meta_exists;",
            Long.class
    );

    private static final RedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])", Long.class);

    private final StringRedisTemplate template;
    private final RedisKeys redisKeys;
    private final Duration ttl;
    private final boolean slidingEnabled;
    private final FlowSentinelRedisProperties.SlidingReset slidingReset;
    private final Duration absoluteCap;

    /**
     * Constructs a new RedisFlowStore with the specified configuration.
     *
     * @param template       The Spring Redis template for executing commands. Must not be null.
     * @param keyPrefix      The prefix for all keys to ensure namespace isolation. Must not be null.
     * @param ttl            The base time-to-live for records. Must not be null.
     * @param slidingEnabled Enables or disables the sliding expiration policy.
     * @param slidingReset   The policy that defines when to reset the TTL on access. Must not be null.
     * @param absoluteCap    The absolute maximum lifetime for a record. Set to zero or negative to disable. Must not be null.
     * @throws NullPointerException if template, keyPrefix, ttl, slidingReset, or absoluteCap are null.
     */
    public RedisFlowStore(StringRedisTemplate template, String keyPrefix, Duration ttl, boolean slidingEnabled, FlowSentinelRedisProperties.SlidingReset slidingReset, Duration absoluteCap) {
        this.template = Objects.requireNonNull(template, "StringRedisTemplate cannot be null");
        this.redisKeys = new RedisKeys(Objects.requireNonNull(keyPrefix, "keyPrefix cannot be null"));
        this.ttl = Objects.requireNonNull(ttl, "ttl cannot be null");
        this.slidingEnabled = slidingEnabled;
        this.slidingReset = Objects.requireNonNull(slidingReset, "slidingReset cannot be null");
        this.absoluteCap = Objects.requireNonNull(absoluteCap, "absoluteCap cannot be null");
    }

    /**
     * Convenience constructor for testing or basic setup where sliding expiration is disabled.
     *
     * @param template  The Spring Redis template for executing commands. Must not be null.
     * @param keyPrefix The prefix for all keys to ensure namespace isolation. Must not be null.
     * @param ttl       The base time-to-live for records. Must not be null.
     * @throws NullPointerException if template, keyPrefix, or ttl are null.
     */
    public RedisFlowStore(StringRedisTemplate template, String keyPrefix, Duration ttl) {
        this(
                template,
                keyPrefix,
                ttl,
                false, // slidingEnabled
                FlowSentinelRedisProperties.SlidingReset.ON_READ, // default slidingReset
                Duration.ZERO // default absoluteCap
        );
    }

    /**
     * Checks if the absolute cap feature is enabled.
     *
     * @return true if an absolute cap is configured and positive, false otherwise.
     */
    private boolean isCapped() {
        return !absoluteCap.isZero() && !absoluteCap.isNegative();
    }

    /**
     * Determines if the TTL should be reset on a read operation based on the current policy.
     *
     * @return true if sliding is enabled and the policy includes ON_READ.
     */
    private boolean shouldResetOnRead() {
        return slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ || slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE);
    }

    @Override
    public boolean exists(String flowId) {
        if (isCapped() && !Boolean.TRUE.equals(template.hasKey(redisKeys.capKey(flowId)))) {
            return false;
        }
        return Boolean.TRUE.equals(template.hasKey(redisKeys.metaKey(flowId)));
    }

    @Override
    public Optional<FlowMeta> loadMeta(String flowId) {
        String metaKey = redisKeys.metaKey(flowId);
        String capKey = redisKeys.capKey(flowId);

        if (isCapped() && !Boolean.TRUE.equals(template.hasKey(capKey))) {
            log.debug("Flow {} expired due to absolute TTL cap. Deleting remnant keys.", flowId);
            delete(flowId);
            return Optional.empty();
        }

        String jsonMeta = template.opsForValue().get(metaKey);

        if (jsonMeta == null) {
            return Optional.empty();
        }

        try {
            FlowMeta meta = objectMapper.readValue(jsonMeta, FlowMeta.class);
            if (shouldResetOnRead()) {
                template.expire(metaKey, ttl);
            }
            return Optional.of(meta);
        } catch (IOException e) {
            throw new DataRetrievalFailureException("Failed to load meta for flow: " + flowId + ". Invalid JSON format.", e);
        }
    }

    @Override
    public Optional<FlowSnapshot> loadSnapshot(String flowId) {
        String snapshotKey = redisKeys.snapshotKey(flowId);
        String capKey = redisKeys.capKey(flowId);

        if (isCapped() && !Boolean.TRUE.equals(template.hasKey(capKey))) {
            log.debug("Flow {} expired due to absolute TTL cap. Deleting remnant keys.", flowId);
            delete(flowId);
            return Optional.empty();
        }

        String snapshotValue = template.opsForValue().get(snapshotKey);

        if (snapshotValue == null) {
            return Optional.empty();
        }

        int firstPipe = snapshotValue.indexOf('|');
        if (firstPipe == -1) {
            throw new DataRetrievalFailureException("Failed to load snapshot for flow: " + flowId + ". Invalid format.");
        }
        String payload = snapshotValue.substring(0, firstPipe);
        String contentType = snapshotValue.substring(firstPipe + 1);

        FlowSnapshot snapshot = new FlowSnapshot(flowId, payload, contentType, Instant.now(), Instant.now());

        if (shouldResetOnRead()) {
            template.expire(snapshotKey, ttl);
        }
        return Optional.of(snapshot);
    }

    @Override
    public void saveMeta(FlowMeta meta) {
        String metaKey = redisKeys.metaKey(meta.flowId());
        try {
            String jsonMeta = objectMapper.writeValueAsString(meta);
            template.opsForValue().set(metaKey, jsonMeta, ttl);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize FlowMeta to JSON for flow: " + meta.flowId(), e);
        }
    }

    @Override
    public void saveSnapshot(FlowSnapshot snapshot) {
        String flowId = snapshot.flowId();
        createMetaIfAbsent(flowId);

        String snapshotKey = redisKeys.snapshotKey(flowId);
        String snapshotValue = snapshot.payload() + "|" + snapshot.contentType();
        template.opsForValue().set(snapshotKey, snapshotValue, ttl);
    }

    @Override
    public void delete(String flowId) {
        List<String> keys = List.of(
                redisKeys.metaKey(flowId),
                redisKeys.snapshotKey(flowId),
                redisKeys.capKey(flowId)
        );
        Long deletedCount = template.execute(DELETE_SCRIPT, keys);
        log.debug("Deleted {} keys for flowId: {}", deletedCount, flowId);
    }

    /**
     * Atomically creates a new {@link FlowMeta} record if one does not already exist.
     * If capping is enabled, it also creates a separate "cap" key with the absolute TTL.
     *
     * @param flowId The ID of the flow for which to create metadata.
     */
    private void createMetaIfAbsent(String flowId) {
        String metaKey = redisKeys.metaKey(flowId);
        try {
            FlowMeta newMeta = FlowMeta.createNew(flowId);
            String jsonMeta = objectMapper.writeValueAsString(newMeta);

            List<String> keys = Collections.singletonList(metaKey);
            // Pass TTL in milliseconds to the PEXPIRE script
            Long result = template.execute(CREATE_META_SCRIPT, keys, jsonMeta, String.valueOf(ttl.toMillis()));

            if (result != null && result == 1L) {
                log.debug("Atomically created new meta for flowId: {}", flowId);
                if (isCapped()) {
                    template.opsForValue().set(redisKeys.capKey(flowId), "1", absoluteCap);
                    log.debug("Set absolute TTL cap of {} for flowId: {}", absoluteCap, flowId);
                }
            } else {
                log.debug("Meta already existed for flowId: {}", flowId);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create initial meta due to JSON serialization issue for flow: " + flowId, e);
        }
    }
}