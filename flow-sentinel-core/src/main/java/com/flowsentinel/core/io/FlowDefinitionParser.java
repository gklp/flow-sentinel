package com.flowsentinel.core.io;

import com.flowsentinel.core.definition.FlowDefinition;

import java.io.Reader;

/**
 * SPI for parsing a {@link FlowDefinition} from a character stream.
 * <p>
 * Implementations may parse JSON, XML, YAML, etc., but must not
 * introduce framework dependencies in the core module.
 * </p>
 *
 * @author gokalp
 */
@FunctionalInterface
public interface FlowDefinitionParser {

    /**
     * Parses a {@link FlowDefinition} from the provided {@link Reader}.
     *
     * @param reader the source of the definition
     * @return parsed flow definition
     * @throws FlowParseException if parsing fails
     */
    FlowDefinition parse(Reader reader) throws FlowParseException;
}