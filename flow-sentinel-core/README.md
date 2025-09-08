# FlowSentinel Core

**FlowSentinel Core** is the framework-independent engine for defining, validating, and executing multi-stepId flows.  
It provides the essential building blocks to model flows as JSON, manage their execution state, and enforce navigation and validation rules without any external framework dependencies.

## Key Responsibilities
- **Flow Model** – Core domain classes such as `FlowId`, `StepId`, `FlowDefinition`, and `StepDefinition`.
- **Execution Engine** – Orchestrates flow execution via `FlowEngine` and `DefaultFlowEngine`.
- **Navigation Rules** – Supports simple and complex stepId transitions.
- **Validation** – Ensures completed steps are immutable and flow rules are respected.
- **I/O Utilities** – Load and parse flow definitions from files or streams.
- **Runner Utilities** – Programmatically execute flows with reporting capabilities.

## Requirements
- **Java Version:** 17 or higher
- **Dependencies:** None (pure Java)

## Example
```java
FlowId flowId = new FlowId("signup-flow");
FlowDefinition definition = FlowFileLoader.loadFromFile("flows/signup.json");
FlowEngine engine = new DefaultFlowEngine();
FlowState state = engine.start(definition);

state = engine.advance(state, new StepId("step1"));
System.out.println("Current stepId: " + state.getCurrentStep());
