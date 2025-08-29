package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;
import org.springframework.util.Assert;

import java.time.Instant;

/**
 * Compact encoder/decoder for {@link FlowMeta} objects.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Encodes FlowMeta into a single string to minimize Redis memory usage.</li>
 *   <li>Decodes payloads back into immutable {@link FlowMeta} instances.</li>
 *   <li>Uses the pipe character ({@code |}) as a delimiter between fields.</li>
 *   <li>Flow id is not stored in the payload because it is already present in the Redis key.</li>
 * </ul>
 *
 * <h2>Format</h2>
 * <pre>
 * version|status|step|createdAtEpochMilli|updatedAtEpochMilli
 * </pre>
 *
 * <ul>
 *   <li>{@code version} – int</li>
 *   <li>{@code status} – engine-level state (e.g., NEW, RUNNING, COMPLETED)</li>
 *   <li>{@code step} – current or last executed step identifier</li>
 *   <li>{@code createdAtEpochMilli} – flow creation timestamp in epoch milliseconds</li>
 *   <li>{@code updatedAtEpochMilli} – last update timestamp in epoch milliseconds</li>
 * </ul>
 *
 * <p>Any malformed payload will result in {@link IllegalArgumentException} at decode time.</p>
 */
final class MetaCodec {

    private static final String SEP = "|";
    private static final int FIELDS = 5;

    private MetaCodec() {
        // utility class
    }

    /**
     * Encodes the given {@link FlowMeta} into a compact string format.
     *
     * @param meta non-null meta object
     * @return encoded string representation
     * @throws IllegalArgumentException if any field contains an illegal character
     */
    static String encode(FlowMeta meta) {
        Assert.notNull(meta, "meta must not be null");
        return new StringBuilder(64)
                .append(meta.version()).append(SEP)
                .append(sanitize(meta.status())).append(SEP)
                .append(sanitize(meta.step())).append(SEP)
                .append(meta.createdAt() != null ? meta.createdAt().toEpochMilli() : 0L).append(SEP)
                .append(meta.updatedAt() != null ? meta.updatedAt().toEpochMilli() : 0L)
                .toString();
    }

    /**
     * Decodes a payload string into a {@link FlowMeta} instance.
     *
     * @param flowId  flow identifier (retrieved from Redis key)
     * @param payload encoded string payload
     * @return decoded {@link FlowMeta} instance
     * @throws IllegalArgumentException if the payload is malformed or fields cannot be parsed
     */
    static FlowMeta decode(String flowId, String payload) {
        Assert.hasText(flowId, "flowId must not be empty");
        Assert.hasText(payload, "payload must not be empty");

        String[] parts = payload.split("\\|", -1);
        if (parts.length != FIELDS) {
            throw new IllegalArgumentException("Invalid meta payload; expected " + FIELDS + " fields, got " + parts.length);
        }

        int version = parseInt(parts[0], "version");
        String status = parts[1];
        String step = parts[2];
        Instant createdAt = parseInstant(parts[3], "createdAtEpochMilli");
        Instant updatedAt = parseInstant(parts[4], "updatedAtEpochMilli");

        return new FlowMeta(flowId, status, step, version, createdAt, updatedAt);
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        if (s.indexOf('|') >= 0) {
            throw new IllegalArgumentException("Field must not contain '|': " + s);
        }
        return s;
    }

    private static int parseInt(String s, String field) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid int for " + field + ": " + s, e);
        }
    }

    private static long parseLong(String s, String field) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long for " + field + ": " + s, e);
        }
    }

    private static Instant parseInstant(String s, String field) {
        long v = parseLong(s, field);
        return v > 0 ? Instant.ofEpochMilli(v) : Instant.EPOCH;
    }
}
