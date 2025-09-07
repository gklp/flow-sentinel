
package com.flowsentinel.core.definition;

/**
 * Thrown when an error occurs during flow definition loading or processing.
 */
public class FlowDefinitionException extends RuntimeException {

    public FlowDefinitionException(String message) {
        super(message);
    }

    public FlowDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}