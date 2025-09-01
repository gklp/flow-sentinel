package com.flowsentinel.store.redis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.flowsentinel.core.runtime.FlowState;
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
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A Redis-based implementation of the {@link FlowStore} interface.
 * <p>
 * This store persists flow metadata and snapshots in Redis, leveraging its Time-To-Live (TTL)
 * capabilities for automatic data expiration. It supports both fixed TTL and sliding expiration
 * policies, as well as an absolute cap on record lifetime. The behavior is configured via
 * {@link com.flowsentinel.store.redis.config.RedisStorageAutoConfiguration}.
 * </p>
 * <p>
 * To ensure atomicity for critical operations like creation and deletion, this implementation
 * uses Lua scripts executed on the Redis server. All values are stored as JSON strings.
 * </p>
 *
 * @author AI Assistant
 * @see FlowStore
 * @see com.flowsentinel.store.redis.config.RedisStorageAutoConfiguration
 */
public class RedisFlowStore implements FlowStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFlowStore.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule());

    // Lua script to atomically create a key with an expiration if it does not already exist.
    // Uses PEXPIRE for millisecond precision.
    private static final RedisScript<Long> CREATE_META_SCRIPT = new DefaultRedisScript<>(
            "local meta_exists = redis.call('SETNX', KEYS[1], ARGV[1]); " +
                    "if meta_exists == 1 then " +
                    "    redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                    "end; " +
                    "return meta_exists;",
            Long.class
    );

    // Lua script to atomically delete multiple keys.
    private static final RedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('DEL', KEYS[1], KEYS[2])", Long.class);

    private final StringRedisTemplate template;
    private final RedisKeys redisKeys;
    private final Duration ttl;
    private final boolean slidingEnabled;
    private final FlowSentinelRedisProperties.SlidingReset slidingReset;
    private final Duration absoluteCap;

    /**
     * Constructs a new RedisFlowStore with the specified configuration.
     * This constructor is typically called by Spring's dependency injection container.
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
        this.template = Objects.requireNonNull(template, "StringRedisTemplate cannot be null.");
        this.redisKeys = new RedisKeys(Objects.requireNonNull(keyPrefix, "Key prefix cannot be null."));
        this.ttl = Objects.requireNonNull(ttl, "TTL duration cannot be null.");
        this.slidingReset = Objects.requireNonNull(slidingReset, "Sliding reset policy cannot be null.");
        this.absoluteCap = Objects.requireNonNull(absoluteCap, "Absolute cap duration cannot be null.");
        this.slidingEnabled = slidingEnabled;
    }

    /**
     * Calculates the effective Time-To-Live (TTL) for a flow's keys, respecting the absolute cap.
     * If sliding expiration is disabled or the absolute cap is not set, this returns the base TTL.
     * Otherwise, it calculates the remaining time until the absolute cap and returns the lesser
     * of that value and the base TTL.
     *
     * @param flowId The ID of the flow.
     * @return The calculated effective TTL as a {@link Duration}.
     */
    private Duration getEffectiveTtl(String flowId) {
        // If sliding is disabled or no absolute cap is set, just return the base TTL.
        if (!slidingEnabled || absoluteCap.isZero() || absoluteCap.isNegative()) {
            return this.ttl;
        }

        // We need the flow's creation time from the meta record.
        String metaJson = template.opsForValue().get(redisKeys.metaKey(flowId));
        if (metaJson == null) {
            log.warn("Cannot calculate effective TTL; FlowMeta for flow ID '{}' not found. Defaulting to base TTL.", flowId);
            return this.ttl;
        }

        try {
            FlowMeta meta = objectMapper.readValue(metaJson, FlowMeta.class);
            Instant createdAt = meta.createdAt();
            Instant now = Instant.now();
            Instant absoluteExpiryTime = createdAt.plus(absoluteCap);

            if (now.isAfter(absoluteExpiryTime)) {
                log.debug("Absolute cap reached for flow '{}'. Returning near-instant TTL.", flowId);
                return Duration.ofMillis(1);
            }

            Duration remainingTime = Duration.between(now, absoluteExpiryTime);
            // The new TTL is the smaller of the base TTL and the remaining time.
            Duration effectiveTtl = remainingTime.compareTo(this.ttl) < 0 ? remainingTime : this.ttl;

            // Ensure TTL is positive
            return (effectiveTtl.isNegative() || effectiveTtl.isZero()) ? Duration.ofMillis(1) : effectiveTtl;
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize FlowMeta for flow ID '{}' to calculate effective TTL. Defaulting to base TTL.", flowId, e);
            return this.ttl;
        }
    }


    @Override
    public void saveMeta(@NonNull FlowMeta meta) {
        Objects.requireNonNull(meta, "FlowMeta cannot be null.");
        final String flowId = Objects.requireNonNull(meta.flowId(), "FlowId in FlowMeta cannot be null.");
        final String metaKey = redisKeys.metaKey(flowId);

        try {
            final String json = objectMapper.writeValueAsString(meta);
            long ttlMillis = ttl.toMillis();
            if (ttlMillis <= 0) {
                log.warn("TTL for flow '{}' is non-positive ({}ms). The record might not expire as expected.", flowId, ttlMillis);
            }

            Long result = template.execute(CREATE_META_SCRIPT, List.of(metaKey), json, String.valueOf(ttlMillis));
            if (result != null && result == 1) {
                log.debug("Atomically created and set TTL for meta key: {}", metaKey);
            } else {
                log.debug("Meta key already exists, did not overwrite: {}", metaKey);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize FlowMeta for flow ID: {}", flowId, e);
            throw new IllegalStateException("Failed to serialize FlowMeta for flow: " + flowId, e);
        }
    }


    @Override
    public Optional<FlowMeta> loadMeta(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        final String metaKey = redisKeys.metaKey(flowId);
        String json = template.opsForValue().get(metaKey);

        if (json == null) {
            return Optional.empty();
        }

        // Apply sliding expiration if configured on read.
        if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ || slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
            Duration effectiveTtl = getEffectiveTtl(flowId);
            log.trace("Applying sliding expiration on read for meta key: {}. New TTL: {}", metaKey, effectiveTtl);
            template.expire(metaKey, effectiveTtl);
        }

        try {
            return Optional.of(objectMapper.readValue(json, FlowMeta.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize FlowMeta for key: {}. Corrupted data: {}", metaKey, json, e);
            throw new DataRetrievalFailureException("Failed to deserialize FlowMeta for flow: " + flowId, e);
        }
    }


    @Override
    public void saveSnapshot(@NonNull FlowSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "FlowSnapshot cannot be null.");
        final String flowId = Objects.requireNonNull(snapshot.flowId(), "FlowId in FlowSnapshot cannot be null.");
        final String snapshotKey = redisKeys.snapshotKey(flowId);
        final String metaKey = redisKeys.metaKey(flowId);

        try {
            String json = objectMapper.writeValueAsString(snapshot);
            Duration effectiveTtl = getEffectiveTtl(flowId);

            // Set the snapshot value with the calculated TTL.
            template.opsForValue().set(snapshotKey, json, effectiveTtl);

            // If sliding on writing is enabled, also refresh the meta key's TTL.
            if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_WRITE || slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
                log.trace("Applying sliding expiration on write for meta key: {}. New TTL: {}", metaKey, effectiveTtl);
                template.expire(metaKey, effectiveTtl);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize FlowSnapshot for flow ID: {}", flowId, e);
            throw new IllegalStateException("Failed to serialize FlowSnapshot for flow: " + flowId, e);
        }
    }


    @Override
    public Optional<FlowSnapshot> loadSnapshot(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        final String snapshotKey = redisKeys.snapshotKey(flowId);
        String json = template.opsForValue().get(snapshotKey);

        if (json == null) {
            return Optional.empty();
        }

        // On snapshot read, extend the TTL of both the snapshot and its meta-key to keep the whole flow alive.
        if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ || slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
            Duration effectiveTtl = getEffectiveTtl(flowId);
            final String metaKey = redisKeys.metaKey(flowId);

            log.trace("Applying sliding expiration on read for snapshot key: {}. New TTL: {}", snapshotKey, effectiveTtl);
            template.expire(snapshotKey, effectiveTtl);

            log.trace("Applying sliding expiration on read for meta key: {}. New TTL: {}", metaKey, effectiveTtl);
            template.expire(metaKey, effectiveTtl);
        }

        try {
            return Optional.of(objectMapper.readValue(json, FlowSnapshot.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize FlowSnapshot for key: {}. Corrupted data: {}", snapshotKey, json, e);
            throw new DataRetrievalFailureException("Failed to deserialize FlowSnapshot for flow: " + flowId, e);
        }
    }


    @Override
    public Optional<FlowState> find(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }

        // Use loadSnapshot to centralize logic for retrieval and sliding expiration
        return loadSnapshot(flowId).flatMap(snapshot -> {
            try {
                // Convert the FlowSnapshot object directly into a FlowState object.
                // This assumes that the fields in FlowSnapshot (currentStep, isCompleted, attributes)
                // can be mapped by Jackson to create a FlowState instance.
                // Note: The 'definition' field in the resulting FlowState will likely be null
                // as it's not part of the snapshot, which might require handling in the business logic layer.
                FlowState state = objectMapper.convertValue(snapshot, FlowState.class);
                return Optional.of(state);
            } catch (IllegalArgumentException e) {
                log.error("Failed to convert FlowSnapshot to FlowState for flow ID: {}", flowId, e);
                throw new DataRetrievalFailureException("Failed to convert FlowSnapshot to FlowState for flow: " + flowId, e);
            }
        });
    }


    @Override
    public void delete(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        final String metaKey = redisKeys.metaKey(flowId);
        final String snapshotKey = redisKeys.snapshotKey(flowId);

        Long deletedCount = template.execute(DELETE_SCRIPT, List.of(metaKey, snapshotKey));
        log.debug("Attempted to delete keys for flow ID '{}'. Keys deleted: {}", flowId, deletedCount);
    }


    @Override
    public boolean exists(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        final String metaKey = redisKeys.metaKey(flowId);
        Boolean hasKey = template.hasKey(metaKey);
        return Boolean.TRUE.equals(hasKey);
    }
}