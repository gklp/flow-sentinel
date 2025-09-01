package com.flowsentinel.starter.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as a step within a business flow defined on the class.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowStep {
    /**
     * The name of this specific step (e.g., "init", "transfer").
     * Must match a step ID in the corresponding flow definition.
     */
    String value();

    /**
     * If true, this step is the starting point of the flow.
     */
    boolean start() default false;
}