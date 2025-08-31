# FlowSentinel - Redis Store

This module provides a Redis-backed implementation of the `FlowStore` interface for the FlowSentinel framework. It is designed to persist flow metadata and snapshots in a Redis database, making it an excellent choice for distributed and stateful applications.

## Features

- **Persistent Storage**: Securely stores flow state in Redis.
- **Automatic Expiration**: Leverages Redis's Time-To-Live (TTL) feature for automatic cleanup of stale flow records.
- **Sliding Expiration**: Supports session-like behavior by extending a flow's lifetime on access, which is ideal for interactive user flows.
- **Flexible Connection Management**: Offers two modes (`SHARED` or `DEDICATED`) to either reuse an existing application-wide Redis connection or create a separate, isolated one.
- **Atomic Operations**: Guarantees data consistency for critical operations (like creation and deletion) by using atomic Lua scripts.

## Setup

Add the following dependency to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.gokalpkuscu.flowsentinel</groupId>
    <artifactId>flow-sentinel-store-redis</artifactId>
    <version>1.0.0</version> <!-- Use your project's version -->
</dependency>
```

The module uses Spring Boot's auto-configuration and will activate automatically once the dependency is present and a `RedisConnectionFactory` bean is available.

---

## Configuration

The store can be configured via your `application.yml` or `application.properties` file. All properties are prefixed with `flow-sentinel.storage.redis`.

### Connection Modes: `SHARED` vs. `DEDICATED`

You can control how the Redis store connects to your Redis server using the `mode` property. This is a critical choice for managing performance and resources.

#### `SHARED` (Default Mode)
- **How it works**: The store reuses the primary `RedisConnectionFactory` bean already defined in your Spring application context.
- **Choose this if**:
    - Your application already uses Redis for other purposes (like caching).
    - You prefer simplicity and want to manage a single connection pool.
    - The expected load from FlowSentinel is low to moderate and won't negatively impact other parts of your application.

#### `DEDICATED`
- **How it works**: The store creates its own separate `LettuceConnectionFactory`, completely isolated from the rest of your application. You must provide the connection details (`host`, `port`, etc.).
- **Choose this if**:
    - You expect high throughput or frequent operations from FlowSentinel and want to prevent it from affecting your application's main Redis cache or connection pool.
    - You need to fine-tune connection settings (e.g., timeouts) specifically for flow storage, which might have different requirements than your general-purpose cache.
    - You want to connect to a different Redis instance or database than the one used by the rest of your application.

### Configuration Properties

The following table details the available configuration properties:

| Property                   | Description                                                                                                                                                                                                          | Default Value       |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| `mode`                     | The connection mode. Can be `SHARED` (reuses the app's connection factory) or `DEDICATED` (creates a new one). See the section above for details.                                                                      | `SHARED`            |
| `key-prefix`               | A string prefix for all Redis keys created by the store. This is essential for preventing key collisions if you use the same Redis database for multiple purposes.                                                      | `fs:flow:`          |
| `ttl-seconds`              | The base Time-To-Live (TTL) for flow records, in seconds. This is the initial lifetime of a record. It is also used as the extension duration when sliding expiration is triggered.                                      | `3600` (1 hour)     |
| `sliding-enabled`          | If `true`, enables the sliding expiration policy. The TTL of a record is refreshed on access, keeping active flows alive longer.                                                                                       | `false`             |
| `sliding-reset`            | Defines which operation(s) trigger a TTL refresh when `sliding-enabled` is true. Values: `ON_READ`, `ON_WRITE`, `ON_READ_AND_WRITE`.                                                                                     | `ON_READ`           |
| `absolute-ttl-seconds`     | An optional absolute lifetime cap in seconds. If set to a value `> 0`, a flow record will be deleted after this duration, regardless of sliding expiration activity. Set to `0` to disable the cap.                     | `0` (disabled)      |
|                            |                                                                                                                                                                                                                      |                     |
| **Dedicated Mode Properties** | **(Only used when `mode` is `DEDICATED`)**                                                                                                                                                                           |                     |
| `host`                     | The Redis server host.                                                                                                                                                                                               | `localhost`         |
| `port`                     | The Redis server port.                                                                                                                                                                                               | `6379`              |
| `database`                 | The Redis database index to connect to.                                                                                                                                                                              | `0`                 |
| `password`                 | The password for the Redis server. Leave empty if no password is required.                                                                                                                                           | `null`              |
| `connect-timeout-ms`       | Connection timeout in milliseconds for the dedicated Lettuce client. `0` uses the client's default.                                                                                                                  | `0`                 |
| `command-timeout-ms`       | Command timeout in milliseconds for the dedicated Lettuce client. `0` uses the client's default.                                                                                                                     | `0`                 |

### Example Configuration (`application.yml`)

Here is a comprehensive example demonstrating various configuration options.

```yaml
flow-sentinel:
  storage:
    redis:
      # --- Connection Choice ---
      # Use 'DEDICATED' mode to isolate this from your main app's Redis usage.
      mode: DEDICATED
      host: redis.my-company.com
      port: 6379
      database: 1 # Use a separate DB index for flows
      # password: "your-redis-password"
      
      # --- Key & TTL Settings ---
      key-prefix: "my-app:flows:" # Custom prefix for this application
      
      # Set a base TTL of 30 minutes for all flows.
      ttl-seconds: 1800 
      
      # --- Sliding Expiration ---
      # Keep flows alive as long as they are being used (e.g., read).
      sliding-enabled: true
      sliding-reset: ON_READ
      
      # --- Absolute Lifetime Cap ---
      # Ensure no flow lives longer than 24 hours, even if it's constantly active.
      # This prevents "immortal" flows and helps with long-term data cleanup.
      absolute-ttl-seconds: 86400 # 24 hours
```