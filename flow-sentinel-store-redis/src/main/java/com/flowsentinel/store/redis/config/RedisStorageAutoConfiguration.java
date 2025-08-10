package com.flowsentinel.store.redis.config;

import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.core.RedisFlowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Autoconfiguration that wires a Redis-based {@link FlowStore} when a {@link StringRedisTemplate}
 * is present in the application context.
 *
 * <p>Selection precedence is handled at the starter level. This configuration only declares
 * the Redis store bean when Redis is available and no other {@code FlowStore} is defined.</p>
 *
 * <p>Logging strategy:
 * <ul>
 *   <li>INFO: bean creation outcome</li>
 *   <li>DEBUG: configuration details</li>
 * </ul>
 * </p>
 * <p>
 * <p>
 * author gokalp
 */
@AutoConfiguration
@EnableConfigurationProperties(FlowSentinelRedisProperties.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisStorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisStorageAutoConfiguration.class);

    @Bean(name = "redisFlowStore")
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(FlowStore.class)
    public FlowStore redisFlowStore(StringRedisTemplate template, FlowSentinelRedisProperties props) {
        if (log.isDebugEnabled()) {
            log.debug("Creating RedisFlowStore with prefix='{}', snapshotTtl='{}', metaTtl='{}'",
                    props.getKeyPrefix(), props.getSnapshotTtl(), props.getMetaTtl());
        }
        var store = new RedisFlowStore(template, props.getKeyPrefix(), props.getSnapshotTtl(), props.getMetaTtl());
        log.info("FlowSentinel RedisFlowStore initialized");
        return store;
    }
}
