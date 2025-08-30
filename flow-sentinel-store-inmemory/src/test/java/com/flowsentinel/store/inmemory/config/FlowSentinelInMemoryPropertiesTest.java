package com.flowsentinel.store.inmemory.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class FlowSentinelInMemoryPropertiesTest {

    @Test
    void shouldSetAndGetAllFields() {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();

        // WHEN
        props.setTtl(Duration.ofSeconds(30));
        props.setAbsoluteTtl(Duration.ofMinutes(5));
        props.setMaximumSize(9999);
        props.setSlidingEnabled(true);
        props.setSlidingReset(FlowSentinelInMemoryProperties.SlidingReset.ON_READ);

        // THEN
        assertThat(props.getTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.getAbsoluteTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.getMaximumSize()).isEqualTo(9999);
        assertThat(props.isSlidingEnabled()).isTrue();
        assertThat(props.getSlidingReset()).isEqualTo(FlowSentinelInMemoryProperties.SlidingReset.ON_READ);
    }

    @Test
    void shouldReturnSaneDefaultsWhenCreated() {
        // GIVEN
        FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();

        // THEN
        assertThat(props.getTtl()).isNotNull();
        assertThat(props.getTtl().isNegative()).isFalse();
        assertThat(props.getAbsoluteTtl()).isNotNull();
        assertThat(props.getAbsoluteTtl().isNegative()).isFalse();
        assertThat(props.getMaximumSize()).isGreaterThan(0);
        assertThat(props.getSlidingReset()).isNotNull();
    }
}
