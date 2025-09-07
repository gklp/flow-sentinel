package com.flowsentinel.store.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.core.RedisFlowStore;
import io.lettuce.core.resource.ClientResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Auto-configures the Redis-based storage layer for FlowSentinel.
 * <p>
 * This configuration class is responsible for setting up the {@link FlowStore} bean
 * that persists flow states to a Redis server. It operates in one of two modes,
 * configured via the {@code flow-sentinel.storage.mode} property:
 *
 * <ul>
 *   <li><b>{@code shared} (Default):</b> Reuses the primary {@link RedisConnectionFactory}
 *       already defined in the application's context. This is the most common and efficient
 *       mode, avoiding the creation of redundant connections.</li>
 *   <li><b>{@code dedicated}:</b> Creates a separate, sandboxed {@link RedisConnectionFactory}
 *       exclusively for FlowSentinel. This is useful when the main application does not use
 *       Redis, or when flow data needs to be stored on a different Redis instance.</li>
 * </ul>
 * <p>
 * The configuration is split into nested static classes to ensure that beans for one mode
 * are not accidentally created for the other.
 *
 * @see FlowSentinelRedisProperties for configuration options.
 * @see RedisFlowStore for the concrete store implementation.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RedisConnectionFactory.class, FlowStore.class})
@EnableConfigurationProperties(FlowSentinelRedisProperties.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RedisStorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisStorageAutoConfiguration.class);

    /**
     * Inner configuration class for the <b>DEDICATED</b> connection mode.
     * <p>
     * This configuration is activated when {@code flow-sentinel.storage.mode=dedicated}.
     * It is responsible for creating a completely independent {@link LettuceConnectionFactory}
     * bean named {@code flowSentinelRedisConnectionFactory} and a {@link RedisFlowStore}
     * that uses it. This ensures total isolation from the main application's Redis configuration, if any.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "flow-sentinel.storage.mode", havingValue = "dedicated")
    static class DedicatedModeConfiguration {

        /**
         * Creates a dedicated {@link RedisConnectionFactory} for FlowSentinel.
         * <p>
         * This bean is only created if no other bean with the name
         * {@code flowSentinelRedisConnectionFactory} exists. It is configured using properties
         * defined in {@link FlowSentinelRedisProperties} (e.g., host, port, password).
         *
         * @param props           The FlowSentinel-specific Redis properties.
         * @param clientResources An {@link ObjectProvider} for optional Lettuce {@link ClientResources}.
         * @return A new {@link LettuceConnectionFactory} instance for FlowSentinel's exclusive use.
         */
        @Bean
        @ConditionalOnMissingBean(name = "flowSentinelRedisConnectionFactory")
        public RedisConnectionFactory flowSentinelRedisConnectionFactory(
                FlowSentinelRedisProperties props,
                ObjectProvider<ClientResources> clientResources) {

            log.info("FlowSentinel Redis: creating DEDICATED LettuceConnectionFactory (host={}, port={}, db={})",
                    props.getHost(), props.getPort(), props.getDatabase());

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(props.getHost());
            config.setPort(props.getPort());
            config.setDatabase(props.getDatabase());

            if (props.getPassword() != null && !props.getPassword().isBlank()) {
                config.setPassword(RedisPassword.of(props.getPassword()));
            }

            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
            if (props.getCommandTimeoutMs() > 0) {
                builder.commandTimeout(Duration.ofMillis(props.getCommandTimeoutMs()));
            }
            clientResources.ifAvailable(builder::clientResources);

            LettuceConnectionFactory factory = new LettuceConnectionFactory(config, builder.build());
            factory.afterPropertiesSet();
            return factory;
        }

        /**
         * Creates the {@link FlowStore} bean using the dedicated connection factory.
         * <p>
         * This bean is only created if no other {@link FlowStore} has been defined. It is
         * explicitly wired to the dedicated factory using {@code @Qualifier}.
         *
         * @param connectionFactory The dedicated {@link RedisConnectionFactory} created for FlowSentinel.
         * @param properties        The Redis storage configuration properties.
         * @return A {@link RedisFlowStore} instance configured for dedicated mode.
         */
        @Bean
        @ConditionalOnMissingBean(FlowStore.class)
        public FlowStore redisFlowStore(
                @Qualifier("flowSentinelRedisConnectionFactory") RedisConnectionFactory connectionFactory,
                ObjectMapper objectMapper,
                FlowSentinelRedisProperties properties) {

            log.debug("Creating RedisFlowStore with DEDICATED connection factory.");
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            return new RedisFlowStore(
                    template,
                    objectMapper,
                    properties
            );
        }
    }

    /**
     * Inner configuration class for the <b>SHARED</b> connection mode.
     * <p>
     * This configuration is activated by default, or when {@code flow-sentinel.storage.mode=shared}.
     * It looks for a pre-existing {@link RedisConnectionFactory} bean in the application context
     * and uses it to create the {@link RedisFlowStore}. This avoids creating a new connection
     * pool and allows FlowSentinel to share resources with the main application.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "flow-sentinel.storage.mode", havingValue = "shared", matchIfMissing = true)
    static class SharedModeConfiguration {

        /**
         * Creates the {@link FlowStore} bean using the application's existing connection factory.
         * <p>
         * This bean is only created if a {@link RedisConnectionFactory} is available in the
         * context and no other {@link FlowStore} bean has been defined. It directly injects
         * the single/primary connection factory from the parent context.
         *
         * @param connectionFactory The application's primary/single {@link RedisConnectionFactory}.
         * @param properties        The Redis storage configuration properties.
         * @return A {@link RedisFlowStore} instance configured for shared mode.
         */
        @Bean
        @ConditionalOnMissingBean(FlowStore.class)
        @ConditionalOnBean(RedisConnectionFactory.class)
        public FlowStore redisFlowStore(
                RedisConnectionFactory connectionFactory, // Injects the app's primary/single factory
                ObjectMapper objectMapper,
                FlowSentinelRedisProperties properties) {

            if (connectionFactory instanceof LettuceConnectionFactory lettuce) {
                lettuce.setValidateConnection(true);
                lettuce.setShareNativeConnection(false); // Thread safety
            }

            log.info("FlowSentinel Redis: using SHARED RedisConnectionFactory from application context.");
            log.debug("FlowSentinel Redis (shared) factory type: {}", connectionFactory.getClass().getSimpleName());
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.setEnableTransactionSupport(true);
            return new RedisFlowStore(
                    template,
                    objectMapper,
                    properties
            );
        }
    }
}