package com.flowsentinel.starter.web;

import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.id.FlowKey;
import com.flowsentinel.core.id.OwnerContext;
import com.flowsentinel.core.runtime.FlowState;
import com.flowsentinel.starter.web.annotation.Flow;
import com.flowsentinel.starter.web.annotation.State;
import com.flowsentinel.starter.web.provider.FlowIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves method arguments of type {@link FlowState} that are annotated with {@link State}.
 * <p>
 * It constructs the appropriate {@link FlowKey} by combining the flow name from the
 * controller's {@link Flow} annotation with the {@link OwnerContext} from the
 * {@link FlowIdProvider}. It then uses this key to fetch the current {@link FlowState}
 * from the {@link FlowEngine}.
 */
public class FlowStateArgumentResolver implements HandlerMethodArgumentResolver {

    private final FlowEngine flowEngine;
    private final FlowIdProvider flowIdProvider;

    public FlowStateArgumentResolver(FlowEngine flowEngine, FlowIdProvider flowIdProvider) {
        this.flowEngine = flowEngine;
        this.flowIdProvider = flowIdProvider;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // The parameter must be of type FlowState and annotated with @State.
        return parameter.hasParameterAnnotation(State.class)
                && parameter.getParameterType().equals(FlowState.class);
    }

    @Override
    @NonNull
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("FlowState can only be resolved for HTTP requests");
        }

        // Find the @Flow annotation on the handler's class
        Class<?> handlerType = parameter.getContainingClass();
        Flow flowAnnotation = AnnotationUtils.findAnnotation(handlerType, Flow.class);
        if (flowAnnotation == null) {
            throw new IllegalStateException("Cannot resolve FlowState. The controller " + handlerType.getName() + " is not annotated with @Flow.");
        }
        String flowName = flowAnnotation.name();

        // Get owner and session context
        OwnerContext ownerContext = flowIdProvider.provide(request);
        if (ownerContext == null || ownerContext.flowId() == null) {
            throw new IllegalStateException("Could not determine flow context to resolve FlowState");
        }

        // Build the key and fetch the state
        FlowKey flowKey = new FlowKey(flowName, ownerContext.ownerId(), ownerContext.flowId());

        return flowEngine.getState(flowKey)
                .orElseThrow(() -> new IllegalStateException("No active FlowState found for key: " + flowKey.toStorageKey()));
    }
}