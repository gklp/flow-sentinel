package com.flowsentinel.starter.web;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.parser.FlowDefinitionParser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache for flow definitions loaded from the classpath.
 * Avoids repeated parsing of definition files.
 */
@Component
public class FlowDefinitionRegistry {

    private final FlowDefinitionParser parser;
    private final Map<String, FlowDefinition> cache = new ConcurrentHashMap<>();

    public FlowDefinitionRegistry(FlowDefinitionParser parser) {
        this.parser = parser;
    }

    public Optional<FlowDefinition> getDefinition(String flowName) {
        return Optional.of(cache.computeIfAbsent(flowName, this::loadDefinition));
    }

    private FlowDefinition loadDefinition(String flowName) {
        String resourcePath = "/flows/" + flowName + ".json";
        try (InputStream stream = FlowDefinitionRegistry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Flow definition not found at: " + resourcePath);
            }
            return parser.parse(stream);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load or parse flow definition: " + resourcePath, e);
        }
    }
}