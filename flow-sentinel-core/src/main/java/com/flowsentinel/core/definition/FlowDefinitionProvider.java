package com.flowsentinel.core.definition;

import java.util.Optional;

/**
 * Contract for providing flow definitions to the engine.
 * <p>
 * This abstraction allows the core engine to resolve flow definitions
 * without depending on specific implementation details like classpath loading,
 * database lookup, or caching strategies.
 * <p>
 * Implementations should handle definition loading, parsing, and caching
 * as appropriate for their specific use case.
 *
 * @see FlowDefinition
 */
public interface FlowDefinitionProvider {

    /**
     * Retrieves a flow definition by its name.
     * 
     * @param definitionName The name of the flow definition to retrieve.
     *                       Must not be null or blank.
     * @return An Optional containing the FlowDefinition if found, 
     *         or empty if no definition exists with the given name.
     * @throws IllegalArgumentException if definitionName is null or blank.
     * @throws FlowDefinitionException if an error occurs while loading the definition.
     */
    Optional<FlowDefinition> getDefinition(String definitionName);

    /**
     * Checks if a definition with the given name exists.
     * 
     * @param definitionName The name to check for.
     * @return true if the definition exists, false otherwise.
     */
    default boolean hasDefinition(String definitionName) {
        return getDefinition(definitionName).isPresent();
    }
}