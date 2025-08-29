
package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.config.FlowSentinelRedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link FlowStore}.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Persists {@link FlowSnapshot} payloads as plain JSON strings.</li>
 *   <li>Persists {@link FlowMeta} state using a compact string encoding via {@link MetaCodec}.</li>
 *   <li>Applies optional TTLs per data type (snapshots and meta entries).</li>
 *   <li>Supports sliding TTL with configurable renewal triggers.</li>
 *   <li>Enforces absolute TTL caps to prevent indefinite renewal.</li>
 *   <li>Ensures atomic creation of meta if absent when saving snapshots.</li>
 * </ul>
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Single-key operations are used for predictable performance.</li>
 *   <li>Meta and snapshots are stored under separate keys (see {@link RedisKeys}).</li>
 *   <li>Timestamps are not tracked by Redis; snapshots use {@link Instant#EPOCH} as sentinel.</li>
 *   <li>Failures during encoding/decoding are rethrown, not swallowed.</li>
 * </ul>
 *
 * <h2>Sliding TTL &amp; Absolute Cap</h2>
 * <ul>
 *   <li><b>Sliding TTL:</b> When enabled, the TTL window is refreshed on reads and/or writes
 *       according to {@link FlowSentinelRedisProperties.SlidingReset}.</li>
 *   <li><b>Absolute Cap:</b> An optional max total lifetime for a flow. If configured, a per-flow
 *       absolute deadline is recorded on first creation; later sliding renewals never extend
 *       beyond this deadline.</li>
 *   <li>Cap keys follow the pattern {@code <namespace>:<flowId>:cap} and store the epoch millisecond
 *       when the flow should definitively expire.</li>
 * </ul>
 *
 * <h2>Logging</h2>
 * <ul>
 *   <li>INFO: only for life-cycle or destructive operations (e.g. deletes).</li>
 *   <li>DEBUG: includes key names and payload sizes, never sensitive data.</li>
 *   <li>No logging in catch blocks – exceptions propagate to the caller.</li>
 * </ul>
 *
 * <h2>SOLID compliance</h2>
 * <ul>
 *   <li><b>SRP</b>: only persistence concerns are handled here.</li>
 *   <li><b>OCP</b>: key format and codec are isolated for replacement without modification.</li>
 * </ul>
 *
 * @author gokalp
 */
public final class RedisFlowStore implements FlowStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFlowStore.class);

    private final StringRedisTemplate redis;
    private final ValueOperations<String, String> ops;
    private final RedisKeys keys;
    private final Duration snapshotTtl;
    private final Duration metaTtl;

    // Sliding TTL configuration
    private final boolean slidingEnabled;
    private final FlowSentinelRedisProperties.SlidingReset slidingReset;
    private final Duration absoluteCap;

    /**
     * Creates a Redis flow store with separate TTL settings for snapshots and meta data.
     *
     * @param redis          non-null {@link StringRedisTemplate} for Redis operations
     * @param namespace      non-blank namespace prefix for all keys
     * @param snapshotTtl    TTL for snapshot entries; null or zero disables expiration
     * @param metaTtl        TTL for meta entries; null or zero disables expiration
     * @param slidingEnabled whether sliding TTL renewal is active
     * @param slidingReset   specifies when TTL should be renewed (READ, WRITE, or both)
     * @param absoluteCap    maximum total lifetime; null disables cap enforcement
     * @throws IllegalArgumentException if redis is null or namespace is blank
     */
    public RedisFlowStore(StringRedisTemplate redis,
                          String namespace,
                          Duration snapshotTtl,
                          Duration metaTtl,
                          boolean slidingEnabled,
                          FlowSentinelRedisProperties.SlidingReset slidingReset,
                          Duration absoluteCap) {
        Assert.notNull(redis, "StringRedisTemplate must not be null");
        Assert.hasText(namespace, "namespace must not be blank");

        this.redis = redis;
        this.ops = redis.opsForValue();
        this.keys = new RedisKeys(namespace);
        this.snapshotTtl = snapshotTtl;
        this.metaTtl = metaTtl;
        this.slidingEnabled = slidingEnabled;
        this.slidingReset = slidingReset;
        this.absoluteCap = absoluteCap;

        log.info("RedisFlowStore initialized with namespace='{}', sliding={}, absoluteCap={}",
                namespace, slidingEnabled, absoluteCap);
    }

    /**
     * Legacy constructor for backward compatibility with existing configurations.
     *
     * @param redis          non-null {@link StringRedisTemplate} for Redis operations
     * @param namespace      non-blank namespace prefix for all keys
     * @param snapshotTtl    TTL for snapshot entries; null or zero disables expiration
     * @param metaTtl        TTL for meta entries; null or zero disables expiration
     * @param slidingEnabled whether sliding TTL renewal is active
     * @throws IllegalArgumentException if redis is null or namespace is blank
     */
    public RedisFlowStore(StringRedisTemplate redis,
                          String namespace,
                          Duration snapshotTtl,
                          Duration metaTtl,
                          boolean slidingEnabled) {
        this(redis, namespace, snapshotTtl, metaTtl, slidingEnabled,
                FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE, null);
    }

    @Override
    public void saveSnapshot(FlowSnapshot snapshot) {
        Assert.notNull(snapshot, "FlowSnapshot must not be null");
        Assert.hasText(snapshot.flowId(), "FlowSnapshot flowId must not be blank");

        String flowId = snapshot.flowId();
        String snapshotKey = keys.snapshotKey(flowId);
        String metaKey = keys.metaKey(flowId);

        log.debug("Saving snapshot for flow='{}', payload size={} bytes",
                flowId, snapshot.payload().length());

        // Save snapshot payload
        ops.set(snapshotKey, snapshot.payload());
        applyOrRenewTtl(snapshotKey, snapshotTtl, "snapshot", flowId, false);

        // Create meta if absent (atomic operation)
        String encodedMeta = MetaCodec.encode(new FlowMeta(
                flowId, "CREATED", "INIT", 0, snapshot.createdAt(), snapshot.updatedAt()
        ));

        boolean metaCreated = Boolean.TRUE.equals(ops.setIfAbsent(metaKey, encodedMeta));
        if (metaCreated) {
            log.debug("Created new meta for flow='{}'", flowId);
            applyOrRenewTtl(metaKey, metaTtl, "meta", flowId, false);
            recordAbsoluteCapIfConfigured(flowId);
        } else {
            log.debug("Meta already exists for flow='{}'", flowId);
            applyOrRenewTtl(metaKey, metaTtl, "meta", flowId, false);
        }
    }

    @Override
    public Optional<FlowSnapshot> loadSnapshot(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");

        String key = keys.snapshotKey(flowId);
        log.debug("Loading snapshot for flow='{}'", flowId);

        try {
            String payload = ops.get(key);
            if (payload == null) {
                log.debug("No snapshot found for flow='{}'", flowId);
                return Optional.empty();
            }

            applyOrRenewTtl(key, snapshotTtl, "snapshot", flowId, true);

            FlowSnapshot snapshot = new FlowSnapshot(
                    flowId, payload, "application/json", Instant.EPOCH, Instant.EPOCH
            );

            log.debug("Loaded snapshot for flow='{}', payload size={} bytes",
                    flowId, payload.length());
            return Optional.of(snapshot);

        } catch (Exception e) {
            log.debug("Failed to load snapshot for flow='{}'", flowId);
            throw new DataRetrievalFailureException(
                    "Failed to load snapshot for flow: " + flowId, e);
        }
    }

    @Override
    public void saveMeta(FlowMeta meta) {
        Assert.notNull(meta, "FlowMeta must not be null");
        Assert.hasText(meta.flowId(), "FlowMeta flowId must not be blank");

        String flowId = meta.flowId();
        String key = keys.metaKey(flowId);
        String encoded = MetaCodec.encode(meta);

        log.debug("Saving meta for flow='{}', status='{}', step='{}'",
                flowId, meta.status(), meta.step());

        ops.set(key, encoded);
        applyOrRenewTtl(key, metaTtl, "meta", flowId, false);
        recordAbsoluteCapIfConfigured(flowId);
    }

    @Override
    public Optional<FlowMeta> loadMeta(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");

        String key = keys.metaKey(flowId);
        log.debug("Loading meta for flow='{}'", flowId);

        try {
            String encoded = ops.get(key);
            if (encoded == null) {
                log.debug("No meta found for flow='{}'", flowId);
                return Optional.empty();
            }

            applyOrRenewTtl(key, metaTtl, "meta", flowId, true);

            FlowMeta meta = MetaCodec.decode(flowId, encoded);
            log.debug("Loaded meta for flow='{}', status='{}', step='{}'",
                    flowId, meta.status(), meta.step());
            return Optional.of(meta);

        } catch (Exception e) {
            log.debug("Failed to load meta for flow='{}'", flowId);
            throw new DataRetrievalFailureException(
                    "Failed to load meta for flow: " + flowId, e);
        }
    }

    @Override
    public boolean delete(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");

        String snapshotKey = keys.snapshotKey(flowId);
        String metaKey = keys.metaKey(flowId);
        String capKey = keys.capKey(flowId);

        log.info("Deleting flow='{}' (snapshot, meta, and cap keys)", flowId);

        Long deletedCount = redis.delete(List.of(snapshotKey, metaKey, capKey));
        boolean hasDeleted = deletedCount != null && deletedCount > 0;

        if (hasDeleted) {
            log.info("Successfully deleted {} keys for flow='{}'", deletedCount, flowId);
        } else {
            log.debug("No keys found to delete for flow='{}'", flowId);
        }

        return hasDeleted;
    }


    @Override
    public boolean exists(String flowId) {
        Assert.hasText(flowId, "flowId must not be blank");

        String metaKey = keys.metaKey(flowId);
        log.debug("Checking existence for flow='{}'", flowId);

        Boolean exists = redis.hasKey(metaKey);
        boolean result = exists != null && exists;

        log.debug("Flow='{}' exists={}", flowId, result);
        return result;
    }

    /**
     * Records an absolute expiration timestamp for the given flow if absolute cap is configured.
     * This is called only during the initial creation of a flow to establish the hard deadline.
     *
     * @param flowId the flow identifier
     */
    private void recordAbsoluteCapIfConfigured(String flowId) {
        if (absoluteCap == null || absoluteCap.isZero() || absoluteCap.isNegative()) {
            return;
        }

        String capKey = keys.capKey(flowId);
        long absoluteDeadline = Instant.now().plus(absoluteCap).toEpochMilli();

        // Only set if absent to avoid extending the original deadline
        boolean wasSet = Boolean.TRUE.equals(ops.setIfAbsent(capKey, String.valueOf(absoluteDeadline)));

        if (wasSet) {
            log.debug("Recorded absolute cap for flow='{}', deadline={}", flowId, absoluteDeadline);
            // Set TTL slightly longer than the cap to allow cleanup
            redis.expire(capKey, absoluteCap.plusMinutes(5));
        }
    }

    /**
     * Applies base TTL or renews TTL according to sliding configuration,
     * respecting the absolute cap if configured.
     *
     * @param key     Redis key to expire
     * @param baseTtl base TTL window
     * @param kind    "snapshot" or "meta" (for logs)
     * @param flowId  flow identifier (for cap key)
     * @param isRead  true if called from a read-path; false for a write-path
     */
    private void applyOrRenewTtl(String key, Duration baseTtl, String kind, String flowId, boolean isRead) {
        if (baseTtl == null || baseTtl.isZero() || baseTtl.isNegative()) {
            return;
        }

        boolean shouldRenew = slidingEnabled && (
                (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ && isRead)
                        || (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_WRITE && !isRead)
                        || (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)
        );

        if (isRead && !shouldRenew) {
            // Read path: only renew if sliding is configured for reads
            return;
        }

        // Write path: always apply TTL (either base or renew based on sliding config)
        applyWithCap(key, baseTtl, kind, flowId, shouldRenew);
    }

    /**
     * Applies TTL to a key, respecting absolute cap constraints if configured.
     *
     * @param key       Redis key to expire
     * @param baseTtl   base TTL duration
     * @param kind      "snapshot" or "meta" (for logs)
     * @param flowId    flow identifier
     * @param isRenewal true if this is a sliding renewal; false for initial TTL
     */
    private void applyWithCap(String key, Duration baseTtl, String kind, String flowId, boolean isRenewal) {
        Duration effectiveTtl = baseTtl;

        // Check absolute cap if configured
        if (absoluteCap != null && !absoluteCap.isZero() && !absoluteCap.isNegative()) {
            String capKey = keys.capKey(flowId);
            String capValue = ops.get(capKey);

            if (capValue != null) {
                try {
                    long deadline = Long.parseLong(capValue);
                    long now = Instant.now().toEpochMilli();
                    long remainingMs = deadline - now;

                    if (remainingMs <= 0) {
                        log.debug("Flow='{}' has exceeded absolute cap, skipping TTL renewal", flowId);
                        return;
                    }

                    Duration remaining = Duration.ofMillis(remainingMs);
                    if (remaining.compareTo(baseTtl) < 0) {
                        effectiveTtl = remaining;
                        log.debug("Capping TTL for flow='{}' {} to remaining={} (base={})",
                                flowId, kind, remaining, baseTtl);
                    }
                } catch (NumberFormatException e) {
                    log.debug("Invalid cap value for flow='{}', ignoring: {}", flowId, capValue);
                }
            }
        }

        redis.expire(key, effectiveTtl);

        if (log.isDebugEnabled()) {
            String action = isRenewal ? "Renewed" : "Set";
            log.debug("{} {} TTL for flow='{}', duration={}", action, kind, flowId, effectiveTtl);
        }
    }
}