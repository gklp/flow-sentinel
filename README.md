# FlowSentinel

[![CodeQL](https://github.com/gklp/flow-sentinel/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/gklp/flow-sentinel/actions/workflows/codeql-analysis.yml)
[![Build](https://github.com/gklp/flow-sentinel/actions/workflows/maven.yml/badge.svg)](https://github.com/gklp/flow-sentinel/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/gklp/flow-sentinel/branch/main/graph/badge.svg)](https://codecov.io/gh/gklp/flow-sentinel)
[![Maven Central](https://img.shields.io/maven-central/v/com.flowsentinel/flow-sentinel-core.svg)](https://search.maven.org/artifact/com.flowsentinel/flow-sentinel-core)


FlowSentinel is a Java-based framework for creating and managing **multi-step flows** in a structured, state-driven way.  
It allows developers to define flows as JSON, validate each step, enforce navigation rules, and execute them with a framework-independent core engine.

Key features:
- **Flow Definition** – Describe multi-step processes with steps, transitions, and navigation rules.
- **Validation** – Ensure data integrity and enforce immutability rules across completed steps.
- **Navigation Control** – Support for simple and complex step navigation.
- **Execution Engine** – Run flows programmatically with clear state management.
- **Framework Independence** – The core works with any Java 17+ application, with optional integrations (e.g., Spring Boot starter).
- **Extensibility** – Future modules for persistence (Redis, JDBC), linting, and developer tooling.

FlowSentinel helps you **model**, **validate**, and **execute** complex flows reliably, making it ideal for onboarding processes, multi-step forms, wizards, and guided workflows.
