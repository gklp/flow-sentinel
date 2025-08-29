package com.flowsentinel.store.redis.config;

import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.core.RedisFlowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedisStorageAutoConfiguration}.
 * Tests are grouped by functionality using nested classes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisStorageAutoConfigurationTest {

    private RedisServer redisServer;
    private int redisPort;

    @BeforeAll
    void setUp() throws IOException {
        redisPort = findFreePort();
        redisServer = new RedisServer(redisPort);
        redisServer.start();
    }

    @AfterAll
    void tearDown() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    private LettuceConnectionFactory newLettuceConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", redisPort);
        factory.afterPropertiesSet();
        return factory;
    }

    @Nested
    @DisplayName("Shared Connection Mode")
    class SharedConnectionModeTests {

        @Test
        void shouldCreateRedisFlowStoreBean() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean(RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory, bd -> bd.setPrimary(true))
                    .withPropertyValues("flow-sentinel.storage.mode=shared")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowStore.class);
                        assertThat(ctx.getBean(FlowStore.class)).isInstanceOf(RedisFlowStore.class);
                        assertThat(ctx).hasBean("sharedRedisConnectionFactory");
                    });
        }

        @Test
        void shouldFallbackToSharedModeWhenModeIsNotSpecified() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean(RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory, bd -> bd.setPrimary(true))
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowStore.class);
                        FlowSentinelRedisProperties props = ctx.getBean(FlowSentinelRedisProperties.class);
                        assertThat(props.getMode()).isEqualTo(FlowSentinelRedisProperties.Mode.SHARED);
                    });
        }

        @Test
        void shouldFailWhenMultipleRedisConnectionFactoriesExist() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean("firstFactory", RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory)
                    .withBean("secondFactory", RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory)
                    .withPropertyValues("flow-sentinel.storage.mode=shared")
                    .run(ctx -> assertThat(ctx).hasFailed());
        }
    }

    @Nested
    @DisplayName("Dedicated Connection Mode")
    class DedicatedConnectionModeTests {

        @Test
        void shouldCreateRedisFlowStoreBean() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withPropertyValues(
                            "flow-sentinel.storage.mode=dedicated",
                            "flow-sentinel.storage.redis.host=localhost",
                            "flow-sentinel.storage.redis.port=" + redisPort
                    )
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowStore.class);
                        assertThat(ctx.getBean(FlowStore.class)).isInstanceOf(RedisFlowStore.class);
                        assertThat(ctx).hasBean("flowSentinelRedisConnectionFactory");
                        assertThat(ctx).doesNotHaveBean("sharedRedisConnectionFactory");
                    });
        }

        @Test
        void shouldFailIfDedicatedModePropertiesAreMissing() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withPropertyValues("flow-sentinel.storage.mode=dedicated")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        assertThat(ctx).getFailure().isInstanceOf(BeanCreationException.class);
                    });
        }
    }


    @Nested
    @DisplayName("Conditional Bean Creation")
    class ConditionalBeanCreationTests {

        @Test
        void shouldNotCreateRedisFlowStoreWhenAnotherFlowStoreBeanExists() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean(RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory, bd -> bd.setPrimary(true))
                    .withBean("customFlowStore", FlowStore.class, TestFlowStore::new)
                    .withPropertyValues("flow-sentinel.storage.mode=shared")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowStore.class);
                        assertThat(ctx.getBean(FlowStore.class)).isInstanceOf(TestFlowStore.class);
                        assertThat(ctx.getBean(FlowStore.class)).isNotInstanceOf(RedisFlowStore.class);
                    });
        }

        @Test
        void shouldNotCreateRedisFlowStoreInSharedModeWhenNoConnectionFactoryExists() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withPropertyValues("flow-sentinel.storage.mode=shared")
                    .run(ctx -> {
                        assertThat(ctx).doesNotHaveBean(FlowStore.class);
                        assertThat(ctx).hasSingleBean(FlowSentinelRedisProperties.class);
                    });
        }
    }

    @Nested
    @DisplayName("Property Binding")
    class PropertyBindingTests {

        @Test
        void shouldApplyDefaultPropertiesWhenNotConfigured() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean(RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory, bd -> bd.setPrimary(true))
                    .withPropertyValues("flow-sentinel.storage.mode=shared")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowSentinelRedisProperties.class);
                        FlowSentinelRedisProperties props = ctx.getBean(FlowSentinelRedisProperties.class);
                        assertThat(props.getKeyPrefix()).isEqualTo("fs:flow:");
                        assertThat(props.getTtlSeconds()).isEqualTo(3600);
                        assertThat(props.isSlidingEnabled()).isFalse();
                        assertThat(props.getAbsoluteTtlSeconds()).isZero();
                        assertThat(props.getMode()).isEqualTo(FlowSentinelRedisProperties.Mode.SHARED);
                    });
        }

        @Test
        void shouldBindCustomPropertiesCorrectly() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean(RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory, bd -> bd.setPrimary(true))
                    .withPropertyValues(
                            "flow-sentinel.storage.mode=shared",
                            "flow-sentinel.storage.redis.key-prefix=test:",
                            "flow-sentinel.storage.redis.ttl-seconds=120",
                            "flow-sentinel.storage.redis.sliding-enabled=true",
                            "flow-sentinel.storage.redis.absolute-ttl-seconds=240"
                    )
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowSentinelRedisProperties.class);
                        FlowSentinelRedisProperties props = ctx.getBean(FlowSentinelRedisProperties.class);
                        assertThat(props.getKeyPrefix()).isEqualTo("test:");
                        assertThat(props.getTtlSeconds()).isEqualTo(120);
                        assertThat(props.isSlidingEnabled()).isTrue();
                        assertThat(props.getAbsoluteTtlSeconds()).isEqualTo(240);
                    });
        }

        @Test
        void shouldCreateRedisFlowStoreWithSlidingTtl() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RedisStorageAutoConfiguration.class))
                    .withBean(RedisConnectionFactory.class, RedisStorageAutoConfigurationTest.this::newLettuceConnectionFactory, bd -> bd.setPrimary(true))
                    .withPropertyValues(
                            "flow-sentinel.storage.redis.ttl-seconds=120",
                            "flow-sentinel.storage.redis.sliding-enabled=true"
                    )
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(FlowStore.class);
                        ctx.getBean(FlowStore.class);
                        // Further assertions can be added here to check internal state if needed
                    });
        }

    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static class TestFlowStore implements FlowStore {
        @Override
        public void saveSnapshot(FlowSnapshot snapshot) {
        }

        @Override
        public Optional<FlowSnapshot> loadSnapshot(String flowId) {
            return Optional.empty();
        }

        @Override
        public void saveMeta(FlowMeta meta) {
        }

        @Override
        public Optional<FlowMeta> loadMeta(String flowId) {
            return Optional.empty();
        }

        @Override
        public void delete(String flowId) {
        }

        @Override
        public boolean exists(String flowId) {
            return false;
        }
    }
}