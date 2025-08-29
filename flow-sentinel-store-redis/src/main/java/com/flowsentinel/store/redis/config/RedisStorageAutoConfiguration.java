package com.flowsentinel.store.redis.config;

import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.core.RedisFlowStore;
import io.lettuce.core.resource.ClientResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * Autoconfiguration for the FlowSentinel Redis storage module.
 *
 * <p>This configuration wires a {@link FlowStore} backed by Redis and exposes two
 * connection modes (see {@link FlowSentinelRedisProperties.Mode}) controlled by the property
 * <code>flow-sentinel.storage.mode</code>:</p>
 *
 * <ul>
 *   <li><b>{@code shared}</b> – Reuse the application's existing {@link RedisConnectionFactory}.
 *       Requires that another factory bean is already present in the context. This is the default.</li>
 *   <li><b>{@code dedicated}</b> – Create a dedicated {@link LettuceConnectionFactory} using
 *       {@link FlowSentinelRedisProperties} (host, port, db, password, timeouts).</li>
 * </ul>
 *
 * <h2>Expiration semantics</h2>
 * <p>FlowSentinel supports both absolute and sliding TTL strategies (see
 * {@link FlowSentinelRedisProperties#getTtlSeconds()},
 * {@link FlowSentinelRedisProperties#isSlidingEnabled()},
 * {@link FlowSentinelRedisProperties#getSlidingReset()},
 * {@link FlowSentinelRedisProperties#getAbsoluteTtlSeconds()}). When sliding is enabled, the
 * store implementation refreshes the TTL window on read and/or write according to
 * {@code sliding-reset}. Optionally, an absolute cap can be applied to ensure records never
 * exceed a maximum total lifetime even under constant access.</p>
 *
 * <h2>Typical configuration</h2>
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
 *       ttl-seconds: 3600
 *       sliding-enabled: true
 *       sliding-reset: ON_READ           # ON_READ | ON_WRITE | ON_READ_AND_WRITE
 *       absolute-ttl-seconds: 86400      # 0 to disable absolute cap
 *       # connect-timeout-ms: 2000
 *       # command-timeout-ms: 2000
 * </pre>
 *
 * <p>This configuration is loaded after Spring Boot’s standard {@link RedisAutoConfiguration}.</p>
 *
 * <p><b>Logging policy:</b> This class uses:
 * <ul>
 *   <li><b>INFO</b> for high-level mode/feature selection (shared vs dedicated, sliding TTL on/off).</li>
 *   <li><b>DEBUG</b> for detailed parameters (host/port/db, timeouts, reset policy, caps).</li>
 *   <li><b>WARN</b> for suspicious/edge configurations (e.g., sliding enabled with non-positive TTL).</li>
 * </ul>
 * </p>
 *
 * <p><b>Thread-safety:</b> All beans are singletons managed by Spring; construction is thread-safe.</p>
 *
 * <p><b>Failure handling:</b> If a required bean is not present, conditional annotations prevent
 * the conflicting bean from being created. Connection failures will surface from the underlying
 * Lettuce/Spring Data Redis classes during context initialization.</p>
 *
 * @author gokalp
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableConfigurationProperties(FlowSentinelRedisProperties.class)
public class RedisStorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisStorageAutoConfiguration.class);

    /**
     * Provides a {@link RedisConnectionFactory} that reuses the application's existing Redis connection.
     *
     * <p><b>Activation conditions:</b></p>
     * <ul>
     *   <li>{@code flow-sentinel.storage.mode=shared} OR property is missing</li>
     *   <li>An existing {@link RedisConnectionFactory} is available in the Spring context</li>
     * </ul>
     *
     * <p>This method returns the application's factory directly. A separate bean name is used only
     * when a dedicated factory is created (see
     * {@link #dedicatedRedisConnectionFactory(FlowSentinelRedisProperties, ObjectProvider)}).</p>
     *
     * @param appRedisFactory the application's existing {@link RedisConnectionFactory}
     * @return the shared {@link RedisConnectionFactory} instance
     */
    @Bean
    @ConditionalOnProperty(name = "flow-sentinel.storage.mode", havingValue = "shared", matchIfMissing = true)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "flowSentinelRedisConnectionFactory")
    public RedisConnectionFactory sharedRedisConnectionFactory(RedisConnectionFactory appRedisFactory) {
        log.info("FlowSentinel Redis: using SHARED RedisConnectionFactory from application context");
        log.debug("FlowSentinel Redis (shared) factory type: {}", appRedisFactory.getClass().getSimpleName());
        return appRedisFactory;
    }

    /**
     * Creates a dedicated {@link LettuceConnectionFactory} bean for FlowSentinel storage.
     *
     * <p><b>Activation condition:</b> {@code flow-sentinel.storage.mode=dedicated}</p>
     *
     * <p>Reads the following properties from {@link FlowSentinelRedisProperties}:</p>
     * <ul>
     *   <li>{@code host}, {@code port}, {@code database}, {@code password}</li>
     *   <li>{@code connect-timeout-ms}, {@code command-timeout-ms}</li>
     * </ul>
     *
     * <p>If a non-empty password is provided, authentication is enabled.</p>
     *
     * @param props           FlowSentinel Redis-specific configuration properties
     * @param clientResources optional shared Lettuce {@link ClientResources} (injected if present)
     * @return a new {@link LettuceConnectionFactory} dedicated to FlowSentinel
     */
    @Bean(name = "flowSentinelRedisConnectionFactory")
    @ConditionalOnProperty(name = "flow-sentinel.storage.mode", havingValue = "dedicated")
    public RedisConnectionFactory dedicatedRedisConnectionFactory(FlowSentinelRedisProperties props,
                                                                  ObjectProvider<ClientResources> clientResources) {
        log.info("FlowSentinel Redis: creating DEDICATED LettuceConnectionFactory (host={}, port={}, db={})",
                props.getHost(), props.getPort(), props.getDatabase());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(props.getHost());
        config.setPort(props.getPort());
        config.setDatabase(props.getDatabase());

        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            config.setPassword(RedisPassword.of(props.getPassword()));
            log.debug("FlowSentinel Redis: authentication is ENABLED");
        }
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder();

        // Command timeout
        if (props.getCommandTimeoutMs() > 0) {
            builder.commandTimeout(Duration.ofMillis(props.getCommandTimeoutMs()));
            log.debug("FlowSentinel Redis: commandTimeout={}ms", props.getCommandTimeoutMs());
        }

        // Connect timeout: Spring Data Redis does not expose a dedicated connectTimeout,
        // we treat connectTimeoutMs as a fallback for commandTimeout if commandTimeoutMs is not set.
        else if (props.getConnectTimeoutMs() > 0) {
            builder.commandTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
            log.debug("FlowSentinel Redis: connectTimeout={}ms mapped to commandTimeout", props.getConnectTimeoutMs());
        }
        // Optional ClientResources
        if(clientResources.getIfAvailable() != null) {
            builder.clientResources(Objects.requireNonNull(clientResources.getIfAvailable()));
            log.debug("FlowSentinel Redis: using shared Lettuce ClientResources");
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, builder.build());
        factory.afterPropertiesSet();
        log.info("FlowSentinel Redis: dedicated LettuceConnectionFactory initialized successfully");
        return factory;
    }

    /**
     * Registers the {@link FlowStore} Redis implementation with sliding TTL support.
     *
     * <p>This bean uses whichever connection factory is active:</p>
     * <ul>
     *   <li>{@link #sharedRedisConnectionFactory(RedisConnectionFactory)} when mode = {@code shared}</li>
     *   <li>{@link #dedicatedRedisConnectionFactory(FlowSentinelRedisProperties, ObjectProvider)} when mode = {@code dedicated}</li>
     * </ul>
     *
     * <p>The store is configured with:
     * <ul>
     *   <li><b>Key prefix</b> from {@link FlowSentinelRedisProperties#getKeyPrefix()}.</li>
     *   <li><b>Base TTL window</b> from {@link FlowSentinelRedisProperties#getTtlSeconds()}.</li>
     *   <li><b>Sliding TTL</b> controlled by
     *       {@link FlowSentinelRedisProperties#isSlidingEnabled()} and
     *       {@link FlowSentinelRedisProperties#getSlidingReset()}.</li>
     *   <li><b>Optional absolute cap</b> from
     *       {@link FlowSentinelRedisProperties#getAbsoluteTtlSeconds()} (0 = disabled).</li>
     * </ul>
     * </p>
     *
     * <p><b>Important:</b> The actual refresh-on-access logic is implemented inside {@link RedisFlowStore}.
     * This configuration only wires the necessary parameters.</p>
     *
     * @param redisConnectionFactory the Redis connection factory (shared or dedicated)
     * @param props             Redis-specific properties for FlowSentinel
     * @return a {@link FlowStore} backed by Redis with sliding TTL semantics if enabled
     */
    @Bean
    @ConditionalOnMissingBean(FlowStore.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public FlowStore redisFlowStore(RedisConnectionFactory redisConnectionFactory,
                                    FlowSentinelRedisProperties props) {
        final var template = new StringRedisTemplate(redisConnectionFactory);

        final long ttlSeconds = Math.max(1, props.getTtlSeconds());
        final boolean slidingEnabled = props.isSlidingEnabled();
        final FlowSentinelRedisProperties.SlidingReset resetPolicy = props.getSlidingReset();
        final long absoluteCapSeconds = Math.max(0, props.getAbsoluteTtlSeconds());

        // High-level feature logging
        if (slidingEnabled) {
            log.info("FlowSentinel Redis: Sliding TTL is ENABLED (baseTTL={}s, resetPolicy={}, absoluteCap={}s)",
                    ttlSeconds, resetPolicy, absoluteCapSeconds);
        } else {
            log.info("FlowSentinel Redis: Sliding TTL is DISABLED (baseTTL={}s)", ttlSeconds);
        }

        // Sanity signals
        if (absoluteCapSeconds > 0 && absoluteCapSeconds < ttlSeconds) {
            log.warn("FlowSentinel Redis: absolute TTL cap ({}) is smaller than base TTL ({}); records may expire before the sliding window completes.",
                    absoluteCapSeconds, ttlSeconds);
        }

        // Debug details
        log.debug("FlowSentinel Redis: keyPrefix='{}'", props.getKeyPrefix());

        // Construct the store with sliding parameters
        return new RedisFlowStore(
                template,
                props.getKeyPrefix(),
                Duration.ofSeconds(ttlSeconds),
                slidingEnabled,
                resetPolicy,
                Duration.ofSeconds(absoluteCapSeconds)
        );
    }
}
