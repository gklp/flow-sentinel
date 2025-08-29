package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link MetaCodec}.
 */
class MetaCodecTest {

    @Test
    void shouldRoundupEncodeDecode() {
        // Given
        String flowId = "ridMetaRt";
        FlowMeta meta = new FlowMeta(
                flowId, "RUNNING", "KYC", 3,
                Instant.ofEpochMilli(1000),
                Instant.ofEpochMilli(2000)
        );

        // When
        String encoded = MetaCodec.encode(meta);
        FlowMeta decoded = MetaCodec.decode(flowId, encoded);

        // Then
        assertThat(decoded.flowId()).isEqualTo(flowId);
        assertThat(decoded.status()).isEqualTo("RUNNING");
        assertThat(decoded.step()).isEqualTo("KYC");
        assertThat(decoded.version()).isEqualTo(3);
        assertThat(decoded.createdAt()).isEqualTo(Instant.ofEpochMilli(1000));
        assertThat(decoded.updatedAt()).isEqualTo(Instant.ofEpochMilli(2000));
    }

    @Test
    void shouldUseEpochForZeroTimestamps() {
        // Given
        String flowId = "ridEpoch";
        String payload = "0|NEW|INIT|0|0";

        // When
        FlowMeta decoded = MetaCodec.decode(flowId, payload);

        // Then
        assertThat(decoded.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(decoded.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void shouldRejectPipesInFields() {
        // Given
        FlowMeta meta = new FlowMeta(
                "ridIllegal", "NE|W", "INIT", 0,
                Instant.now(), Instant.now()
        );

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.encode(meta));
    }

    @Test
    void shouldRejectMalformedPayload() {
        // Given
        String flowId = "ridMalformed";
        String payload = "0|NEW|INIT|123"; // missing one field

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, payload));
    }

    @Test
    void shouldEncodeEpochAsZeroString() {
        // Given
        FlowMeta meta = new FlowMeta(
                "ridEpochEncode", "NEW", "INIT", 0,
                Instant.EPOCH, Instant.EPOCH
        );

        // When
        String encoded = MetaCodec.encode(meta);

        // Then
        assertThat(encoded).isEqualTo("0|NEW|INIT|0|0");
    }

    @Test
    void shouldRejectNonNumericTimestampsAndVersion() {
        // Given
        String flowId = "ridNonNumeric";

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, "x|NEW|INIT|1|2")); // createdAt non-numeric
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, "1|NEW|INIT|x|2")); // version non-numeric
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, "1|NEW|INIT|1|x")); // updatedAt non-numeric
    }

    @Test
    void shouldRejectEmptyOrBlankPayload() {
        // Given
        String flowId = "ridBlank";

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, ""));
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, "   "));
    }

    @Test
    void shouldRejectPayloadWithExtraFields() {
        // Given
        String flowId = "ridExtra";
        String payload = "1|NEW|INIT|1|2|EXTRA";

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> MetaCodec.decode(flowId, payload));
    }

    @Test
    void shouldRoundupWithUnicodeFields() {
        // Given
        String flowId = "ridUnicode";
        FlowMeta meta = new FlowMeta(
                flowId, "Working🚀", "Validate a step", 42,
                Instant.ofEpochMilli(12345),
                Instant.ofEpochMilli(67890)
        );

        // When
        String encoded = MetaCodec.encode(meta);
        FlowMeta decoded = MetaCodec.decode(flowId, encoded);

        // Then
        assertThat(decoded.status()).isEqualTo(meta.status());
        assertThat(decoded.step()).isEqualTo(meta.step());
        assertThat(decoded.version()).isEqualTo(42);
        assertThat(decoded.createdAt()).isEqualTo(Instant.ofEpochMilli(12345));
        assertThat(decoded.updatedAt()).isEqualTo(Instant.ofEpochMilli(67890));
    }
}
