package com.flowsentinel.store.inmemory.core;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.context.FlowId;
import com.flowsentinel.core.runtime.FlowState;
import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.inmemory.config.FlowSentinelInMemoryProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * A high-performance, in-memory implementation of the {@link FlowStore} interface, backed by Caffeine.
 * <p>
 * This class provides a thread-safe storage mechanism for flow data. It is designed to be a direct
 * alternative to persistent stores like Redis, offering consistent behavior for expiration policies.
 * It supports:
 * <ul>
 *   <li>Fixed Time-To-Live (TTL) expiration.</li>
 *   <li>Sliding expiration (Time-To-Idle) based on read and/or write access.</li>
 *   <li>An absolute expiration cap, ensuring data is evicted after a maximum lifetime.</li>
 * </ul>
 * The store's behavior is fully configurable via the {@link FlowSentinelInMemoryProperties} class.
 *
 * @see FlowSentinelInMemoryProperties
 * @see com.github.benmanes.caffeine.cache.Caffeine
 */
public final class InMemoryFlowStore implements FlowStore {

    /**
     * A wrapper record that pairs a stored value with its creation timestamp in nanoseconds.
     * This is essential for calculating the remaining lifetime when an absolute TTL is enforced.
     *
     * @param <T>          The type of the value being stored (e.g., {@link FlowMeta}).
     * @param value        The actual data object.
     * @param createdNanos The timestamp of creation, from {@link System#nanoTime()}.
     */
    private record CacheEntry<T>(@NonNull T value, @NonNegative long createdNanos) {
    }

    private final Cache<String, CacheEntry<FlowMeta>> metaCache;
    private final Cache<String, CacheEntry<FlowSnapshot>> snapshotCache;
    private final FlowSentinelInMemoryProperties properties;

    /**
     * Constructs a new {@code InMemoryFlowStore} with the specified configuration.
     *
     * @param properties The configuration object defining cache behavior. Must not be null.
     * @throws NullPointerException if properties is null.
     */
    public InMemoryFlowStore(@NonNull FlowSentinelInMemoryProperties properties) {
        this.properties = Objects.requireNonNull(properties, "FlowSentinelInMemoryProperties cannot be null.");
        this.metaCache = buildCache();
        this.snapshotCache = buildCache();
    }

    /**
     * Constructs a new {@code InMemoryFlowStore} with default configuration.
     * This is useful for testing or basic setups where custom configuration is not required.
     */
    public InMemoryFlowStore() {
        this(new FlowSentinelInMemoryProperties());
    }

    private <T> Cache<String, CacheEntry<T>> buildCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .expireAfter(new FlowExpiry(properties))
                .build();
    }

    @Override
    public void saveMeta(@NonNull FlowMeta meta) {
        Objects.requireNonNull(meta, "FlowMeta cannot be null.");
        final String flowId = Objects.requireNonNull(meta.flowId(), "FlowId in FlowMeta cannot be null.");
        metaCache.put(flowId, new CacheEntry<>(meta, System.nanoTime()));
    }

    @Override
    public Optional<FlowMeta> loadMeta(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        return Optional.ofNullable(metaCache.getIfPresent(flowId)).map(CacheEntry::value);
    }

    @Override
    public void saveSnapshot(@NonNull FlowSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "FlowSnapshot cannot be null.");
        final String flowId = Objects.requireNonNull(snapshot.flowId(), "FlowId in FlowSnapshot cannot be null.");
        snapshotCache.put(flowId, new CacheEntry<>(snapshot, System.nanoTime()));
    }

    @Override
    public Optional<FlowSnapshot> loadSnapshot(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        return Optional.ofNullable(snapshotCache.getIfPresent(flowId)).map(CacheEntry::value);
    }

    @Override
    public Optional<FlowState> find(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }

        final Optional<FlowMeta> metaOpt = this.loadMeta(flowId);
        final Optional<FlowSnapshot> snapshotOpt = this.loadSnapshot(flowId);

        // Both metadata and a snapshot are required to fully reconstruct the flow state.
        if (metaOpt.isPresent() && snapshotOpt.isPresent()) {
            final FlowDefinition flowDefinition = new FlowDefinition.Builder().id(FlowId.of(metaOpt.get().flowId())).build();
            // The FlowState is reconstructed from its constituent parts.
            return Optional.of(FlowState.fromSnapshot(flowDefinition, snapshotOpt.get()));
        }

        return Optional.empty();
    }

    @Override
    public void delete(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        snapshotCache.invalidate(flowId);
        metaCache.invalidate(flowId);
    }

    @Override
    public boolean exists(@NonNull String flowId) {
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }
        // A flow's existence is defined solely by its metadata. A snapshot without meta
        // is an orphan. Using asMap().containsKey() avoids triggering TTL resets.
        return metaCache.asMap().containsKey(flowId);
    }

    /**
     * Implements Caffeine's {@link Expiry} interface to provide dynamic, per-entry expiration.
     */
    private static final class FlowExpiry implements Expiry<String, CacheEntry<?>> {

        private final FlowSentinelInMemoryProperties props;
        private final long absoluteTtlNanos;

        FlowExpiry(@NonNull FlowSentinelInMemoryProperties props) {
            this.props = props;
            this.absoluteTtlNanos = (props.getAbsoluteTtl() != null && !props.getAbsoluteTtl().isNegative() && !props.getAbsoluteTtl().isZero())
                    ? props.getAbsoluteTtl().toNanos()
                    : -1L;
        }

        @Override
        public long expireAfterCreate(@NonNull String key, @NonNull CacheEntry<?> value, long currentTime) {
            final long baseTtlNanos = props.getTtl().toNanos();
            return (absoluteTtlNanos > 0) ? Math.min(baseTtlNanos, absoluteTtlNanos) : baseTtlNanos;
        }

        @Override
        public long expireAfterUpdate(@NonNull String key, @NonNull CacheEntry<?> value, long currentTime, @NonNegative long currentDuration) {
            final var resetPolicy = props.getSlidingReset();
            if (props.isSlidingEnabled() && (resetPolicy == FlowSentinelInMemoryProperties.SlidingReset.ON_WRITE || resetPolicy == FlowSentinelInMemoryProperties.SlidingReset.ON_READ_AND_WRITE)) {
                return calculateSlidingExpiration(value, currentTime);
            }
            return currentDuration;
        }

        @Override
        public long expireAfterRead(@NonNull String key, @NonNull CacheEntry<?> value, long currentTime, @NonNegative long currentDuration) {
            final var resetPolicy = props.getSlidingReset();
            if (props.isSlidingEnabled() && (resetPolicy == FlowSentinelInMemoryProperties.SlidingReset.ON_READ || resetPolicy == FlowSentinelInMemoryProperties.SlidingReset.ON_READ_AND_WRITE)) {
                return calculateSlidingExpiration(value, currentTime);
            }
            return currentDuration;
        }

        private long calculateSlidingExpiration(@NonNull CacheEntry<?> value, long currentTime) {
            final long baseTtlNanos = props.getTtl().toNanos();
            if (absoluteTtlNanos > 0) {
                final long age = currentTime - value.createdNanos();
                final long remainingAbsolute = absoluteTtlNanos - age;
                return Math.max(0, Math.min(baseTtlNanos, remainingAbsolute));
            }
            return baseTtlNanos;
        }
    }
}