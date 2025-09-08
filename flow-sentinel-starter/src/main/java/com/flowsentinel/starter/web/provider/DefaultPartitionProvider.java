package com.flowsentinel.starter.web.provider;

import com.flowsentinel.core.provider.PartitionProvider;

import java.util.Optional;

public class DefaultPartitionProvider implements PartitionProvider {

    @Override
    public Optional<String> provide() {
        return Optional.empty();
    }
}
