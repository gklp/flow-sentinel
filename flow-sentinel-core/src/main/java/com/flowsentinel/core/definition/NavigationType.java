package com.flowsentinel.core.definition;

/**
 * Navigation mode for a step.
 * <ul>
 *   <li>{@link #SIMPLE}: a single linear transition.</li>
 *   <li>{@link #COMPLEX}: multiple conditional transitions evaluated in order.</li>
 * </ul>
 *
 * @author gokalp
 */
public enum NavigationType {
    SIMPLE,
    COMPLEX
}