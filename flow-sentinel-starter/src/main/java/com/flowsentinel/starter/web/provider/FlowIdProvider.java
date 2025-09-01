package com.flowsentinel.starter.web.provider;

import com.flowsentinel.core.id.OwnerContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A provider responsible for extracting the {@link OwnerContext} from a request.
 * The context includes the flow's session identifier (flowId) and its owner's identifier (ownerId).
 */
public interface FlowIdProvider {

    /**
     * Provides the owner and session context from the incoming request.
     *
     * @return An {@link OwnerContext} containing the flow and owner IDs.
     */
    OwnerContext provide();

    /**
     * Provides the owner and session context from the incoming request.
     *
     * @param request The current {@link HttpServletRequest}.
     * @return An {@link OwnerContext} containing the flow and owner IDs.
     */
    default OwnerContext provide(HttpServletRequest request) {
        return null;
    }
}