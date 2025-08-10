package com.flowsentinel.core.io;

import com.flowsentinel.core.definition.FlowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utility for loading a {@link FlowDefinition} from various sources
 * (file path, string, input stream).
 * <p>
 * Delegates parsing to a {@link FlowDefinitionParser} implementation.
 * </p>
 *
 * @author gokalp
 */
public final class FlowFileLoader {

    private static final Logger log = LoggerFactory.getLogger(FlowFileLoader.class);

    private final FlowDefinitionParser parser;

    /**
     * Creates a loader with the given parser.
     *
     * @param parser parser implementation
     */
    public FlowFileLoader(FlowDefinitionParser parser) {
        this.parser = Objects.requireNonNull(parser, "The parser cannot be null.");
    }

    /**
     * Loads a {@link FlowDefinition} from the given {@link Path}.
     *
     * @param path file path
     * @return loaded flow definition
     */
    public FlowDefinition loadFromFile(Path path) {
        Objects.requireNonNull(path, "The path cannot be null.");
        log.info("Loading flow definition from file [{}]", path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parser.parse(reader);
        } catch (IOException e) {
            throw new FlowParseException("Failed to read flow definition from file: " + path, e);
        }
    }

    /**
     * Loads a {@link FlowDefinition} from the given string content.
     *
     * @param content flow definition content
     * @return loaded flow definition
     */
    public FlowDefinition loadFromString(String content) {
        Objects.requireNonNull(content, "The content cannot be null.");
        log.info("Loading flow definition from string content ({} chars)", content.length());
        // StringReader does not throw IOException; no try/catch needed.
        return parser.parse(new StringReader(content));
    }

    /**
     * Loads a {@link FlowDefinition} from an {@link InputStream}.
     *
     * @param input input stream containing flow definition
     * @return loaded flow definition
     */
    public FlowDefinition loadFromStream(InputStream input) {
        Objects.requireNonNull(input, "The input stream cannot be null.");
        log.info("Loading flow definition from input stream");
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return parser.parse(reader);
        } catch (IOException e) {
            throw new FlowParseException("Failed to read flow definition from input stream", e);
        }
    }
}
