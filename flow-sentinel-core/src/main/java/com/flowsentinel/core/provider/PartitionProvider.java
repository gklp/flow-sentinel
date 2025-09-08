package com.flowsentinel.core.provider;

import java.util.Optional;

public interface PartitionProvider {

    Optional<String> provide();

}
