/**
 * Contains framework-independent I/O utilities for loading and parsing
 * flow definitions from various sources such as files, streams, and strings.
 * <p>
 * This package defines the {@link com.flowsentinel.core.parser.FlowDefinitionParser}
 * Service Provider Interface (SPI) and related helper classes for reading
 * {@link com.flowsentinel.core.definition.FlowDefinition} instances without
 * introducing dependencies on external frameworks.
 * </p>
 * <h2>Design Principles</h2>
 * <ul>
 *     <li>Framework-agnostic — parsing implementations are provided in other modules.</li>
 *     <li>Immutable results — parsing produces immutable flow definitions.</li>
 *     <li>Clear error handling via {@link com.flowsentinel.core.parser.FlowParseException}.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * FlowDefinitionParser parser = new MyJsonFlowParser();
 * FlowFileLoader loader = new FlowFileLoader(parser);
 * FlowDefinition def = loader.loadFromFile(Path.of("flow.json"));
 * }</pre>
 *
 * @author gokalp
 * @since 1.0
 */
package com.flowsentinel.core.parser;
