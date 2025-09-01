package com.flowsentinel.core.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsentinel.core.definition.FlowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A parser for deserializing a {@link FlowDefinition} from a JSON representation
 * from various sources like files, strings, or streams.
 * <p>
 * This class provides a default implementation for parsing flow definitions
 * using the Jackson library. It is configured to be robust against unknown
 * properties in the JSON source, which allows for more flexible and
 * backward-compatible definition files. It also serves as a loader,
 * providing convenience methods to load definitions directly from the filesystem or a string.
 *
 * @author gokalp
 */
public final class FlowDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionParser.class);
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new parser with a pre-configured {@link ObjectMapper}.
     * The mapper is set to ignore unknown properties for forward compatibility.
     */
    public FlowDefinitionParser() {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Parses a {@link FlowDefinition} from a file path.
     *
     * @param path The {@link Path} to the definition file. Must not be null.
     * @return The parsed {@link FlowDefinition}.
     * @throws FlowParseException if the file cannot be read or its content is malformed.
     */
    public FlowDefinition parse(Path path) {
        Objects.requireNonNull(path, "The path cannot be null.");
        log.info("Loading flow definition from file [{}]", path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new FlowParseException("Failed to read flow definition from file: " + path, e);
        }
    }

    /**
     * Parses a {@link FlowDefinition} from a string content.
     *
     * @param content The string containing the JSON definition. Must not be null.
     * @return The parsed {@link FlowDefinition}.
     * @throws FlowParseException if parsing fails.
     */
    public FlowDefinition parse(String content) {
        Objects.requireNonNull(content, "The content cannot be null.");
        log.info("Loading flow definition from string content ({} chars)", content.length());
        return parse(new StringReader(content));
    }


    /**
     * Parses a {@link FlowDefinition} from the provided {@link InputStream}.
     *
     * @param inputStream The input stream containing the JSON definition. Must not be null.
     * @return The parsed {@link FlowDefinition}.
     * @throws FlowParseException if parsing fails due to an I/O error or malformed content.
     */
    public FlowDefinition parse(InputStream inputStream) throws FlowParseException {
        try {
            return objectMapper.readValue(inputStream, FlowDefinition.class);
        } catch (IOException e) {
            throw new FlowParseException("Failed to parse flow definition from input stream.", e);
        }
    }

    /**
     * Parses a {@link FlowDefinition} from the provided {@link Reader}.
     *
     * @param reader The reader containing the JSON definition. Must not be null.
     * @return The parsed {@link FlowDefinition}.
     * @throws FlowParseException if parsing fails due to an I/O error or malformed content.
     */
    public FlowDefinition parse(Reader reader) throws FlowParseException {
        try {
            return objectMapper.readValue(reader, FlowDefinition.class);
        } catch (IOException e) {
            throw new FlowParseException("Failed to parse flow definition from reader.", e);
        }
    }
}