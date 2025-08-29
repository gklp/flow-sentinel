package com.flowsentinel.store.redis.core;

import org.springframework.util.Assert;

/**
 * Utility class for generating standardized Redis keys for FlowSentinel operations.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Provides consistent key naming patterns across all Redis operations.</li>
 *   <li>Enforces namespace isolation to prevent key collisions.</li>
 *   <li>Supports snapshot, meta, and absolute cap key generation.</li>
 * </ul>
 *
 * <h2>Key Patterns</h2>
 * <ul>
 *   <li><b>Snapshot:</b> {@code <namespace><flowId>:snapshot}</li>
 *   <li><b>Meta:</b> {@code <namespace><flowId>:meta}</li>
 *   <li><b>Absolute Cap:</b> {@code <namespace><flowId>:cap}</li>
 * </ul>
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Immutable after construction - namespace is final.</li>
 *   <li>Package-private to enforce usage only within the Redis store implementation.</li>
 *   <li>Validates flow IDs to prevent malformed keys.</li>
 * </ul>
 *
 * @author gokalp
 */
final class RedisKeys {

    private final String namespace;

    /**
     * Creates a Redis key generator with the specified namespace.
     *
     * @param namespace non-blank namespace prefix for all keys; should end with a delimiter (e.g., "fs:flow:")
     * @throws IllegalArgumentException if namespace is blank
     */
    RedisKeys(String namespace) {
        Assert.hasText(namespace, "namespace must not be blank");
        this.namespace = namespace;
    }

    /**
     * Generates a Redis key for storing flow snapshot data.
     *
     * @param flowId non-blank flow identifier
     * @return Redis key in the format {@code <namespace><flowId>:snapshot}
     * @throws IllegalArgumentException if flowId is blank
     */
    String snapshotKey(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + flowId + ":snapshot";
    }

    /**
     * Generates a Redis key for storing flow meta information.
     *
     * @param flowId non-blank flow identifier
     * @return Redis key in the format {@code <namespace><flowId>:meta}
     * @throws IllegalArgumentException if flowId is blank
     */
    String metaKey(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + flowId + ":meta";
    }

    /**
     * Generates a Redis key for storing absolute expiration timestamps.
     * This key is used to enforce absolute TTL caps that prevent indefinite sliding renewal.
     *
     * @param flowId non-blank flow identifier
     * @return Redis key in the format {@code <namespace><flowId>:cap}
     * @throws IllegalArgumentException if flowId is blank
     */
    String capKey(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + flowId + ":cap";
    }

    /**
     * Returns the namespace prefix used by this key generator.
     *
     * @return the configured namespace
     */
    String getNamespace() {
        return namespace;
    }
}