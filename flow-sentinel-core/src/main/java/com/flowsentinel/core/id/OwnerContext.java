package com.flowsentinel.core.id;

/**
 * Represents the context of the user or entity that owns a flow.
 *
 * @param flowId  The unique identifier for the session or request (e.g., JWT JTI, session ID).
 * @param ownerId An optional identifier for the owner (e.g., customer number, user ID).
 *                This is crucial for security to ensure a flow is accessed only by its owner.
 */
public record OwnerContext(String flowId, String ownerId) {
}