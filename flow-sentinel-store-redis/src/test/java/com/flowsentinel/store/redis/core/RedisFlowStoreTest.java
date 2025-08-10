package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisFlowStore.
 * <p>
 * <p>
 * Notes:
 * - Uses embedded Redis on localhost:6379.
 * - Keeps assertions focused on observable behavior.
 * - No display names; comments explain intent.
 * <p>
 * <p>
 * author gokalp
 */
@TestMethodOrder(OrderAnnotation.class)
public class RedisFlowStoreTest {

    private static RedisServer server;
    private static StringRedisTemplate template;

    @BeforeAll
    static void startEmbeddedRedis() throws Exception {
        server = new RedisServer(6379);
        server.start();
        var factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void stopEmbeddedRedis() throws IOException {
        if (server != null) server.stop();
    }

    // Verifies that a snapshot can be saved and retrieved as-is.
    @Test
    @Order(1)
    void shouldSaveAndLoadSnapshot() {
        var store = new RedisFlowStore(template, "fs:", Duration.ofHours(1), Duration.ZERO);
        var snap = new com.flowsentinel.core.store.FlowSnapshot("flow-1", "{\"a\":1}", "application/json", Instant.now(), Instant.now());

        store.saveSnapshot(snap);

        var loaded = store.loadSnapshot("flow-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().payload()).isEqualTo("{\"a\":1}");
    }

    // Ensures meta can be stored and decoded correctly.
    @Test
    @Order(2)
    void shouldSaveAndLoadMeta() {
        var store = new RedisFlowStore(template, "fs:", Duration.ofHours(1), Duration.ofMinutes(5));
        var now = Instant.now();
        var meta = new FlowMeta("flow-2", "RUNNING", "STEP-1", 2, now, now);

        store.saveMeta(meta);

        var loaded = store.loadMeta("flow-2");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().status()).isEqualTo("RUNNING");
        assertThat(loaded.get().step()).isEqualTo("STEP-1");
        assertThat(loaded.get().version()).isEqualTo(2);
    }

    // Confirms that delete removes both snapshot- and meta-keys to keep consistency.
    @Test
    @Order(3)
    void shouldDeleteSnapshotAndMetaTogether() {
        var store = new RedisFlowStore(template, "fs:", Duration.ofHours(1), Duration.ZERO);

        store.saveSnapshot(new FlowSnapshot("flow-3", "{\"x\":42}", "application/json", Instant.now(), Instant.now()));
        store.saveMeta(new FlowMeta("flow-3", "RUNNING", "STEP-X", 1, Instant.now(), Instant.now()));

        store.deleteSnapshot("flow-3");

        assertThat(store.loadSnapshot("flow-3")).isEmpty();
        assertThat(store.loadMeta("flow-3")).isEmpty();
    }
}
