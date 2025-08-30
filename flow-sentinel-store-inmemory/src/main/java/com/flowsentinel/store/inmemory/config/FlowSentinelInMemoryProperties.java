package com.flowsentinel.store.inmemory.config;

import java.time.Duration;

/**
 * Holds all configuration properties for the in-memory storage implementation of FlowSentinel.
 * <p>
 * This class provides a type-safe way to configure the behavior of the {@code InMemoryFlowStore},
 * including cache size, expiration policies (TTL), and sliding expiration rules. Its structure
 * is designed to mirror {@code FlowSentinelRedisProperties} for consistency across storage types.
 *
 * @see com.flowsentinel.store.inmemory.core.InMemoryFlowStore
 */
public class FlowSentinelInMemoryProperties {

    /**
     * Defines the trigger for resetting a record's time-to-live (TTL), enabling sliding expiration.
     */
    public enum SlidingReset {
        /**
         * Reset TTL upon read access.
         */
        ON_READ,
        /**
         * Reset TTL upon write or update access.
         */
        ON_WRITE,
        /**
         * Reset TTL upon either read or write access.
         */
        ON_READ_AND_WRITE
    }

    /**
     * The base Time-To-Live (TTL) for flow records.
     * Default: 1 hour.
     */
    private Duration ttl = Duration.ofHours(1);

    /**
     * Enables or disables the sliding expiration policy. If false, records expire based on a fixed
     * TTL from their creation time. If true, the TTL is reset on access according to the
     * {@code slidingReset} policy.
     * Default: false.
     */
    private boolean slidingEnabled = false;

    /**
     * The policy that defines when to reset the TTL when {@code slidingEnabled} is true.
     * Default: ON_READ.
     */
    private SlidingReset slidingReset = SlidingReset.ON_READ;

    /**
     * An optional absolute upper bound on a record's lifetime. When set to a positive duration,
     * a record will be evicted after this period, regardless of access patterns or sliding TTL resets.
     * A value of zero or a negative duration disables this feature.
     * Default: 0 (disabled).
     */
    private Duration absoluteTtl = Duration.ZERO;

    /**
     * The maximum number of entries the cache can hold. Once this limit is reached,
     * the cache will evict entries based on its configured policy (e.g., least recently used).
     * Default: 10,000.
     */
    private long maximumSize = 10_000L;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public boolean isSlidingEnabled() {
        return slidingEnabled;
    }

    public void setSlidingEnabled(boolean slidingEnabled) {
        this.slidingEnabled = slidingEnabled;
    }

    public SlidingReset getSlidingReset() {
        return slidingReset;
    }

    public void setSlidingReset(SlidingReset slidingReset) {
        this.slidingReset = slidingReset;
    }

    public Duration getAbsoluteTtl() {
        return absoluteTtl;
    }

    public void setAbsoluteTtl(Duration absoluteTtl) {
        this.absoluteTtl = absoluteTtl;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }
}