package com.flowsentinel.core.definition;

import java.util.Optional;

public interface FlowDefinitionProvider {

    Optional<FlowDefinition> getDefinition(String definitionName);

    default boolean hasDefinition(String definitionName) {
        return getDefinition(definitionName).isPresent();
    }
}