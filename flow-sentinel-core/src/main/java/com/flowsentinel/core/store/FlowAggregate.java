package com.flowsentinel.core.store;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class FlowAggregate {
	private FlowMeta meta;
	private FlowSnapshot currentSnapshot;
	private List<FlowSnapshot> snapshotHistory;

	@JsonCreator
	public FlowAggregate(
			@JsonProperty("meta") FlowMeta meta,
			@JsonProperty("currentSnapshot") FlowSnapshot currentSnapshot,
			@JsonProperty("snapshotHistory") List<FlowSnapshot> snapshotHistory) {
		this.meta = Objects.requireNonNull(meta, "meta");
		this.currentSnapshot = currentSnapshot;
		this.snapshotHistory = snapshotHistory != null ? new ArrayList<>(snapshotHistory) : new ArrayList<>();
	}

	public FlowMeta meta() { return meta; }
	public FlowSnapshot currentSnapshot() { return currentSnapshot; }
	public List<FlowSnapshot> snapshotHistory() { return List.copyOf(snapshotHistory); }

	public void setMeta(FlowMeta meta) { this.meta = Objects.requireNonNull(meta); }
	public void setCurrentSnapshot(FlowSnapshot s) { this.currentSnapshot = s; }
	public void appendHistory(FlowSnapshot s, int maxSize) {
		if (s == null) return;
		this.snapshotHistory.add(s);
		if (maxSize > 0 && this.snapshotHistory.size() > maxSize) {
			int toRemove = this.snapshotHistory.size() - maxSize;
			this.snapshotHistory = new ArrayList<>(this.snapshotHistory.subList(toRemove, this.snapshotHistory.size()));
		}
	}
}