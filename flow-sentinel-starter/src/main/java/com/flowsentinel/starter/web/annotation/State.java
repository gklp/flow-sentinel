package com.flowsentinel.starter.web.annotation;

import com.flowsentinel.core.runtime.FlowState;
import com.flowsentinel.starter.web.FlowStateArgumentResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter to be resolved to the current {@link FlowState}.
 * <p>
 * This annotation signals the {@link FlowStateArgumentResolver}
 * to inject the active {@code FlowState} for the ongoing flow instance associated
 * with the current request. The parameter must be of type {@code FlowState}.
 *
 * <pre>
 * {@code
 * @RestController
 * @Flow(name = "onboarding")
 * public class OnboardingController {
 *
 *     @GetMapping("/current")
 *     public String showCurrentStep(@State FlowState currentState) {
 *         return "You are at: " + currentState.currentStep().id();
 *     }
 * }
 * }
 * </pre>
 *
 * @see FlowStateArgumentResolver
 * @see FlowState
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface State {
}