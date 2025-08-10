package com.flowsentinel.core.engine;

/**
 * Base unchecked exception for all engine-related errors.
 *
 * @author gokalp
 */
public class FlowEngineException extends RuntimeException {
    /** Creates a new exception with the provided message. */
    public FlowEngineException(String message) { super(message); }
}