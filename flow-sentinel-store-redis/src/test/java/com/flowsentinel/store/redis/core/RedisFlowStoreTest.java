package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link RedisFlowStore} using embedded-redis.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisFlowStoreTest {

    private RedisServer redisServer;
    private int redisPort;
    private RedisFlowStore store;
    private StringRedisTemplate template;

    @BeforeAll
    void startRedis() throws IOException {
        redisPort = findFreePort();
        redisServer = new RedisServer(redisPort);
        redisServer.start();
    }

    @AfterAll
    void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory cf = new LettuceConnectionFactory("localhost", redisPort);
        cf.afterPropertiesSet();
        template = new StringRedisTemplate(cf);

        store = new RedisFlowStore(
                template,
                "fs:flow:",
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                false
        );
    }

    @Test
    void shouldSaveAndLoadMeta() {
        // Given
        String flowId = "ridMeta1";
        FlowMeta meta = FlowMeta.createNew(flowId);

        // When
        store.saveMeta(meta);

        // Then
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isPresent();
        assertThat(found.get().flowId()).isEqualTo(flowId);
        assertThat(found.get().status()).isEqualTo("NEW");
        assertThat(found.get().step()).isEqualTo("INIT");
        assertThat(found.get().version()).isEqualTo(0);
    }

    @Test
    void shouldSaveAndLoadSnapshotAndDelete() {
        // Given
        String flowId = "ridSnap1";
        FlowSnapshot snapshot = new FlowSnapshot(
                flowId,
                "{\"ok\":true}",
                "application/json",
                Instant.now(),
                Instant.now()
        );

        // When
        store.saveSnapshot(snapshot);

        // Then
        assertThat(store.loadSnapshot(flowId))
                .isPresent()
                .get()
                .satisfies(s -> {
                    assertThat(s.flowId()).isEqualTo(flowId);
                    assertThat(s.payload()).isEqualTo("{\"ok\":true}");
                    assertThat(s.contentType()).isEqualTo("application/json");
                });

        // And meta is created atomically if absent
        assertThat(store.loadMeta(flowId)).isPresent();

        // When (delete)
        store.delete(flowId);

        // Then
        assertThat(store.loadSnapshot(flowId)).isEmpty();
        assertThat(store.loadMeta(flowId)).isEmpty();
    }

    @Test
    void shouldRespectTtlForMetaAndSnapshot() throws InterruptedException {
        // Given (override with short TTL)
        store = new RedisFlowStore(
                template,
                "fs:flow:",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                false
        );
        String flowId = "ridTtl1";
        FlowSnapshot snapshot = new FlowSnapshot(
                flowId,
                "{\"ttl\":true}",
                "application/json",
                Instant.now(),
                Instant.now()
        );

        // When
        store.saveSnapshot(snapshot);

        // Then (immediately present)
        assertThat(store.loadSnapshot(flowId)).isPresent();
        assertThat(store.loadMeta(flowId)).isPresent();

        // When (wait for TTL to expire)
        Thread.sleep(1500);

        // Then (expired)
        assertThat(store.loadSnapshot(flowId)).isEmpty();
        assertThat(store.loadMeta(flowId)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoData() {
        // Given
        String flowId = "ridMissing";

        // When / Then
        assertThat(store.loadSnapshot(flowId)).isEmpty();
        assertThat(store.loadMeta(flowId)).isEmpty();
    }

    @Test
    void shouldThrowWhenMetaCorruptedOnLoad() {
        // Given
        String flowId = "ridCorrupt";
        String metaKey = "fs:flow:" + flowId + ":meta"; // Manuel key format
        // Malformed: not 5 fields (should be version|status|step|createdAtEpochMilli|updatedAtEpochMilli)
        template.opsForValue().set(metaKey, "0|NEW|INIT|123"); // Field 4 instead of 5

        // When / Then - AssertJ kullanarak exception test et
        assertThatThrownBy(() -> store.loadMeta(flowId))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("Failed to load meta for flow: " + flowId);
    }

    @Test
    void shouldCreateMetaAtomicallyUnderParallelSnapshotSaves() throws InterruptedException {
        // Given
        String flowId = "ridParallel";
        int writers = 8;
        int iterationsPerWriter = 50;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            AtomicInteger errors = new AtomicInteger(0);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(writers);

            // When
            for (int w = 0; w < writers; w++) {
                int writerId = w;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < iterationsPerWriter; i++) {
                            FlowSnapshot snapshot = new FlowSnapshot(
                                    flowId,
                                    "{\"writer\":" + writerId + ",\"i\":" + i + "}",
                                    "application/json",
                                    Instant.now(),
                                    Instant.now()
                            );
                            store.saveSnapshot(snapshot);
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            doneGate.await();

            // Then: no errors in writers
            assertThat(errors.get()).isZero();

            // And meta exists and is valid (version 0 as created via createMetaIfAbsent)
            Optional<FlowMeta> meta = store.loadMeta(flowId);
            assertThat(meta).isPresent();
            assertThat(meta.get().flowId()).isEqualTo(flowId);
            assertThat(meta.get().version()).isEqualTo(0);

            // And a snapshot exists (last write wins; payload content not strictly asserted)
            assertThat(store.loadSnapshot(flowId)).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldNotExtendTtlOnReadByDefault() throws InterruptedException {
        // Given: very short TTLs and a flow
        store = new RedisFlowStore(
                template,
                "fs:flow:",
                Duration.ofMillis(800), // snapshot TTL ~0.8s
                Duration.ofMillis(800),  // meta TTL ~0.8s
                false
        );
        String flowId = "ridNoSlidingTtl";
        FlowSnapshot snapshot = new FlowSnapshot(
                flowId,
                "{\"v\":1}",
                "application/json",
                Instant.now(),
                Instant.now()
        );
        store.saveSnapshot(snapshot);

        // When: repeatedly read within the 0.8 s window (this should NOT extend TTL)
        for (int i = 0; i < 3; i++) {
            assertThat(store.loadSnapshot(flowId)).isPresent();
            assertThat(store.loadMeta(flowId)).isPresent();
            Thread.sleep(200);
        }

        // Then: after >0.8 s total, entries should expire because read does not touch TTL
        Thread.sleep(400);
        assertThat(store.loadSnapshot(flowId)).isEmpty();
        assertThat(store.loadMeta(flowId)).isEmpty();
    }

    // ---------- helpers ----------

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
