package com.flowsentinel.starter.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as a stepId within a business flow defined on the class.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowStep {
    /**
     * The name of this specific stepId (e.g., "init", "transfer").
     * Must match a stepId ID in the corresponding flow definition.
     */
    String value();

    /**
     * If true, this stepId is the starting point of the flow.
     */
    boolean start() default false;
}