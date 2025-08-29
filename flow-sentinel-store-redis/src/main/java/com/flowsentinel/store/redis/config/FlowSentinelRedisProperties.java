package com.flowsentinel.store.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the FlowSentinel Redis storage module.
 *
 * <p>
 * These properties control how FlowSentinel persists flow snapshots and metadata in Redis,
 * including connection selection mode, key naming, and expiration (TTL) policies.
 * </p>
 *
 * <h2>Property prefix</h2>
 * <p><code>flow-sentinel.storage.redis</code></p>
 *
 * <h2>Expiration semantics</h2>
 * <ul>
 *   <li><b>Absolute TTL:</b> A fixed lifetime after which a record expires regardless of access.</li>
 *   <li><b>Sliding TTL:</b> The TTL window is refreshed on access (read and/or write), so
 *       actively used records keep living while idle ones expire.</li>
 *   <li>You can combine both: enable sliding TTL and also set an <i>absolute cap</i> to ensure
 *       records do not live forever under constant access.</li>
 * </ul>
 *
 * <h2>Example configuration (YAML)</h2>
 * <pre>
 * flow-sentinel:
 *   storage:
 *     mode: dedicated
 *     redis:
 *       host: localhost
 *       port: 6379
 *       database: 0
 *       # password: secret
 *       key-prefix: fs:flow:
 *       # Base TTL window used when writing and when sliding is triggered
 *       ttl-seconds: 3600
 *
 *       # --- Sliding TTL controls ---
 *       sliding-enabled: true
 *       # ON_READ | ON_WRITE | ON_READ_AND_WRITE
 *       sliding-reset: ON_READ
 *       # Optional absolute upper bound (0 = disabled).
 *       absolute-ttl-seconds: 86400
 *
 *       # Optional client-level timeouts (Lettuce)
 *       # connect-timeout-ms: 2000
 *       # command-timeout-ms: 2000
 * </pre>
 */
@ConfigurationProperties(prefix = "flow-sentinel.storage.redis")
public class FlowSentinelRedisProperties {

    /**
     * Connection selection mode for Redis.
     *
     * <ul>
     *   <li>{@link #SHARED SHARED}: Reuse the application's existing {@code RedisConnectionFactory}.
     *       Requires that another {@code RedisConnectionFactory} bean is already present in the Spring context.</li>
     *   <li>{@link #DEDICATED DEDICATED}: Create a dedicated {@code LettuceConnectionFactory} for FlowSentinel,
     *       using the host/port/database/password settings in this properties class.</li>
     * </ul>
     *
     * <p>Default: {@link #SHARED}</p>
     */
    public enum Mode {
        SHARED, DEDICATED
    }

    /**
     * Policy that determines when the TTL window is refreshed (sliding expiration).
     * <ul>
     *   <li>{@code ON_READ}: Refresh TTL on read-access operations.</li>
     *   <li>{@code ON_WRITE}: Refresh TTL on write/update operations.</li>
     *   <li>{@code ON_READ_AND_WRITE}: Refresh TTL on both read and write.</li>
     * </ul>
     * <p>
     * <b>Notes:</b>
     * <ul>
     *   <li>Write paths typically set the TTL anyway; {@code ON_WRITE} is useful if you want to ensure
     *       rewrites/updates also re-apply the full window.</li>
     *   <li>{@code ON_READ} is a common choice for session-like workloads.</li>
     * </ul>
     */
    public enum SlidingReset {
        ON_READ,
        ON_WRITE,
        ON_READ_AND_WRITE
    }

    /**
     * The connection selection mode. Defaults to {@link Mode#SHARED}.
     */
    private Mode mode = Mode.SHARED;

    /**
     * Time-to-live window for flow records in seconds.
     *
     * <p>This value determines the base TTL window used when writing a record and when a sliding
     * reset is triggered. A value less than or equal to 0 is not allowed and will be coerced
     * by the store implementation to a positive minimum (e.g., 1 second).</p>
     *
     * <p>Default: 3600 (1 hour)</p>
     */
    private long ttlSeconds = 3600;

    /**
     * Enables sliding TTL (sliding expiration).
     *
     * <p>When enabled, the store implementation should refresh the TTL window to
     * {@link #ttlSeconds} according to {@link #slidingReset} on eligible operations.</p>
     *
     * <p>Default: {@code false}</p>
     */
    private boolean slidingEnabled = false;

    /**
     * Determines on which operations the TTL window is refreshed when {@link #slidingEnabled} is true.
     *
     * <p>Default: {@link SlidingReset#ON_READ}</p>
     */
    private SlidingReset slidingReset = SlidingReset.ON_READ;

    /**
     * Optional absolute TTL cap in seconds.
     *
     * <p>When {@code > 0}, a record must not live longer than this total lifetime even if
     * sliding TTL continually refreshes the window. The store implementation is expected
     * to enforce the cap (e.g., by tracking the record's first-seen timestamp and choosing
     * the smaller of the remaining absolute lifetime and {@link #ttlSeconds} when setting
     * expiration).</p>
     *
     * <p>When {@code 0}, no absolute cap is enforced.</p>
     *
     * <p>Default: {@code 0} (disabled)</p>
     */
    private long absoluteTtlSeconds = 0;

    /**
     * Prefix used for all keys written by the FlowSentinel Redis store.
     *
     * <p>Example: if {@code keyPrefix = "fs:flow:"}, then a record with id {@code abc123}
     * will be stored under the Redis key {@code fs:flow:abc123}.</p>
     *
     * <p>Default: {@code "fs:flow:"}</p>
     */
    private String keyPrefix = "fs:flow:";

    // --- Dedicated connection settings (used only when mode = DEDICATED) ---

    /**
     * Redis host used when {@link #mode} = {@link Mode#DEDICATED}.
     *
     * <p>Default: {@code "localhost"}</p>
     */
    private String host;

    /**
     * Redis port used when {@link #mode} = {@link Mode#DEDICATED}.
     *
     * <p>Default: {@code 6379}</p>
     */
    private int port = 6379;

    /**
     * Redis logical database index used when {@link #mode} = {@link Mode#DEDICATED}.
     *
     * <p>Default: {@code 0}</p>
     */
    private int database = 0;

    /**
     * Optional Redis password used when {@link #mode} = {@link Mode#DEDICATED}.
     *
     * <p>If empty or {@code null}, password authentication is not configured.</p>
     */
    private String password;

    /**
     * Optional client connect timeout in milliseconds (Lettuce).
     *
     * <p>When {@code &gt; 0}, the dedicated client will apply this timeout. Otherwise,
     * the client uses its own defaults.</p>
     */
    private long connectTimeoutMs = 0;

    /**
     * Optional command timeout in milliseconds (Lettuce).
     *
     * <p>When {@code &gt; 0}, the dedicated client will apply this timeout for commands.
     * Otherwise, the client uses its own defaults.</p>
     */
    private long commandTimeoutMs = 0;

    // --- Getters / Setters ---

    /** Returns the current connection mode. */
    public Mode getMode() {
        return mode;
    }

    /** Sets the connection mode. */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** Returns the base TTL (in seconds) for flow records. */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Sets the base TTL (in seconds) for flow records.
     * @param ttlSeconds TTL in seconds; values &le; 0 will be coerced at runtime
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Whether sliding TTL is enabled.
     * @return {@code true} if sliding TTL is enabled; otherwise {@code false}
     */
    public boolean isSlidingEnabled() {
        return slidingEnabled;
    }

    /**
     * Enables or disables sliding TTL.
     * @param slidingEnabled {@code true} to enable sliding TTL
     */
    public void setSlidingEnabled(boolean slidingEnabled) {
        this.slidingEnabled = slidingEnabled;
    }

    /**
     * Returns the sliding reset policy.
     * @see SlidingReset
     */
    public SlidingReset getSlidingReset() {
        return slidingReset;
    }

    /**
     * Sets the sliding reset policy.
     * @param slidingReset {@link SlidingReset}
     */
    public void setSlidingReset(SlidingReset slidingReset) {
        this.slidingReset = slidingReset;
    }

    /**
     * Returns the absolute TTL cap in seconds (0 = disabled).
     */
    public long getAbsoluteTtlSeconds() {
        return absoluteTtlSeconds;
    }

    /**
     * Sets the absolute TTL cap in seconds.
     * @param absoluteTtlSeconds {@code 0} to disable; when {@code > 0}, records must not
     *                           outlive this total age even if sliding refreshes occur
     */
    public void setAbsoluteTtlSeconds(long absoluteTtlSeconds) {
        this.absoluteTtlSeconds = absoluteTtlSeconds;
    }

    /** Returns the key prefix used for Redis keys. */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Sets the key prefix used for Redis keys.
     * @param keyPrefix non-empty string; if blank, a default will be used by the store
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /** Returns the host for the dedicated Redis connection. */
    public String getHost() {
        return host;
    }

    /** Sets the host for the dedicated Redis connection. */
    public void setHost(String host) {
        this.host = host;
    }

    /** Returns the port for the dedicated Redis connection. */
    public int getPort() {
        return port;
    }

    /** Sets the port for the dedicated Redis connection. */
    public void setPort(int port) {
        this.port = port;
    }

    /** Returns the logical Redis database index for the dedicated connection. */
    public int getDatabase() {
        return database;
    }

    /** Sets the logical Redis database index for the dedicated connection. */
    public void setDatabase(int database) {
        this.database = database;
    }

    /** Returns the password for the dedicated Redis connection, if any. */
    public String getPassword() {
        return password;
    }

    /** Sets the password for the dedicated Redis connection. */
    public void setPassword(String password) {
        this.password = password;
    }

    /** Returns the connect timeout (ms) for the dedicated client. */
    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /** Sets the connect timeout (ms) for the dedicated client. */
    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /** Returns the command timeout (ms) for the dedicated client. */
    public long getCommandTimeoutMs() {
        return commandTimeoutMs;
    }

    /** Sets the command timeout (ms) for the dedicated client. */
    public void setCommandTimeoutMs(long commandTimeoutMs) {
        this.commandTimeoutMs = commandTimeoutMs;
    }
}
