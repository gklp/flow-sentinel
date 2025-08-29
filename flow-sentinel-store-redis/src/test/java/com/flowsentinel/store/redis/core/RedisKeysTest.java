package com.flowsentinel.store.redis.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RedisKeys}.
 */
class RedisKeysTest {

    @Test
    void shouldCreateKeysWithNamespace() {
        // Given
        RedisKeys keys = new RedisKeys("fs:flow:");

        // When/Then
        assertThat(keys.snapshotKey("test-flow")).isEqualTo("fs:flow:test-flow:snapshot");
        assertThat(keys.metaKey("test-flow")).isEqualTo("fs:flow:test-flow:meta");
        assertThat(keys.capKey("test-flow")).isEqualTo("fs:flow:test-flow:cap");
    }

    @Test
    void shouldReturnNamespace() {
        // Given
        String namespace = "fs:flow:";
        RedisKeys keys = new RedisKeys(namespace);

        // When/Then
        assertThat(keys.getNamespace()).isEqualTo(namespace);
    }

    @Test
    void shouldFailOnBlankNamespace() {
        assertThatThrownBy(() -> new RedisKeys(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RedisKeys(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RedisKeys(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailOnBlankFlowIdForSnapshotKey() {
        // Given
        RedisKeys keys = new RedisKeys("fs:flow:");

        // When/Then
        assertThatThrownBy(() -> keys.snapshotKey(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> keys.snapshotKey(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> keys.snapshotKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailOnBlankFlowIdForMetaKey() {
        // Given
        RedisKeys keys = new RedisKeys("fs:flow:");

        // When/Then
        assertThatThrownBy(() -> keys.metaKey(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> keys.metaKey(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> keys.metaKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailOnBlankFlowIdForCapKey() {
        // Given
        RedisKeys keys = new RedisKeys("fs:flow:");

        // When/Then
        assertThatThrownBy(() -> keys.capKey(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> keys.capKey(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> keys.capKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleDifferentNamespaceFormats() {
        // Test with namespace ending with colon
        RedisKeys keysWithColon = new RedisKeys("myapp:");
        assertThat(keysWithColon.metaKey("flow1")).isEqualTo("myapp:flow1:meta");

        // Test with namespace not ending with colon
        RedisKeys keysWithoutColon = new RedisKeys("myapp");
        assertThat(keysWithoutColon.metaKey("flow1")).isEqualTo("myappflow1:meta");
    }

    @Test
    void shouldHandleComplexFlowIds() {
        // Given
        RedisKeys keys = new RedisKeys("fs:flow:");

        // When/Then - test with various flow ID formats
        assertThat(keys.snapshotKey("flow-123")).isEqualTo("fs:flow:flow-123:snapshot");
        assertThat(keys.snapshotKey("flow_with_underscore")).isEqualTo("fs:flow:flow_with_underscore:snapshot");
        assertThat(keys.snapshotKey("UPPERCASE-FLOW")).isEqualTo("fs:flow:UPPERCASE-FLOW:snapshot");
        assertThat(keys.snapshotKey("flow.with.dots")).isEqualTo("fs:flow:flow.with.dots:snapshot");
    }

    @Test
    void shouldGenerateConsistentKeysForSameInputs() {
        // Given
        RedisKeys keys1 = new RedisKeys("test:");
        RedisKeys keys2 = new RedisKeys("test:");
        String flowId = "consistent-flow";

        // When/Then
        assertThat(keys1.snapshotKey(flowId)).isEqualTo(keys2.snapshotKey(flowId));
        assertThat(keys1.metaKey(flowId)).isEqualTo(keys2.metaKey(flowId));
        assertThat(keys1.capKey(flowId)).isEqualTo(keys2.capKey(flowId));
    }
}
