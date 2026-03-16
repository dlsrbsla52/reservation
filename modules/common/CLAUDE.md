# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`modules/common` is a **shared library** (no `bootJar`, plain `jar`) consumed by all other modules in the `reservation` MSA project. It provides auto-configured infrastructure: response wrapping, exception handling, bulkhead concurrency guards, inter-service HTTP client, JWT token abstraction, JPA base entities, and QueryDSL setup.

Base package: `com.media.bus.common`

## Build Commands

```bash
# From project root
./gradlew :modules:common:build          # Build the library jar
./gradlew :modules:common:test           # Run tests
./gradlew :modules:common:test --tests "com.media.bus.common.ClassName.methodName"

# Java 25 Byte Buddy workaround (already in build.gradle, but needed for manual runs)
-Dnet.bytebuddy.experimental=true
```

## Auto-Configuration

Beans are registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Consuming modules get all beans without component scanning.

| Auto-config class | Condition | What it registers |
|---|---|---|
| `CommonCoreAutoConfiguration` | `@ConditionalOnClass(Aspect.class)` | `TransactionalBulkheadAspect`, `BoundedConcurrencyAspect` |
| `CommonWebMvcAutoConfiguration` | Servlet env only | All `ResponseBodyWrapper` beans, `ResponseAdvisor`, `ExceptionAdvisor` |
| `RestClientConfig` | Servlet env only | `internalRestClient` bean |
| `QueryDslConfig` | Always | `JPAQueryFactory` bean |
| `ThreadPoolConfig` | Always | Shared executor config |
| `SwaggerConfig` | Always | SpringDoc OpenAPI |

## Required Configuration (consuming modules)

```yaml
hig:
  bulkhead:
    database-name: <name>   # REQUIRED — must match a resilience4j.bulkhead.instances key

rest:
  service:
    apply-patterns:         # Ant-path patterns where ResponseAdvisor wraps responses
      - /api/**
```

## Key Extension Points

### Adding a new response wrapper type
Implement `ResponseBodyWrapper` and register as a `@Bean`. `ResponseAdvisor` auto-discovers all `ResponseBodyWrapper` beans and tries them in `@Order` sequence.

Built-in pipeline order:
1. `PassthroughBodyWrapper` — `AbstractView` or `String` → pass through unchanged
2. `NullBodyWrapper` — `null` → `NoDataView`
3. `PageResultBodyWrapper` — `PageResult` → `PageView`
4. `DefaultObjectBodyWrapper` — anything else → `DataView`

### Adding a new exception type
Extend `BaseException(Result result, ...)`. The `Result` interface is implemented by the `CommonResult` enum; module-specific enums can also implement `Result`.

**Reserved result codes:** `00000`–`00299` (common module). Do not use these in other modules.

### HTTP status mapping (ExceptionAdvisor)
| Exception | HTTP Status |
|---|---|
| `NoAuthenticationException` | 401 |
| `NoAuthorizationException` / `AccessDeniedException` | 403 |
| `StorageException` | 404 |
| `BaseException` | 500 |
| `Exception` (catch-all) | 500 |

## Concurrency Patterns

### TransactionalBulkheadAspect
Wraps every `@Transactional` method and every `@Repository` bean with a Resilience4j semaphore bulkhead. Re-entrance is tracked with `ScopedValue<Boolean>` (Java 25) — do **not** replace with `ThreadLocal`.

### @BoundedConcurrency
For `@Async` methods returning `CompletableFuture<T>`. The annotated method must return `CompletableFuture`; the aspect acquires the named `Semaphore` bean before proceeding and releases it in `whenComplete`.

```java
@BoundedConcurrency("myTaskSemaphore")   // Semaphore bean name
@Async("myTaskExecutor")
public CompletableFuture<String> doAsync() { ... }
```

Self-invocation (`this.method()`) bypasses AOP — move async methods to a separate bean.

## Inter-Service HTTP Client

`internalRestClient` (bean name) auto-propagates `Authorization` headers **only** to trusted internal hosts: `*.local`, `*.internal`, `*service*`, `localhost`, `10.*`. For async/scheduler calls without a request context, it falls back to `TokenProvider.generateS2SToken()`.

`TokenProvider` is an interface (`com.media.bus.common.security.TokenProvider`). The implementing bean must be provided by the consuming module (e.g., `auth` module's `JwtProvider`).

## JPA Base Entities

- `BaseEntity` — UUID PK (`@UuidGenerator(style = TIME)`), Hibernate-proxy-safe `equals`/`hashCode`
- `DateBaseEntity` — extends `BaseEntity`, adds `createdAt`/`updatedAt` audit fields
- All entities use `@MappedSuperclass`; QueryDSL Q-types are generated at compile time into `build/generated/`
