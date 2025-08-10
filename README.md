# FlowSentinel

**FlowSentinel** is a Spring Boot Starter for securely managing multi-step processes on the server side.  
It uses JSON-based flow definitions to map each step to controller methods, preventing step skipping and, by default, disallowing changes to completed step data.

With session or token-based actor resolution, TTL management, Redis/JDBC store support, Actuator endpoints, and Prometheus metrics, FlowSentinel delivers a secure, observable, and extensible flow engine for enterprise applications.
