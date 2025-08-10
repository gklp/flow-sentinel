package com.flowsentinel.store.redis.core;

import com.flowsentinel.core.store.FlowMeta;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal meta encoder/decoder.
 *
 * <p>Purposefully avoids extra JSON libs here to keep the hot-path lean.
 * If you need richer meta in the future, move to a JSON codec behind this interface.</p>
 * <p>
 * author gokalp
 */
final class MetaCodec {

    private MetaCodec() {
    }

    static String encode(FlowMeta m) {
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("status", m.status());
        kv.put("step", m.step());
        kv.put("version", Integer.toString(m.version()));
        kv.put("createdAt", toIso(m.createdAt()));
        kv.put("updatedAt", toIso(m.updatedAt()));

        StringBuilder sb = new StringBuilder(64);
        kv.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    static FlowMeta decode(String flowId, String s) {
        // defaults
        String status = "NEW";
        String step = "INIT";
        int version = 0;
        Instant created = Instant.EPOCH;
        Instant updated = Instant.EPOCH;

        for (String part : s.split(";")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf('=');
            if (i <= 0) continue;
            String k = part.substring(0, i);
            String v = part.substring(i + 1);
            switch (k) {
                case "status" -> status = v;
                case "step" -> step = v;
                case "version" -> version = Integer.parseInt(v);
                case "createdAt" -> created = Instant.parse(v);
                case "updatedAt" -> updated = Instant.parse(v);
                default -> { /* ignore unknown */ }
            }
        }
        return new FlowMeta(flowId, status, step, version, created, updated);
    }

    private static String toIso(Instant t) {
        return (t != null ? t : Instant.EPOCH).toString();
    }
}
