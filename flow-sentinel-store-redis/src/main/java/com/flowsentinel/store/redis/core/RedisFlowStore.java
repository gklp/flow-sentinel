package com.flowsentinel.store.redis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsentinel.core.store.FlowAggregate;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.config.FlowSentinelRedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregate-only RedisFlowStore
 */
public final class RedisFlowStore implements FlowStore {

	private static final Logger log = LoggerFactory.getLogger(RedisFlowStore.class);

	private static final DefaultRedisScript<Long> BULK_DELETE_SCRIPT = new DefaultRedisScript<>(
			"""
			local total = 0
			for i=1,#KEYS do
				local r = redis.call('DEL', KEYS[i])
				if r then total = total + r end
			end
			return total
			""",
			Long.class
	);

	private final RedisTemplate<String, String> template;
	private final ObjectMapper objectMapper;
	private final RedisKeys redisKeys;

	private final Duration ttl;
	private final Duration absoluteCap;
	private final boolean slidingEnabled;
	private final FlowSentinelRedisProperties.SlidingReset slidingReset;
	private final int historyLimit;

	public RedisFlowStore(@NonNull RedisTemplate<String, String> template,
	                      @NonNull ObjectMapper objectMapper,
	                      @NonNull FlowSentinelRedisProperties properties) {
		this.template = Objects.requireNonNull(template, "template");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
		Objects.requireNonNull(properties, "properties");

		this.ttl = Duration.ofSeconds(properties.getTtlSeconds());
		this.absoluteCap = Duration.ofSeconds(properties.getAbsoluteTtlSeconds());
		this.slidingEnabled = properties.isSlidingEnabled();
		this.slidingReset = properties.getSlidingReset();
		this.historyLimit = Math.max(0, 100);

		this.redisKeys = new RedisKeys(properties.getKeyPrefix());
	}

	@Override
	public void saveAggregate(@NonNull FlowAggregate aggregate) {
		Objects.requireNonNull(aggregate, "aggregate");
		final String flowId = Objects.requireNonNull(aggregate.meta(), "meta missing").flowId();

		final String aggKey = aggregate.meta().getPartitionKey() != null
				? redisKeys.aggregateKey(aggregate.meta().getPartitionKey(), flowId)
				: redisKeys.aggregateKey(flowId);

		try {
			Duration effectiveTtl = getEffectiveTtlFromAggregate(aggregate);
			writeAggregate(aggKey, aggregate, effectiveTtl);

			// sliding on writing
			if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_WRITE
					|| slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
				template.expire(aggKey, effectiveTtl);
			}
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize FlowAggregate for flow: " + flowId, e);
		}
	}

	@Override
	public Optional<FlowAggregate> loadAggregate(@NonNull String flowId) {
		if (flowId.isBlank()) {
			throw new IllegalArgumentException("flowId cannot be blank.");
		}
		final String aggKey = redisKeys.aggregateKey(flowId);
		String json = template.opsForValue().get(aggKey);
		if (json == null) {
			return Optional.empty();
		}

		// sliding on read
		if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ
				|| slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
			try {
				FlowAggregate agg = objectMapper.readValue(json, FlowAggregate.class);
				Duration effectiveTtl = getEffectiveTtlFromAggregate(agg);
				template.expire(aggKey, effectiveTtl);
				return Optional.of(agg);
			} catch (JsonProcessingException e) {
				log.error("Failed to deserialize FlowAggregate for key: {}. Corrupted data: {}", aggKey, json, e);
				throw new DataRetrievalFailureException("Failed to deserialize FlowAggregate for flow: " + flowId, e);
			}
		}

		try {
			return Optional.of(objectMapper.readValue(json, FlowAggregate.class));
		} catch (JsonProcessingException e) {
			log.error("Failed to deserialize FlowAggregate for key: {}. Corrupted data: {}", aggKey, json, e);
			throw new DataRetrievalFailureException("Failed to deserialize FlowAggregate for flow: " + flowId, e);
		}
	}

	@Override
	public void delete(@NonNull String flowId) {
		if (flowId.isBlank()) {
			throw new IllegalArgumentException("flowId cannot be blank.");
		}
		final String aggKey = redisKeys.aggregateKey(flowId);
		template.delete(aggKey);
	}

	@Override
	public boolean exists(@NonNull String flowId) {
		if (flowId.isBlank()) {
			throw new IllegalArgumentException("flowId cannot be blank.");
		}
		final String aggKey = redisKeys.aggregateKey(flowId);
		Boolean hasKey = template.hasKey(aggKey);
		return Boolean.TRUE.equals(hasKey);
	}

	@Override
	public int invalidateByPartition(String partitionKey) {
		if (partitionKey == null || partitionKey.trim().isEmpty()) {
			throw new IllegalArgumentException("partitionKey cannot be blank");
		}
		String pattern = redisKeys.partitionPattern(partitionKey).replace("*", "*:agg");
		Set<String> keys = template.keys(pattern);
		if (keys == null || keys.isEmpty()) {
			return 0;
		}
		Long deletedCount = template.execute(BULK_DELETE_SCRIPT, keys.stream().toList());
		return deletedCount != null ? deletedCount.intValue() : 0;
	}

	@Override
	public Set<String> listActiveFlows(String partitionKey) {
		if (partitionKey == null || partitionKey.trim().isEmpty()) {
			throw new IllegalArgumentException("partitionKey cannot be blank");
		}
		String pattern = redisKeys.partitionPattern(partitionKey).replace("*", "*:agg");
		Set<String> aggKeys = template.keys(pattern);
		if (aggKeys == null || aggKeys.isEmpty()) {
			return Collections.emptySet();
		}
		return aggKeys.stream()
				.map(this::extractFlowIdFromAggregateKey)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}

	@Override
	public int bulkDelete(Set<String> flowIds) {
		if (flowIds == null) {
			throw new IllegalArgumentException("flowIds cannot be null");
		}
		if (flowIds.isEmpty()) {
			return 0;
		}
		List<String> keysToDelete = flowIds.stream()
				.map(redisKeys::aggregateKey)
				.toList();
		Long deletedCount = template.execute(BULK_DELETE_SCRIPT, keysToDelete);
		return deletedCount != null ? deletedCount.intValue() : 0;
	}

	private void writeAggregate(String key, FlowAggregate aggregate, Duration ttl) throws JsonProcessingException {
		String json = objectMapper.writeValueAsString(aggregate);
		template.opsForValue().set(key, json, ttl);
	}

	private Duration getEffectiveTtlFromAggregate(FlowAggregate agg) {
		if (!slidingEnabled || absoluteCap == null || absoluteCap.isZero() || absoluteCap.isNegative()) {
			return this.ttl;
		}
		final Instant createdAt = agg.meta() != null ? agg.meta().createdAt() : null;
		if (createdAt == null) {
			return this.ttl;
		}
		var age = Duration.between(createdAt, Instant.now());
		var remainingAbsolute = absoluteCap.minus(age);
		if (remainingAbsolute.isNegative()) {
			return Duration.ZERO;
		}
		return remainingAbsolute.compareTo(ttl) < 0 ? remainingAbsolute : ttl;
	}

	private String extractFlowIdFromAggregateKey(String aggKey) {
		try {
			String ns = redisKeys.getNamespace();
			String suffix = ":agg";
			if (!aggKey.startsWith(ns) || !aggKey.endsWith(suffix)) return null;
			String mid = aggKey.substring(ns.length(), aggKey.length() - suffix.length()); // "<partition>:<flowId>" or "<flowId>"
			int idx = mid.lastIndexOf(':');
			return idx >= 0 ? mid.substring(idx + 1) : mid;
		} catch (Exception e) {
			return null;
		}
	}
}