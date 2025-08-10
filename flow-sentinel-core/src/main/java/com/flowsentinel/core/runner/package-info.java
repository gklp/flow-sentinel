/**
 * Contains utilities for executing flows until completion.
 * <p>
 * This package provides the {@link com.flowsentinel.core.runner.FlowRunner}
 * which coordinates the execution of a {@link com.flowsentinel.core.runtime.FlowState}
 * through a provided {@link com.flowsentinel.core.engine.FlowEngine}.
 * </p>
 * <h2>Design Principles</h2>
 * <ul>
 *     <li>Framework-agnostic — no Spring or external library dependencies.</li>
 *     <li>Immutable execution reports via {@link com.flowsentinel.core.runner.ExecutionReport}.</li>
 *     <li>Clear control over maximum steps to prevent infinite loops.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * FlowEngine engine = new DefaultFlowEngine();
 * FlowRunner runner = new FlowRunner(engine);
 * ExecutionReport report = runner.runToEnd(flowState, 100);
 * }</pre>
 *
 * @author gokalp
 * @since 1.0
 */
package com.flowsentinel.core.runner;
