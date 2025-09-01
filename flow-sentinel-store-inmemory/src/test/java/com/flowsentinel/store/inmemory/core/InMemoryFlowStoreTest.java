package com.flowsentinel.store.inmemory.core;

import com.flowsentinel.core.id.StepId;
import com.flowsentinel.core.runtime.FlowState;
import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.store.inmemory.config.FlowSentinelInMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFlowStoreTest {

    private InMemoryFlowStore store;

    @BeforeEach
    void setUp() {
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setMaximumSize(1000);
        props.setTtl(Duration.ofSeconds(1));
        props.setAbsoluteTtl(Duration.ofSeconds(5));
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ_AND_WRITE);
        store = new InMemoryFlowStore(props);
    }

    @Test
    void shouldSaveAndLoadMeta() {
        // GIVEN
        String flowId = "ridMeta1";
        FlowMeta meta = FlowMeta.createNew(flowId);

        // WHEN
        store.saveMeta(meta);

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isPresent();
        assertThat(found.get().flowId()).isEqualTo(flowId);
        assertThat(found.get().status()).isEqualTo("NEW");
        assertThat(found.get().step()).isEqualTo(StepId.of("INIT").value());
        assertThat(found.get().createdAt()).isNotNull();
        assertThat(found.get().updatedAt()).isNotNull();
    }

    @Test
    void shouldReturnEmptyWhenMetaNotFound() {
        // GIVEN
        String flowId = "nonexistent";

        // WHEN
        Optional<FlowMeta> found = store.loadMeta(flowId);

        // THEN
        assertThat(found).isNotPresent();
    }

    @Test
    void shouldReturnEmptyWhenStateNotFound() {
        // GIVEN
        String flowId = "nonexistent";

        // WHEN
        Optional<FlowState> found = store.find(flowId);

        // THEN
        assertThat(found).isNotPresent();
    }

    @Test
    void shouldDeleteMeta() {
        // GIVEN
        String flowId = "ridMetaDelete";
        FlowMeta meta = FlowMeta.createNew(flowId);
        store.saveMeta(meta);

        // WHEN
        store.delete(flowId);

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isNotPresent();
    }

    @Test
    void shouldExpireAfterTtl() throws InterruptedException {
        // GIVEN
        String flowId = "ridExpire";
        FlowMeta meta = FlowMeta.createNew(flowId);
        store.saveMeta(meta);

        // WHEN
        Thread.sleep(1500); // Wait for TTL to expire (1 second)

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isNotPresent();
    }

    @Test
    void shouldNotExpireWhenSlidingTtlOnRead() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofSeconds(2));
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ);
        store = new InMemoryFlowStore(props);

        String flowId = "ridSlidingRead";
        FlowMeta meta = FlowMeta.createNew(flowId);
        store.saveMeta(meta);

        // WHEN
        Thread.sleep(1500);
        store.loadMeta(flowId); // Read to reset TTL
        Thread.sleep(1500);

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isPresent();
    }

    @Test
    void shouldExpireWhenSlidingTtlOnWrite() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofSeconds(2));
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_WRITE);
        store = new InMemoryFlowStore(props);

        String flowId = "ridSlidingWrite";
        FlowMeta meta = FlowMeta.createNew(flowId);
        store.saveMeta(meta);

        // WHEN
        Thread.sleep(1500);
        store.loadMeta(flowId); // Read should not reset TTL
        Thread.sleep(1500);

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isNotPresent();
    }

    @Test
    void shouldNotExpireWithAbsoluteTtl() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofSeconds(2));
        props.setAbsoluteTtl(Duration.ofSeconds(5)); // Absolute TTL is longer
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ);
        store = new InMemoryFlowStore(props);

        String flowId = "ridAbsolute";
        FlowMeta meta = FlowMeta.createNew(flowId);
        store.saveMeta(meta);

        // WHEN
        Thread.sleep(1500);
        store.loadMeta(flowId); // Reset sliding TTL
        Thread.sleep(1500);

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isPresent(); // Should still be present
    }

    @Test
    void shouldExpireWithAbsoluteTtl() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofSeconds(2));
        props.setAbsoluteTtl(Duration.ofSeconds(4)); // Absolute TTL is shorter
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ);
        store = new InMemoryFlowStore(props);

        String flowId = "ridAbsoluteExpire";
        FlowMeta meta = FlowMeta.createNew(flowId);
        store.saveMeta(meta);

        // WHEN
        Thread.sleep(1500);
        store.loadMeta(flowId); // Read to reset TTL
        Thread.sleep(3000); // Wait for absolute TTL to expire

        // THEN
        Optional<FlowMeta> found = store.loadMeta(flowId);
        assertThat(found).isNotPresent();
    }
}