package com.flowsentinel.store.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for FlowSentinel Redis store.
 *
 * <p>Controls key prefix and entry TTLs. Keep values minimal and explicit to
 * avoid unexpected expiration in production.</p>
 *
 * @author gokalp
 */
@ConfigurationProperties(prefix = "flow-sentinel.redis")
public final class FlowSentinelRedisProperties {

    /**
     * Prefix used for all Redis keys. Example: {@code fs:}.
     */
    private String keyPrefix = "fs:";

    /**
     * Time-to-live for snapshot entries. Zero or negative means no expiration.
     */
    private Duration snapshotTtl = Duration.ofHours(24);

    /**
     * Time-to-live for meta-entries. Zero or negative means no expiration.
     */
    private Duration metaTtl = Duration.ZERO;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getSnapshotTtl() {
        return snapshotTtl;
    }

    public void setSnapshotTtl(Duration snapshotTtl) {
        this.snapshotTtl = snapshotTtl;
    }

    public Duration getMetaTtl() {
        return metaTtl;
    }

    public void setMetaTtl(Duration metaTtl) {
        this.metaTtl = metaTtl;
    }

}
