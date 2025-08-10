package com.flowsentinel.core.runner;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.NavigationType;
import com.flowsentinel.core.definition.StepDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.engine.DefaultFlowEngine;
import com.flowsentinel.core.engine.FlowEngineException;
import com.flowsentinel.core.id.FlowId;
import com.flowsentinel.core.id.StepId;
import com.flowsentinel.core.runtime.FlowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for FlowRunner utility.
 * Given-When-Then style with should* method names.
 * <p>
 * Author: gokalp
 */
public class FlowRunnerTest {

    private FlowDefinition completingDef() {
        var s1 = StepId.of("s1");
        var s2 = StepId.of("s2");
        var d1 = new StepDefinition.Builder().id(s1).addTransition(Transition.to(s2)).build();
        var d2 = new StepDefinition.Builder().id(s2).addTransition(Transition.eof()).build();
        return new FlowDefinition.Builder().id(FlowId.of("ok")).initialStep(s1).putStep(d1).putStep(d2).build();
    }

    private FlowDefinition loopingDef() {
        var a = StepId.of("A");
        var b = StepId.of("B");
        var da = new StepDefinition.Builder().id(a).addTransition(Transition.to(b)).build();
        var db = new StepDefinition.Builder().id(b).addTransition(Transition.to(a)).build();
        return new FlowDefinition.Builder().id(FlowId.of("loop")).initialStep(a).putStep(da).putStep(db).build();
    }

    /**
     * Executes a normal completing flow until EOF and reports success.
     */
    @Test
    @DisplayName("shouldRunToEndSuccessfully")
    void shouldRunToEndSuccessfully() {
        // Given
        var engine = new DefaultFlowEngine();
        var runner = new FlowRunner(engine);
        var state = new FlowState(completingDef());

        // When
        ExecutionReport report = runner.runToEnd(state, 10);

        // Then
        assertThat(report.completed()).isTrue();
        assertThat(report.stepsExecuted()).isEqualTo(2);
        assertThat(report.lastStepId()).isEqualTo(StepId.of("s2"));
    }

    /**
     * Stops when maxSteps is reached for a looping definition and reports not completed.
     */
    @Test
    @DisplayName("shouldStopWhenMaxStepsReached")
    void shouldStopWhenMaxStepsReached() {
        // Given
        var engine = new DefaultFlowEngine();
        var runner = new FlowRunner(engine);
        var state = new FlowState(loopingDef());

        // When
        ExecutionReport report = runner.runToEnd(state, 5);

        // Then
        assertThat(report.completed()).isFalse();
        assertThat(report.stepsExecuted()).isEqualTo(5);
        assertThat(report.lastStepId()).isNotNull();
    }

    /**
     * Validates argument checks for maxSteps and null state.
     */
    @Test
    @DisplayName("shouldValidateArguments")
    void shouldValidateArguments() {
        // Given
        var runner = new FlowRunner(new DefaultFlowEngine());

        // When / Then
        assertThatThrownBy(() -> runner.runToEnd(null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The state cannot be null.");
        assertThatThrownBy(() -> runner.runToEnd(new FlowState(completingDef()), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maxSteps must be greater than zero.");
    }

    /**
     * Ensures engine errors propagate through the runner.
     */
    @Test
    @DisplayName("shouldPropagateEngineErrors")
    void shouldPropagateEngineErrors() {
        // Given a definition that will fail in advance (no matching transition)
        var bad = new FlowDefinition.Builder()
                .id(FlowId.of("bad"))
                .initialStep(StepId.of("X"))
                .putStep(new StepDefinition.Builder().id(StepId.of("X"))
                        .navigationType(NavigationType.COMPLEX)
                        .addTransition(Transition.when(StepId.of("Y"), s -> false))
                        .build())
                .build();
        var runner = new FlowRunner(new DefaultFlowEngine());

        // When / Then
        assertThatThrownBy(() -> runner.runToEnd(new FlowState(bad), 3))
                .isInstanceOf(FlowEngineException.class)
                .hasMessageContaining("No transition matched for the step");
    }
}
