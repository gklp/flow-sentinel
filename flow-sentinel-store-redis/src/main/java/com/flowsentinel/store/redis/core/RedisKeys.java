package com.flowsentinel.store.redis.core;

import java.util.Objects;

/**
 * Utility to build Redis keys consistently.
 *
 * <p>Keep the key format stable to ensure predictable compatibility across versions.</p>
 * <p>
 * author gokalp
 */
public final class RedisKeys {

    private final String prefix;

    public RedisKeys(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
    }

    public String meta(String flowId) {
        return prefix + "meta:" + require(flowId);
    }

    public String snap(String flowId) {
        return prefix + "snap:" + require(flowId);
    }

    private static String require(String id) {
        return Objects.requireNonNull(id, "flowId must not be null");
    }
}
