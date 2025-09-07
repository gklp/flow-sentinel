package com.flowsentinel.store.redis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsentinel.core.id.FlowContext;
import com.flowsentinel.core.store.FlowAggregate;
import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowSnapshot;
import com.flowsentinel.core.store.FlowStore;
import com.flowsentinel.store.redis.config.FlowSentinelRedisProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
		this.historyLimit = Math.max(0, 100); // varsayım: properties’e eklendi

		this.redisKeys = new RedisKeys(properties.getKeyPrefix());
	}

	@Override
	public void saveMeta(@NonNull FlowMeta meta) {
		final FlowContext context = meta.flowContext();
		final String flowId = context.flowId();

		final String aggKey = context.getEffectivePartitionKey() != null
				? redisKeys.aggregateKey(context.getEffectivePartitionKey(), flowId)
				: redisKeys.aggregateKey(flowId);

		try {
			FlowAggregate aggregate = readAggregate(flowId).orElseGet(() ->
					new FlowAggregate(meta, null, List.of())
			);
			aggregate.setMeta(meta);

			Duration effectiveTtl = getEffectiveTtlFromAggregate(flowId, aggregate);
			writeAggregate(aggKey, aggregate, effectiveTtl);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize FlowAggregate for flow: {}", flowId, e);
			throw new DataAccessException("JSON serialization failed for FlowAggregate: " + flowId, e) {};
		}
	}

	@Override
	public Optional<FlowMeta> loadMeta(@NonNull String flowId) {
		if (flowId.isBlank()) {
			throw new IllegalArgumentException("flowId cannot be blank.");
		}
		final Optional<FlowAggregate> aggOpt = readAggregate(flowId);
		if (aggOpt.isEmpty()) return Optional.empty();

		if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ
				|| slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
			String aggKey = redisKeys.aggregateKey(flowId);
			Duration effectiveTtl = getEffectiveTtlFromAggregate(flowId, aggOpt.get());
			template.expire(aggKey, effectiveTtl);
		}
		return Optional.ofNullable(aggOpt.get().meta());
	}

	@Override
	public void saveSnapshot(@NonNull FlowSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "FlowSnapshot cannot be null.");
		final String flowId = Objects.requireNonNull(snapshot.flowId(), "FlowId in FlowSnapshot cannot be null.");

		final String aggKey = redisKeys.aggregateKey(flowId);

		try {
			FlowAggregate aggregate = readAggregate(flowId).orElseGet(() ->
					new FlowAggregate(
							FlowMeta.createNew(FlowContext.anonymous(flowId)),
							null,
							List.of()
					)
			);

			if (aggregate.currentSnapshot() != null) {
				aggregate.appendHistory(aggregate.currentSnapshot(), this.historyLimit);
			}
			aggregate.setCurrentSnapshot(snapshot);

			Duration effectiveTtl = getEffectiveTtlFromAggregate(flowId, aggregate);
			writeAggregate(aggKey, aggregate, effectiveTtl);

			if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_WRITE
					|| slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
				template.expire(aggKey, effectiveTtl);
			}
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize FlowAggregate for flow ID: {}", flowId, e);
			throw new IllegalStateException("Failed to serialize FlowAggregate for flow: " + flowId, e);
		}
	}

	@Override
	public Optional<FlowSnapshot> loadSnapshot(@NonNull String flowId) {
		if (flowId.isBlank()) {
			throw new IllegalArgumentException("flowId cannot be blank.");
		}
		final String aggKey = redisKeys.aggregateKey(flowId);
		Optional<FlowAggregate> aggOpt = readAggregate(flowId);
		if (aggOpt.isEmpty()) {
			return Optional.empty();
		}

		if (slidingEnabled && (slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ
				|| slidingReset == FlowSentinelRedisProperties.SlidingReset.ON_READ_AND_WRITE)) {
			Duration effectiveTtl = getEffectiveTtlFromAggregate(flowId, aggOpt.get());
			template.expire(aggKey, effectiveTtl);
		}
		return Optional.ofNullable(aggOpt.get().currentSnapshot());
	}

	@Override
	public void delete(@NonNull String flowId) {
		if (flowId.isBlank()) {
			throw new IllegalArgumentException("flowId cannot be blank.");
		}
		final String aggKey = redisKeys.aggregateKey(flowId);
		template.delete(aggKey);
		log.debug("Deleted aggregate key for flow ID '{}'", flowId);
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
			log.debug("No aggregate keys found for partition: {}", partitionKey);
			return 0;
		}

		Long deletedCount = template.execute(BULK_DELETE_SCRIPT, keys.stream().toList());
		int flowCount = keys.size();
		log.info("Invalidated {} flows ({} keys) for partition: {}", flowCount, deletedCount, partitionKey);
		return flowCount;
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
		int actualFlowsDeleted = deletedCount != null ? deletedCount.intValue() : 0;

		log.debug("Bulk deleted {} flows ({} keys)", actualFlowsDeleted, deletedCount);
		return actualFlowsDeleted;
	}

	private Optional<FlowAggregate> readAggregate(String flowId) {
		final String key = redisKeys.aggregateKey(flowId);
		String json = template.opsForValue().get(key);
		if (json == null) return Optional.empty();
		try {
			return Optional.of(objectMapper.readValue(json, FlowAggregate.class));
		} catch (JsonProcessingException e) {
			log.error("Failed to deserialize FlowAggregate for key: {}. Corrupted data: {}", key, json, e);
			throw new DataRetrievalFailureException("Failed to deserialize FlowAggregate for flow: " + flowId, e);
		}
	}

	private void writeAggregate(String key, FlowAggregate aggregate, Duration ttl) throws JsonProcessingException {
		String json = objectMapper.writeValueAsString(aggregate);
		template.opsForValue().set(key, json, ttl);
	}

	private Duration getEffectiveTtlFromAggregate(String flowId, FlowAggregate agg) {
		if (!slidingEnabled || absoluteCap == null || absoluteCap.isZero() || absoluteCap.isNegative()) {
			return this.ttl;
		}
		final Instant createdAt = agg.meta() != null ? agg.meta().createdAt() : null;
		if (createdAt == null) {
			log.warn("Cannot calculate effective TTL; FlowMeta for flow ID '{}' not present in aggregate. Defaulting to base TTL.", flowId);
			return this.ttl;
		}
		Duration age = Duration.between(createdAt, Instant.now());
		Duration remainingAbsolute = absoluteCap.minus(age);
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
			String mid = aggKey.substring(ns.length(), aggKey.length() - suffix.length());
			int idx = mid.lastIndexOf(':');
			return idx >= 0 ? mid.substring(idx + 1) : mid;
		} catch (Exception e) {
			return null;
		}
	}
}