package com.flowsentinel.starter.web;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.id.FlowKey;
import com.flowsentinel.core.id.OwnerContext;
import com.flowsentinel.starter.web.annotation.Flow;
import com.flowsentinel.starter.web.annotation.FlowStep;
import com.flowsentinel.starter.web.provider.FlowIdProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An Aspect that intercepts methods annotated with {@link FlowStep} to enforce flow control logic
 * before the target method is executed.
 *
 * <p>This aspect acts as a guard, ensuring that all flow transitions are valid according
 * to the rules defined in the {@link FlowEngine}. It intercepts incoming requests,
 * extracts flow identifiers and the business payload, and validates the requested step
 * transition with the engine.
 *
 * <p>Its primary responsibilities are:
 * <ul>
 *   <li>Constructing a unique {@link FlowKey} for the current flow instance.</li>
 *   <li>Scanning the intercepted method's arguments to find the business payload,
 *       which is expected to be annotated with {@link RequestBody}.</li>
 *   <li>Invoking the {@link FlowEngine} with the extracted payload to validate the transition
 *       ({@code start} or {@code advance}) *before* executing the controller method.</li>
 *   <li>Allowing the controller method to proceed only if the flow transition is valid.</li>
 *   <li>Letting any exceptions from the engine (e.g., for illegal state transitions)
 *       bubble up to be handled by global exception handlers.</li>
 * </ul>
 */
@Aspect
@Component
public class FlowGuardAspect {

    private static final String TARGET_STEP_PAYLOAD_KEY = "__targetStep";

    private final FlowEngine flowEngine;
    private final FlowDefinitionRegistry registry;
    private final FlowIdProvider flowIdProvider;

    /**
     * Constructs the aspect with required dependencies.
     *
     * @param flowEngine     The core {@link FlowEngine} for managing flow state.
     * @param registry       The {@link FlowDefinitionRegistry} to look up flow definitions.
     * @param flowIdProvider The {@link FlowIdProvider} to extract owner and session context.
     */
    public FlowGuardAspect(FlowEngine flowEngine, FlowDefinitionRegistry registry, FlowIdProvider flowIdProvider) {
        this.flowEngine = flowEngine;
        this.registry = registry;
        this.flowIdProvider = flowIdProvider;
    }

    /**
     * The core advice that intercepts flow-controlled endpoints. It validates the flow
     * transition *before* executing the target method.
     * <p>
     * It extracts the payload from a {@link RequestBody} annotated argument, calls the
     * {@link FlowEngine} to validate and persist the state change, and only then proceeds
     * with the actual controller method logic.
     *
     * @param joinPoint      The proceeding join point for the intercepted method.
     * @param flowAnnotation The {@link Flow} annotation on the controller class.
     * @param flowStep       The {@link FlowStep} annotation on the intercepted method.
     * @return The result of the original controller method.
     * @throws Throwable if the {@link FlowEngine} rejects the transition or the controller
     *                   method itself throws an exception.
     */
    @Around("@within(flowAnnotation) && @annotation(flowStep)")
    public Object aroundFlowStep(ProceedingJoinPoint joinPoint, Flow flowAnnotation, FlowStep flowStep) throws Throwable {
        OwnerContext ownerContext = flowIdProvider.provide();
        FlowKey flowKey = new FlowKey(flowAnnotation.name(), ownerContext.ownerId(), ownerContext.flowId());

        FlowDefinition definition = registry.getDefinition(flowAnnotation.name())
                .orElseThrow(() -> new IllegalStateException("Flow definition not found: " + flowAnnotation.name()));

        Optional<String> fieldOpt = getRequestBodyArgumentName(joinPoint);
        Map<String, Object> payload = new HashMap<>();
        // Extract the payload from the @RequestBody-annotated method argument.
        // Add control information for the engine.
        fieldOpt.ifPresent(s -> payload.put(s, extractRequestBodyPayload(joinPoint)
                .orElse(null)));
        // Validate the transition and persist state with the engine BEFORE proceeding.
        if (flowStep.start()) {
            flowEngine.start(flowKey, definition, payload);
        } else {
            flowEngine.advance(flowKey, definition, payload);
        }

        // If the engine call was successful, proceed with the original method.
        return joinPoint.proceed();
    }

    /**
     * Scans the intercepted method's arguments to find one annotated with {@link RequestBody}.
     * If found, and if the argument is a {@link Map}, it returns it as a mutable {@link HashMap}.
     *
     * @param joinPoint The proceeding join point.
     * @return An {@link Optional} containing a mutable map of the payload, or empty if not found.
     */
    private Optional<Object> extractRequestBodyPayload(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof RequestBody) {
                    return Optional.of(args[i]);
                }
            }
        }
        // No @RequestBody annotation found on any argument.
        return Optional.empty();
    }

    /**
     * Scans the intercepted method's arguments to find the name of the parameter
     * annotated with {@link RequestBody}. This requires the code to be compiled
     * with the `-parameters` flag to preserve parameter names at runtime.
     *
     * @param joinPoint The proceeding join point.
     * @return An {@link Optional} containing the name of the parameter, or empty if not found.
     */
    private Optional<String> getRequestBodyArgumentName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof RequestBody) {
                    return Optional.of(parameterNames[i]);
                }
            }
        }
        return Optional.empty();
    }

}