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
 *   <li>Enables partition-based key patterns for multi-tenancy.</li>
 * </ul>
 *
 * <h2>Key Patterns</h2>
 * <ul>
 *   <li><b>Snapshot:</b> {@code <namespace><partitionKey>:<flowId>:snapshot}</li>
 *   <li><b>Meta:</b> {@code <namespace><partitionKey>:<flowId>:meta}</li>
 *   <li><b>Absolute Cap:</b> {@code <namespace><partitionKey>:<flowId>:cap}</li>
 *   <li><b>Partition Pattern:</b> {@code <namespace><partitionKey>:*}</li>
 * </ul>
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Immutable after construction - namespace is final.</li>
 *   <li>Package-private to enforce usage only within the Redis store implementation.</li>
 *   <li>Validates flow IDs and partition keys to prevent malformed keys.</li>
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
     * Generates a Redis key for storing flow snapshot data with partition support.
     *
     * @param partitionKey non-blank partition key
     * @param flowId non-blank flow identifier
     * @return Redis key in the format {@code <namespace><partitionKey>:<flowId>:snapshot}
     * @throws IllegalArgumentException if partitionKey or flowId is blank
     */
    String snapshotKey(String partitionKey, String flowId) {
        Assert.hasText(partitionKey, "partitionKey must not be blank");
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + partitionKey + ":" + flowId + ":snapshot";
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
     * Generates a Redis key for storing flow meta information with partition support.
     *
     * @param partitionKey non-blank partition key
     * @param flowId non-blank flow identifier
     * @return Redis key in the format {@code <namespace><partitionKey>:<flowId>:meta}
     * @throws IllegalArgumentException if partitionKey or flowId is blank
     */
    String metaKey(String partitionKey, String flowId) {
        Assert.hasText(partitionKey, "partitionKey must not be blank");
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + partitionKey + ":" + flowId + ":meta";
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
     * Generates a Redis key for storing absolute expiration timestamps with partition support.
     *
     * @param partitionKey non-blank partition key
     * @param flowId non-blank flow identifier
     * @return Redis key in the format {@code <namespace><partitionKey>:<flowId>:cap}
     * @throws IllegalArgumentException if partitionKey or flowId is blank
     */
    String capKey(String partitionKey, String flowId) {
        Assert.hasText(partitionKey, "partitionKey must not be blank");
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + partitionKey + ":" + flowId + ":cap";
    }

    /**
     * Generates a Redis pattern for finding all keys belonging to a partition.
     *
     * @param partitionKey non-blank partition key
     * @return Redis pattern in the format {@code <namespace><partitionKey>:*}
     * @throws IllegalArgumentException if partitionKey is blank
     */
    String partitionPattern(String partitionKey) {
        Assert.hasText(partitionKey, "partitionKey must not be blank");
        return namespace + partitionKey + ":*";
    }

    /**
     * Returns the namespace prefix used by this key generator.
     *
     * @return the configured namespace
     */
    String getNamespace() {
        return namespace;
    }

    String aggregateKey(String flowId) { 
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + flowId + ":agg";
    }

    String aggregateKey(String partitionKey, String flowId) {
        Assert.hasText(partitionKey, "partitionKey must not be blank");
        Assert.hasText(flowId, "flowId must not be blank");
        return namespace + partitionKey + ":" + flowId + ":agg";
    }
}