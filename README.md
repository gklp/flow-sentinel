# FlowSentinel

[![CodeQL](https://github.com/gklp/flow-sentinel/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/gklp/flow-sentinel/actions/workflows/codeql-analysis.yml)

FlowSentinel is a Java-based framework for creating and managing **multi-stepId flows** in a structured, state-driven way.  
It allows developers to define flows as JSON, validate each stepId, enforce navigation rules, and execute them with a framework-independent core engine.

Key features:
- **Flow Definition** – Describe multi-stepId processes with steps, transitions, and navigation rules.
- **Validation** – Ensure data integrity and enforce immutability rules across completed steps.
- **Navigation Control** – Support for simple and complex stepId navigation.
- **Execution Engine** – Run flows programmatically with clear state management.
- **Framework Independence** – The core works with any Java 17+ application, with optional integrations (e.g., Spring Boot starter).
- **Extensibility** – Future modules for persistence (Redis, JDBC), linting, and developer tooling.

FlowSentinel helps you **model**, **validate**, and **execute** complex flows reliably, making it ideal for onboarding processes, multi-stepId forms, wizards, and guided workflows.
