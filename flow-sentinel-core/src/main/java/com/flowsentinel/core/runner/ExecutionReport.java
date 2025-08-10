package com.flowsentinel.core.runner;

import com.flowsentinel.core.id.StepId;

/**
 * Immutable report produced after executing a flow run.
 *
 * @param completed whether the flow completed
 * @param stepsExecuted number of steps executed
 * @param lastStepId identifier of the last step reached
 *
 * @author gokalp
 */
public record ExecutionReport(boolean completed, int stepsExecuted, StepId lastStepId) {
}