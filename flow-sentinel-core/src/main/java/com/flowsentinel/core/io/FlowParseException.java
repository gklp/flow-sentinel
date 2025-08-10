package com.flowsentinel.core.io;

/**
 * Unchecked exception thrown when parsing a flow definition fails.
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