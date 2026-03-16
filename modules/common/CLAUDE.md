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
| `CommonLoggingAutoConfiguration` | Servlet env only | `MdcLoggingFilter` (requestId + userId → MDC) |
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

## Logging

### 로깅 스택
- **Log4j2** (Logback 대체) + **LMAX Disruptor 4.0** — `AsyncRoot`로 락프리 비동기 로깅. Virtual Thread 핀닝 방지.
- **Micrometer Tracing + OTel 브릿지** — `traceId`/`spanId`를 MDC에 자동 주입. 분산 추적 가능.
- **`MdcLoggingFilter`** (order=HIGHEST_PRECEDENCE+200, Security 이후 실행) — `requestId`(X-Request-ID 헤더 또는 UUID), `userId`(인증 사용자) 주입.

### MDC 필드 요약
| 필드 | 주입 주체 | 설명 |
|---|---|---|
| `traceId` | Micrometer Tracing | 분산 트레이스 ID (OTel) |
| `spanId` | Micrometer Tracing | 현재 Span ID |
| `requestId` | `MdcLoggingFilter` | HTTP 요청 단위 고유 ID |
| `userId` | `MdcLoggingFilter` | 인증된 사용자 Principal name |
| `service` | `log4j2-spring.xml` | `spring.application.name` 값 |

### 환경별 로그 포맷
- `local` 프로파일: 컬러 패턴 로그 (가독성)
- 그 외 (운영/스테이징): JSON 구조화 로그 → CloudWatch Logs Insights 쿼리 최적화
  ```json
  {"@timestamp":"...","level":"INFO","traceId":"...","spanId":"...","requestId":"...","userId":"...","service":"stop","message":"..."}
  ```

### Virtual Thread + MDC 주의사항
MDC는 ThreadLocal 기반이므로 새 스레드 생성 시 자동 전파되지 않는다.
`@Async` / `CompletableFuture.supplyAsync()` 등 사용 시 명시적으로 컨텍스트를 복사해야 한다:
```java
Map<String, String> mdcCopy = MDC.getCopyOfContextMap();
CompletableFuture.runAsync(() -> {
    MDC.setContextMap(mdcCopy);
    try { /* ... */ } finally { MDC.clear(); }
});
```

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

- `BaseEntity` — UUID PK (`@UuidV7`), Hibernate-proxy-safe `equals`/`hashCode`
- `DateBaseEntity` — extends `BaseEntity`, adds `createdAt`/`updatedAt` audit fields
- All entities use `@MappedSuperclass`; QueryDSL Q-types are generated at compile time into `build/generated/`

### UUID v7 (`@UuidV7` / `UuidV7Generator`)

`@UuidV7` is a custom Hibernate `@IdGeneratorType` annotation placed on a `UUID` PK field. At flush time, Hibernate calls `UuidV7Generator.generate()` which builds a time-ordered UUID v7:

```
msb = (currentTimeMillis << 16) | 0x7000 | random(12 bits)   // version 7
lsb = 0x8000_0000_0000_0000 | random(62 bits)                 // variant bits
```

Key properties:
- **Monotonically increasing** within the same millisecond (12 random bits prevent collisions)
- **B-tree friendly** — time prefix keeps new rows near the end of the index, reducing page splits
- **Virtual Thread safe** — uses `ThreadLocalRandom` (no contention across threads)
- Base package: `com.media.bus.common.entity.common`

Usage:
```java
@Id
@UuidV7
@Column(name = "id", updatable = false, nullable = false)
private UUID id;
```
