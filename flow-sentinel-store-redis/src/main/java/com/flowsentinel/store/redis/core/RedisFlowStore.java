package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.core.store.FlowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link FlowStore}.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Single-key operations for predictable performance.</li>
 *   <li>Snapshots are stored as raw JSON strings.</li>
 *   <li>Meta is stored as a compact key-value string via {@link MetaCodec}.</li>
 *   <li>TTL is optional per data type to control retention.</li>
 * </ul>
 *
 * <h2>Logging</h2>
 * <ul>
 *   <li>INFO: high-level life-cycle or notable state changes (kept minimal in store layer)</li>
 *   <li>DEBUG: keys and sizes for troubleshooting (<em>no sensitive data</em>)</li>
 *   <li>No logging inside catch blocks; failures are rethrown to the caller.</li>
 * </ul>
 *
 * <h2>SOLID</h2>
 * <ul>
 *   <li>SRP: only persistence concerns here.</li>
 *   <li>OCP: codec and key strategy are isolated and replaceable.</li>
 * </ul>
 * <p>
 * author gokalp
 */
public final class RedisFlowStore implements FlowStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFlowStore.class);

    private final StringRedisTemplate redis;
    private final ValueOperations<String, String> ops;
    private final RedisKeys keys;
    private final Duration snapshotTtl;
    private final Duration metaTtl;

    /**
     * Creates a new RedisFlowStore.
     *
     * @param redis       non-null Redis template
     * @param keyPrefix   non-null prefix for namespacing keys
     * @param snapshotTtl expiration for snapshots (zero/negative disables)
     * @param metaTtl     expiration for meta entries (zero/negative disables)
     */
    public RedisFlowStore(
            StringRedisTemplate redis,
            String keyPrefix,
            Duration snapshotTtl,
            Duration metaTtl
    ) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.ops = this.redis.opsForValue();
        this.keys = new RedisKeys(Objects.requireNonNull(keyPrefix, "keyPrefix must not be null"));
        this.snapshotTtl = snapshotTtl != null ? snapshotTtl : Duration.ZERO;
        this.metaTtl = metaTtl != null ? metaTtl : Duration.ZERO;
    }

    @Override
    public void saveMeta(FlowMeta meta) {
        Assert.notNull(meta, "meta must not be null");
        String key = keys.meta(meta.flowId());
        String payload = MetaCodec.encode(meta);

        if (log.isDebugEnabled()) {
            log.debug("saveMeta key={} version={}", key, meta.version());
        }

        ops.set(key, payload);
        applyTtlIfConfigured(key, metaTtl);
    }

    @Override
    public Optional<FlowMeta> loadMeta(String flowId) {
        String key = keys.meta(flowId);
        String payload = ops.get(key);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MetaCodec.decode(flowId, payload));
        } catch (RuntimeException e) {
            // Do not log here; let the upper layers decide. Keep context in an exception message.
            throw new DataRetrievalFailureException("Failed to decode meta for key=" + key, e);
        }
    }

    @Override
    public void saveSnapshot(FlowSnapshot snapshot) {
        Assert.notNull(snapshot, "snapshot must not be null");
        String key = keys.snap(snapshot.flowId());

        if (log.isDebugEnabled()) {
            int size = snapshot.payload() != null ? snapshot.payload().length() : 0;
            log.debug("saveSnapshot key={} size={}", key, size);
        }

        assert snapshot.payload() != null;
        ops.set(key, snapshot.payload());
        applyTtlIfConfigured(key, snapshotTtl);

        // Ensure meta exists; do not overwrite if it already does.
        createMetaIfAbsent(snapshot.flowId());
    }

    @Override
    public Optional<FlowSnapshot> loadSnapshot(String flowId) {
        String key = keys.snap(flowId);
        String payload = ops.get(key);
        if (payload == null) {
            return Optional.empty();
        }
        // Redis store does not track timestamps; use epoch to signal "not tracked".
        return Optional.of(new FlowSnapshot(flowId, payload, "application/json", Instant.EPOCH, Instant.EPOCH));
    }

    @Override
    public void deleteSnapshot(String flowId) {
        // Keep behavior consistent with the JDBC store: delete both snapshot and meta.
        redis.delete(keys.snap(flowId));
        redis.delete(keys.meta(flowId));

        if (log.isInfoEnabled()) {
            log.info("Deleted flow snapshot and meta for flowId={}", flowId);
        }
    }

    private void applyTtlIfConfigured(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) return;
        redis.expire(key, ttl);
    }

    private void createMetaIfAbsent(String flowId) {
        String key = keys.meta(flowId);
        // SET NX pattern (atomic). No logging here; this path is hot.
        redis.execute(connection ->
                Boolean.TRUE.equals(connection.stringCommands().setNX(
                        Objects.requireNonNull(redis.getStringSerializer().serialize(key)),
                        Objects.requireNonNull(redis.getStringSerializer().serialize(MetaCodec.encode(new FlowMeta(
                                flowId, "NEW", "INIT", 0, Instant.now(), Instant.now()
                        ))))
                )) ? Boolean.TRUE : Boolean.FALSE, true
        );
        applyTtlIfConfigured(key, metaTtl);
    }

}
