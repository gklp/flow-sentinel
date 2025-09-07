package com.flowsentinel.starter.web;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.FlowDefinitionException;
import com.flowsentinel.core.definition.FlowDefinitionProvider;
import com.flowsentinel.core.parser.FlowDefinitionParser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A classpath-based implementation of {@link FlowDefinitionProvider}.
 * <p>
 * This registry loads flow definitions from the classpath at `/flows/{flowName}.json`
 * and caches them in memory to avoid repeated parsing operations.
 */
@Component
public class FlowDefinitionRegistry implements FlowDefinitionProvider {

    private final FlowDefinitionParser parser;
    private final Map<String, FlowDefinition> cache = new ConcurrentHashMap<>();

    public FlowDefinitionRegistry(FlowDefinitionParser parser) {
        this.parser = Objects.requireNonNull(parser, "FlowDefinitionParser cannot be null");
    }

    @Override
    public Optional<FlowDefinition> getDefinition(String definitionName) {
        if (definitionName == null || definitionName.isBlank()) {
            throw new IllegalArgumentException("definitionName cannot be null or blank");
        }

        try {
            FlowDefinition definition = cache.computeIfAbsent(definitionName, this::loadDefinition);
            return Optional.of(definition);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private FlowDefinition loadDefinition(String flowName) {
        String resourcePath = "/flows/" + flowName + ".json";
        try (InputStream stream = FlowDefinitionRegistry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new FlowDefinitionException("Flow definition not found at: " + resourcePath);
            }
            return parser.parse(stream);
        } catch (Exception e) {
            throw new FlowDefinitionException("Failed to load or parse flow definition: " + resourcePath, e);
        }
    }
}