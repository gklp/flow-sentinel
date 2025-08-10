package com.flowsentinel.core.engine;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.NavigationType;
import com.flowsentinel.core.definition.StepDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.id.FlowId;
import com.flowsentinel.core.id.StepId;
import com.flowsentinel.core.runtime.FlowState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Core engine happy-path and edge-case tests.
 * Each test uses Given-When-Then sections for readability.
 * <p>
 * Author: gokalp
 */
public class DefaultFlowEngineTest {

    private FlowDefinition simpleDef() {
        var start = StepId.of("start");
        var end = StepId.of("end");

        var s1 = new StepDefinition.Builder()
                .id(start)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.to(end))
                .build();

        var s2 = new StepDefinition.Builder()
                .id(end)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.eof())
                .build();

        return new FlowDefinition.Builder()
                .id(FlowId.of("demo"))
                .initialStep(start)
                .putStep(s1)
                .putStep(s2)
                .build();
    }

    /**
     * Verifies a two-step SIMPLE flow advances once and then completes at EOF.
     */
    @Test
    void shouldAdvanceSimpleFlowAndComplete() {
        // Given: a simple flow with start -> end -> EOF
        var engine = new DefaultFlowEngine();
        var def = simpleDef();
        var state = new FlowState(def);

        // When: advancing twice
        assertThat(state.currentStep()).isEqualTo(def.initialStep());
        engine.next(state);
        engine.next(state);

        // Then: flow reaches EOF and is markedly completed
        assertThat(state.isCompleted()).isTrue();
        assertThat(state.currentStep()).isEqualTo(StepId.of("end"));
    }

    /**
     * Ensures COMPLEX navigation evaluates conditions in order and picks the first match.
     */
    @Test
    void shouldSupportComplexConditionalNavigation() {
        // Given: step A with two conditional transitions to B or C
        var a = StepId.of("A");
        var b = StepId.of("B");
        var c = StepId.of("C");

        var stepA = new StepDefinition.Builder()
                .id(a)
                .navigationType(NavigationType.COMPLEX)
                .addTransition(Transition.when(b, s -> "goB".equals(s.attributes().get("k"))))
                .addTransition(Transition.when(c, s -> "goC".equals(s.attributes().get("k"))))
                .build();
        var stepB = new StepDefinition.Builder().id(b).addTransition(Transition.eof()).build();
        var stepC = new StepDefinition.Builder().id(c).addTransition(Transition.eof()).build();

        var def = new FlowDefinition.Builder()
                .id(FlowId.of("cond"))
                .initialStep(a)
                .putStep(stepA)
                .putStep(stepB)
                .putStep(stepC)
                .build();
        var engine = new DefaultFlowEngine();

        // When: attribute leads to B
        var s1 = new FlowState(def);
        s1.setAttribute("k", "goB");
        engine.next(s1);
        // Then
        assertThat(s1.currentStep()).isEqualTo(b);

        // When: attribute leads to C
        var s2 = new FlowState(def);
        s2.setAttribute("k", "goC");
        engine.next(s2);
        // Then
        assertThat(s2.currentStep()).isEqualTo(c);
    }

    /**
     * Confirms the engine fails with a clear message when no transition matches.
     */
    @Test
    void shouldThrowWhenNoTransitionMatches() {
        // Given: a COMPLEX step with a single false predicate
        var a = StepId.of("A");
        var stepA = new StepDefinition.Builder()
                .id(a)
                .navigationType(NavigationType.COMPLEX)
                .addTransition(Transition.when(StepId.of("X"), s -> false))
                .build();
        var def = new FlowDefinition.Builder()
                .id(FlowId.of("bad"))
                .initialStep(a)
                .putStep(stepA)
                .build();
        var engine = new DefaultFlowEngine();
        var state = new FlowState(def);

        // When / Then: advancing fails
        assertThatThrownBy(() -> engine.next(state))
                .isInstanceOf(FlowEngineException.class)
                .hasMessageContaining("No transition matched for the step");
    }

    /**
     * Validates FlowDefinition builder rejects an initial step not present in the steps map.
     */
    @Test
    void shouldRejectInvalidDefinitions() {
        // Given: steps A and B, but the initial step is unknown
        var a = StepId.of("A");
        var b = StepId.of("B");
        var stepA = new StepDefinition.Builder()
                .id(a)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.to(b))
                .build();
        var stepB = new StepDefinition.Builder()
                .id(b)
                .navigationType(NavigationType.SIMPLE)
                .addTransition(Transition.to(a))
                .build();

        // When / Then: building definition fails
        assertThatThrownBy(() -> new FlowDefinition.Builder()
                .id(FlowId.of("x"))
                .initialStep(StepId.of("unknown"))
                .putStep(stepA)
                .putStep(stepB)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The initial step must be present");
    }

    /**
     * Checks value object validation messages and equality-by-value semantics.
     */
    @Test
    void shouldValidateValueObjectsEqualityAndMessages() {
        // Given / When / Then: invalid inputs
        assertThatThrownBy(() -> FlowId.of(" ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The flowId cannot be null or blank.");
        assertThatThrownBy(() -> StepId.of(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The stepId cannot be null or blank.");

        // And: equality by value
        assertThat(FlowId.of("f")).isEqualTo(FlowId.of("f"));
        assertThat(StepId.of("s")).hasToString("s");
    }

    /**
     * Ensures advancing after EOF results in a clear error.
     */
    @Test
    void shouldErrorIfAdvancingCompletedFlow() {
        // Given: a single-step flow ending with EOF
        var start = StepId.of("start");
        var s1 = new StepDefinition.Builder().id(start).addTransition(Transition.eof()).build();
        var def = new FlowDefinition.Builder().id(FlowId.of("f")).initialStep(start).putStep(s1).build();
        var engine = new DefaultFlowEngine();
        var state = new FlowState(def);

        // When: advance once to reach EOF
        engine.next(state);
        // Then: second advance throws
        assertThatThrownBy(() -> engine.next(state))
                .isInstanceOf(FlowEngineException.class)
                .hasMessageContaining("already completed");
    }

    /**
     * Guards against null state being passed to the engine.
     */
    @Test
    void shouldThrowOnNullState() {
        // Given / When / Then
        var engine = new DefaultFlowEngine();
        assertThatThrownBy(() -> engine.next(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The state cannot be null.");
    }
}