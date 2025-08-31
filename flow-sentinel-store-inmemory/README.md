# flow-sentinel-store-inmemory

An in-memory implementation of the FlowSentinel `FlowStore` interface, using Caffeine as a backend.  
Useful for testing, prototyping, or single-instance low-volume deployments.

---

## ✅ Features

- ⚡ Fast, in-memory storage of `FlowMeta` and `FlowSnapshot`
- 🔁 Optional sliding TTL expiration
- 🧠 Support for absolute TTL cap
- 🔧 Fully configurable via `FlowSentinelInMemoryProperties`
- ☕ No external dependencies

---

## 🚀 Usage

To use the in-memory store, simply register the following Spring bean:

```java
@Bean
public FlowStore flowStore() {
    return new InMemoryFlowStore();
}
```

Or if using Spring Boot auto-configuration:

```yaml
flow-sentinel.inmemory.enabled: true
```

> Note: Only one `FlowStore` bean should be active in the context.

---

## 🧩 Configuration Properties

The in-memory store is configured via the following properties:

```yaml
flow-sentinel.inmemory:
  enabled: true
  ttl: 30s
  absolute-ttl: 5m
  maximum-size: 10000
  sliding-enabled: true
  sliding-reset: ON_READ_AND_WRITE
```

| Property             | Type      | Default             | Description                                                                 |
|----------------------|-----------|---------------------|-----------------------------------------------------------------------------|
| `ttl`                | Duration  | `60s`               | Base TTL for all entries (sliding window if enabled)                        |
| `absolute-ttl`       | Duration  | `0s` (disabled)     | Max lifetime for any entry regardless of activity                           |
| `maximum-size`       | long      | `10000`             | Maximum number of flow entries cached (evicts LRU beyond this)              |
| `sliding-enabled`    | boolean   | `true`              | Enables sliding expiration per access or update                            |
| `sliding-reset`      | enum      | `ON_READ_AND_WRITE` | When to reset TTL (`ON_READ`, `ON_WRITE`, `ON_READ_AND_WRITE`)              |

---

## 🧪 Testing Use

You can directly instantiate the store for unit tests:

```java
InMemoryFlowStore store = new InMemoryFlowStore();
```

Or provide a custom configuration:

```java
FlowSentinelInMemoryProperties props = new FlowSentinelInMemoryProperties();
props.setTtl(Duration.ofSeconds(30));
props.setSlidingEnabled(false);
InMemoryFlowStore store = new InMemoryFlowStore(props);
```

---

## 🔐 Notes

- This module is **not suitable** for distributed or clustered deployments.
- Expiration behavior is powered by Caffeine’s `Expiry<K, V>` API and is deterministic.
- `FlowMeta` and `FlowSnapshot` are stored in separate caches internally.

---

## 📂 Package Structure

```
com.flowsentinel.store.inmemory
├── config
│   └── FlowSentinelInMemoryProperties.java
├── core
│   └── InMemoryFlowStore.java
```

---

## 📦 Dependency

If using manually (non-autoconfig):

```xml
<dependency>
  <groupId>com.flowsentinel</groupId>
  <artifactId>flow-sentinel-store-inmemory</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## 👨‍💻 Author

Maintained by [FlowSentinel](https://github.com/gklp/flow-sentinel)
