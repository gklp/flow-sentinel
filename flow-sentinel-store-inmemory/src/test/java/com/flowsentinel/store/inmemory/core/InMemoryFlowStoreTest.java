package com.flowsentinel.store.inmemory.core;

import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.store.inmemory.config.FlowSentinelInMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
        assertThat(found.get().step()).isEqualTo("INIT");
        assertThat(found.get().version()).isEqualTo(0);
    }

    @Test
    void shouldSaveAndLoadSnapshot() {
        // GIVEN
        String flowId = "ridSnap1";
        FlowSnapshot snapshot = FlowSnapshot.ofJson(flowId, "{\"step\":\"START\"}");

        // WHEN
        store.saveSnapshot(snapshot);

        // THEN
        Optional<FlowSnapshot> found = store.loadSnapshot(flowId);
        assertThat(found).isPresent();
        assertThat(found.get().flowId()).isEqualTo(flowId);
        assertThat(found.get().payload()).isEqualTo("{\"step\":\"START\"}");
        assertThat(found.get().contentType()).isEqualTo("application/json");
    }

    @Test
    void shouldReturnEmptyOptionalWhenFlowIdNotFound() {
        // GIVEN
        String flowId = "notfound";

        // WHEN
        Optional<FlowMeta> meta = store.loadMeta(flowId);
        Optional<FlowSnapshot> snapshot = store.loadSnapshot(flowId);

        // THEN
        assertThat(meta).isEmpty();
        assertThat(snapshot).isEmpty();
    }

    @Test
    void shouldDeleteMetaAndSnapshot() {
        // GIVEN
        String flowId = "delete1";
        store.saveMeta(FlowMeta.createNew(flowId));
        store.saveSnapshot(FlowSnapshot.ofJson(flowId, "{}"));

        // WHEN
        store.delete(flowId);

        // THEN
        assertThat(store.loadMeta(flowId)).isEmpty();
        assertThat(store.loadSnapshot(flowId)).isEmpty();
    }

    @Test
    void shouldReturnTrueIfAnyExists() {
        // GIVEN
        String flowId = "exists1";
        store.saveSnapshot(FlowSnapshot.ofJson(flowId, "{}"));

        // WHEN/THEN
        assertThat(store.exists(flowId)).isTrue();
    }

    @Test
    void shouldReturnFalseIfNoneExists() {
        // GIVEN
        String flowId = "nonexistent";

        // WHEN/THEN
        assertThat(store.exists(flowId)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenSavingNullMeta() {
        // GIVEN // WHEN // THEN
        assertThatNullPointerException()
                .isThrownBy(() -> store.saveMeta(null))
                .withMessage("FlowMeta cannot be null.");
    }

    @Test
    void shouldThrowExceptionWhenSavingNullSnapshot() {
        // GIVEN // WHEN // THEN
        assertThatNullPointerException()
                .isThrownBy(() -> store.saveSnapshot(null))
                .withMessage("FlowSnapshot cannot be null.");
    }

    @Test
    void shouldThrowExceptionWhenFlowIdIsBlank() {
        // GIVEN // WHEN // THEN
        assertThatIllegalArgumentException().isThrownBy(() -> store.loadMeta(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> store.loadSnapshot(""));
        assertThatIllegalArgumentException().isThrownBy(() -> store.delete("\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.exists("\t"));
    }

    @Test
    void shouldEvictExpiredMetaEntry() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setMaximumSize(1000);
        props.setTtl(Duration.ofMillis(50));
        props.setAbsoluteTtl(Duration.ofSeconds(1));
        props.setSlidingEnabled(false);
        InMemoryFlowStore localStore = new InMemoryFlowStore(props);

        String flowId = "expireMeta";
        localStore.saveMeta(FlowMeta.createNew(flowId));

        // WHEN
        Thread.sleep(100);

        // THEN
        assertThat(localStore.loadMeta(flowId)).isEmpty();
    }

    @Test
    void shouldEvictExpiredSnapshotEntry() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setMaximumSize(100);
        props.setTtl(Duration.ofMillis(50));
        props.setAbsoluteTtl(Duration.ofSeconds(1));
        props.setSlidingEnabled(false);
        InMemoryFlowStore localStore = new InMemoryFlowStore(props);

        String flowId = "expireSnap";
        localStore.saveSnapshot(FlowSnapshot.ofJson(flowId, "{}"));

        // WHEN
        Thread.sleep(100);

        // THEN
        assertThat(localStore.loadSnapshot(flowId)).isEmpty();
    }

    @Test
    void shouldInitializeWithDefaultConstructor() {
        // GIVEN
        InMemoryFlowStore defaultStore = new InMemoryFlowStore();

        String flowId = "defaultCtor";
        FlowMeta meta = FlowMeta.createNew(flowId);

        // WHEN
        defaultStore.saveMeta(meta);

        // THEN
        Optional<FlowMeta> found = defaultStore.loadMeta(flowId);
        assertThat(found).isPresent();
        assertThat(found.get().flowId()).isEqualTo(flowId);
    }

    @Test
    void shouldRespectBaseTtlOnlyWhenAbsoluteTtlDisabled() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofMillis(100));
        props.setAbsoluteTtl(Duration.ZERO);
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ);
        InMemoryFlowStore store = new InMemoryFlowStore(props);

        String flowId = "ttlOnly";
        store.saveMeta(FlowMeta.createNew(flowId));

        Thread.sleep(90);

        // WHEN – this read should refresh TTL
        store.loadMeta(flowId);

        Thread.sleep(20);

        // THEN
        assertThat(store.loadMeta(flowId)).isPresent();
    }

    @Test
    void shouldLimitExpirationToAbsoluteTtlWhenRemainingIsLessThanBase() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofMillis(100)); // base ttl
        props.setAbsoluteTtl(Duration.ofMillis(120)); // total limit
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ_AND_WRITE);
        InMemoryFlowStore store = new InMemoryFlowStore(props);

        String flowId = "absoluteLimit";
        store.saveMeta(FlowMeta.createNew(flowId));

        Thread.sleep(90);

        // WHEN – remaining absolute ttl ~30ms, base ttl = 100ms
        store.loadMeta(flowId); // triggers sliding read + expiration logic

        Thread.sleep(40); // wait longer than remaining absolute ttl

        // THEN
        assertThat(store.loadMeta(flowId)).isEmpty(); // proves the absolute cap was enforced
    }

    @Test
    void shouldNotResetTtlWhenSlidingDisabled() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofMillis(50));
        props.setSlidingEnabled(false); // sliding disabled
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_WRITE);
        InMemoryFlowStore store = new InMemoryFlowStore(props);

        String flowId = "noSliding";
        store.saveMeta(FlowMeta.createNew(flowId));

        Thread.sleep(60);

        // THEN
        assertThat(store.loadMeta(flowId)).isEmpty(); // TTL did not reset, entry expired
    }

    @Test
    void shouldNotResetTtlWhenResetPolicyIsReadOnly() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofMillis(50));
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ); // write ignored
        InMemoryFlowStore store = new InMemoryFlowStore(props);

        String flowId = "readOnlyReset";
        store.saveMeta(FlowMeta.createNew(flowId));
        Thread.sleep(40);

        store.saveMeta(FlowMeta.createNew(flowId)); // write should not reset TTL
        Thread.sleep(20); // total > base TTL

        // THEN
        assertThat(store.loadMeta(flowId)).isEmpty(); // TTL not reset due to policy
    }

    @Test
    void shouldResetTtlAfterUpdateWhenSlidingReadAndWriteEnabled() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofMillis(100));
        props.setAbsoluteTtl(Duration.ofSeconds(1));
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ_AND_WRITE);
        InMemoryFlowStore store = new InMemoryFlowStore(props);

        String flowId = "resetReadWrite";
        store.saveMeta(FlowMeta.createNew(flowId));
        Thread.sleep(50);

        // WHEN — write triggers TTL reset
        store.saveMeta(FlowMeta.createNew(flowId));
        Thread.sleep(70); // total > base TTL, but should still be valid

        // THEN
        assertThat(store.loadMeta(flowId)).isPresent();
    }

    @Test
    void shouldResetTtlAfterUpdateWhenSlidingOnWriteEnabled() throws InterruptedException {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
        props.setTtl(Duration.ofMillis(100));
        props.setAbsoluteTtl(Duration.ofSeconds(1));
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_WRITE);
        InMemoryFlowStore store = new InMemoryFlowStore(props);

        String flowId = "resetOnWrite";
        store.saveMeta(FlowMeta.createNew(flowId));
        Thread.sleep(50);

        // WHEN
        store.saveMeta(FlowMeta.createNew(flowId));
        Thread.sleep(70);


        // THEN
        assertThat(store.loadMeta(flowId)).isPresent();
    }
    
}