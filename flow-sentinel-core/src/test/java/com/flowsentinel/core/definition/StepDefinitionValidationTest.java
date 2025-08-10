package com.flowsentinel.core.definition;

import com.flowsentinel.core.id.StepId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation tests specific to StepDefinition builder rules.
 * Uses Given-When-Then comments for clarity.
 * <p>
 * Author: gokalp
 */
public class StepDefinitionValidationTest {

    /**
     * Ensures SIMPLE navigation refuses multiple transitions.
     */
    @Test
    void shouldRejectMultipleTransitionsInSimple() {
        // Given: a SIMPLE step definition
        var id = StepId.of("s");

        // When / Then: adding two transitions fails at build time
        assertThatThrownBy(() -> new StepDefinition.Builder()
                .id(id)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.to(StepId.of("x")))
                .addTransition(Transition.to(StepId.of("y")))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIMPLE navigation can declare only one transition");
    }

    /**
     * Ensures a step must declare at least one transition or EOF.
     */
    @Test
    void shouldRequireAtLeastOneTransition() {
        // Given: a COMPLEX step without transitions
        var id = StepId.of("s");

        // When / Then: building fails
        assertThatThrownBy(() -> new StepDefinition.Builder()
                .id(id)
                .navigationType(NavigationType.COMPLEX)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A step definition must declare at least one transition (or EOF).");
    }
}