package com.flowsentinel.core.store;

import java.util.Optional;
import java.util.Set;

public interface FlowStore {

    void saveAggregate(FlowAggregate aggregate);

    Optional<FlowAggregate> loadAggregate(String flowId);

    void delete(String flowId);

    boolean exists(String flowId);

    int invalidateByPartition(String partitionKey);

    Set<String> listActiveFlows(String partitionKey);

    int bulkDelete(Set<String> flowIds);
}