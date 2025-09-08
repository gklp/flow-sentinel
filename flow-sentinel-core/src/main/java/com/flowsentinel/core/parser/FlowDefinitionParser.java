package com.flowsentinel.core.parser;

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

public final class FlowDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionParser.class);
    private final ObjectMapper objectMapper;

    public FlowDefinitionParser(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FlowDefinition parse(Path path) {
        Objects.requireNonNull(path, "The path cannot be null.");
        log.info("Loading flow definition from file [{}]", path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new FlowParseException("Failed to read flow definition from file: " + path, e);
        }
    }

    public FlowDefinition parse(String content) {
        Objects.requireNonNull(content, "The content cannot be null.");
        log.info("Loading flow definition from string content ({} chars)", content.length());
        return parse(new StringReader(content));
    }

    public FlowDefinition parse(InputStream inputStream) throws FlowParseException {
        try {
            return objectMapper.readValue(inputStream, FlowDefinition.class);
        } catch (IOException e) {
            throw new FlowParseException("Failed to parse flow definition from input stream.", e);
        }
    }

    public FlowDefinition parse(Reader reader) throws FlowParseException {
        try {
            return objectMapper.readValue(reader, FlowDefinition.class);
        } catch (IOException e) {
            throw new FlowParseException("Failed to parse flow definition from reader.", e);
        }
    }

}