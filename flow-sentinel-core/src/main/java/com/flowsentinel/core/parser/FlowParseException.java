package com.flowsentinel.core.parser;

/**
 * Unchecked exception thrown when a flow definition cannot be parsed.
 *
 * @author gokalp
 */
public class FlowParseException extends RuntimeException {

    public FlowParseException(String message) {
        super(message);
    }

    public FlowParseException(String message, Throwable cause) {
        super(message, cause);
    }
}