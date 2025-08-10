package com.flowsentinel.store.redis.config;

import com.flowsentinel.core.store.FlowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for RedisStorageAutoConfiguration.
 * <p>
 * Notes:
 * - Context runner supplies a StringRedisTemplate.
 * - Verifies that a FlowStore bean is created when conditions are met.
 * <p>
 * author gokalp
 */
public class RedisStorageAutoConfigurationTest {

    private static RedisServer server;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
            .withBean(FlowSentinelRedisProperties.class)
            .withBean(StringRedisTemplate.class, () -> {
                var factory = new LettuceConnectionFactory("localhost", 6379);
                factory.afterPropertiesSet();
                return new StringRedisTemplate(factory);
            });

    @BeforeAll
    static void startEmbeddedRedis() throws Exception {
        server = new RedisServer(6379);
        server.start();
    }

    @AfterAll
    static void stopEmbeddedRedis() throws IOException {
        if (server != null) server.stop();
    }

    @Test
    void shouldCreateFlowStoreBeanWhenTemplatePresent() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(StringRedisTemplate.class);
            assertThat(ctx).hasSingleBean(FlowStore.class);
            assertThat(ctx.getBean(FlowStore.class)).isNotNull();
        });
    }
}
