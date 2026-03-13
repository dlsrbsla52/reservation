# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Multi-module Spring Boot 3.4.x MSA project (root project name: `hig`) using Java 25. Five modules share a single Gradle root with common infrastructure in `modules/common`.

## Build & Run Commands

```bash
# From project root
./gradlew clean build                              # Full build (all modules)
./gradlew :modules:<module>:bootJar               # Build single module JAR
./gradlew :modules:<module>:bootRun               # Run a module locally
./gradlew test                                     # All tests
./gradlew :modules:<module>:test                  # Single module tests
./gradlew :modules:<module>:test --tests "com.hig.ClassName.methodName"  # Single test
```

Java 25 requires Byte Buddy workaround for tests:
```bash
-Dnet.bytebuddy.experimental=true
```

Docker (all services):
```bash
docker-compose up --build -d
docker-compose up -d --build --no-deps <service-name>   # e.g., gateway-service
docker-compose down
```

## Module Map

| Module | Port | Debug Port | Purpose |
|--------|------|-----------|---------|
| `gateway` | 8080 | 18080 | Spring Cloud Gateway — JWT validation, routing |
| `auth` | 8181 | 18181 | JWT issuance, refresh, S2S tokens |
| `member` | 8182 | 18182 | User registration and profile |
| `reservation` | 8183 | 18183 | Core booking business logic |
| `common` | — | — | Shared library (no executable) |

Infrastructure: PostgreSQL 18.3 on port **15433**, Valkey (Redis fork) on port **6379**.

## Architecture

### Inter-Service Communication
`RestClient` (Spring Boot 3.2+ native) configured in `common/RestClientConfig.java`. The `internalRestClient` bean auto-propagates the `Authorization` header to internal services only. Domain whitelist: `*.local`, `*.internal`, `*service*`, `localhost`, `10.*`. For async/scheduler contexts without a user, a S2S token is generated automatically via `JwtProvider.generateS2SToken()`.

### JWT Tokens
Three token types managed in `common/security/JwtProvider.java`:
- **Access**: 60 min, stateless (signature-only verification)
- **Refresh**: 7 days, Redis-backed (supports revocation)
- **S2S**: 1 hour, system-to-system calls (`type: "s2s"` claim)

`MemberPrincipal` record carries: `id` (UUID), `loginId`, `email`, `memberType` (MEMBER/BUSINESS), `emailVerified`.

### Concurrency — Bulkhead Pattern
Virtual Threads are enabled (`spring.threads.virtual.enabled=true`). To prevent HikariCP connection pool exhaustion (pool size: 20), `TransactionalBulkheadAspect.java` wraps all `@Transactional` and `@Repository` methods with a Resilience4j semaphore bulkhead (maxConcurrentCalls: 19, maxWait: 200ms). Uses `ScopedValue<Boolean>` (Java 25) for re-entrance detection — do NOT use `ThreadLocal` in this project.

### Data Layer
- PostgreSQL with **schema-per-module** isolation (`?schema=auth`, `?schema=member`, `?schema=reservation`)
- **Liquibase** for migrations (changelogs in `src/main/resources/db/changelog/`)
- **QueryDSL 5.1.0** for type-safe queries; `JPAQueryFactory` bean in `QueryDslConfig.java`
- HikariCP pool size: 20 per module

### Response Wrapping
`ResponseAdvisor` (ResponseBodyAdvice) wraps responses matching `/api/**` and `/health-check` in `DataView<T>`. Exception handling is centralized in `ExceptionAdvisor` (@ControllerAdvice). Response types: `DataView`, `PageView`, `NoDataView`, `ErrorView`.

## Key Files

- `modules/common/src/main/java/com/hig/configuration/RestClientConfig.java` — inter-service HTTP client
- `modules/common/src/main/java/com/hig/core/aop/TransactionalBulkheadAspect.java` — bulkhead concurrency guard
- `modules/common/src/main/java/com/hig/security/JwtProvider.java` — all token operations
- `modules/common/src/main/java/com/hig/mvc/advisor/ResponseAdvisor.java` — response wrapping
- `modules/common/src/main/java/com/hig/mvc/advisor/ExceptionAdvisor.java` — exception handling
- `docker-compose.yml` — service wiring, env vars, JDWP debug ports
