package com.flowsentinel.core.runner;

import com.flowsentinel.core.id.StepId;

/**
 * Represents the result of a {@link FlowRunner} execution.
 * <p>
 * This immutable record provides a summary of a flow run, including whether
 * it completed, how many steps were executed, and the final step reached.
 *
 * @param completed     {@code true} if the flow reached an end-of-flow state.
 * @param stepsExecuted The total number of steps advanced during the run.
 * @param lastStepId    The {@link StepId} of the last step the flow was on.
 */
public record ExecutionReport(
        boolean completed,
        int stepsExecuted,
        StepId lastStepId
) {
}