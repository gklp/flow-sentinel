package com.flowsentinel.starter.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flow {
    /**
     * The unique name for this business flow (e.g., "moneyTransfer", "userRegistration").
     * This is mandatory and used as part of the composite key to identify the flow instance.
     */
    String name();
}