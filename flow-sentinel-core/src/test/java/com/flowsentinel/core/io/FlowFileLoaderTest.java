package com.flowsentinel.core.io;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.NavigationType;
import com.flowsentinel.core.definition.StepDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.id.FlowId;
import com.flowsentinel.core.id.StepId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for framework-agnostic file loading utilities.
 * Each test follows Given-When-Then and starts with 'should...'.
 * <p>
 * Author: gokalp
 */
public class FlowFileLoaderTest {

    private FlowDefinition demoDefinition() {
        var start = StepId.of("start");
        var end = StepId.of("end");
        var s1 = new StepDefinition.Builder().id(start)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.to(end)).build();
        var s2 = new StepDefinition.Builder().id(end)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.eof()).build();
        return new FlowDefinition.Builder()
                .id(FlowId.of("demo"))
                .initialStep(start)
                .putStep(s1).putStep(s2).build();
    }

    /**
     * Verifies that loadFromString delegates to the parser.
     */
    @Test
    void shouldLoadFromStringUsingParser() {
        // Given
        FlowDefinition expected = demoDefinition();
        FlowDefinitionParser parser = new FakeParser(expected);
        FlowFileLoader loader = new FlowFileLoader(parser);

        // When
        FlowDefinition actual = loader.loadFromString("any");

        // Then
        assertThat(actual).isSameAs(expected);
    }

    /**
     * Verifies that loadFromStream delegates to the parser.
     */
    @Test
    void shouldLoadFromStreamUsingParser() {
        // Given
        FlowDefinition expected = demoDefinition();
        FlowDefinitionParser parser = new FakeParser(expected);
        FlowFileLoader loader = new FlowFileLoader(parser);
        InputStream in = new ByteArrayInputStream("irrelevant".getBytes());

        // When
        FlowDefinition actual = loader.loadFromStream(in);

        // Then
        assertThat(actual).isSameAs(expected);
    }

    /**
     * Ensures I/O errors are wrapped as FlowParseException for file loads.
     */
    @Test
    void shouldWrapIoErrorsWhenFileMissing() {
        // Given
        FlowDefinitionParser parser = new FakeParser(demoDefinition());
        FlowFileLoader loader = new FlowFileLoader(parser);
        Path missing = Path.of("this/definitely/does/not/exist.flow");

        // When / Then
        assertThatThrownBy(() -> loader.loadFromFile(missing))
                .isInstanceOf(FlowParseException.class)
                .hasMessageContaining("Failed to read flow definition from file");
    }

    /**
     * Ensures parser failures propagate as FlowParseException.
     */
    @Test
    void shouldPropagateParserErrors() {
        // Given
        FlowDefinitionParser parser = reader -> {
            throw new FlowParseException("Boom");
        };
        FlowFileLoader loader = new FlowFileLoader(parser);

        // When / Then
        assertThatThrownBy(() -> loader.loadFromString("x"))
                .isInstanceOf(FlowParseException.class)
                .hasMessage("Boom");
    }

    /**
     * Guards against null arguments and verifies messages.
     */
    @Test
    void shouldRejectNullArguments() {
        // Given / When / Then
        assertThatThrownBy(() -> new FlowFileLoader(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The parser cannot be null.");

        FlowDefinitionParser parser = new FakeParser(demoDefinition());
        FlowFileLoader loader = new FlowFileLoader(parser);

        assertThatThrownBy(() -> loader.loadFromFile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The path cannot be null.");
        assertThatThrownBy(() -> loader.loadFromStream(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The input stream cannot be null.");
        assertThatThrownBy(() -> loader.loadFromString(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The content cannot be null.");
    }

    /**
     * Verifies that loadFromFile successfully reads from an existing file and
     * delegates to the parser.
     */
    @Test
    void shouldLoadFromExistingFileUsingParser() throws Exception {
        // Given
        FlowDefinition expected = demoDefinition();
        FlowDefinitionParser parser = new FakeParser(expected);
        FlowFileLoader loader = new FlowFileLoader(parser);

        // Create a temporary file with some content
        Path tempFile = createTempFile("flow", ".txt");
        writeString(tempFile, "dummy-content");

        // When
        FlowDefinition actual = loader.loadFromFile(tempFile);

        // Then
        assertThat(actual).isSameAs(expected);

        // Cleanup
        deleteIfExists(tempFile);
    }

    // --- Test helper ---
    private record FakeParser(FlowDefinition result) implements FlowDefinitionParser {

        @Override
        public FlowDefinition parse(Reader reader) {
            return result;
        }
    }
}
