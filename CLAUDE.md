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
./gradlew :modules:<module>:test --tests "com.common.ClassName.methodName"  # Single test
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
DOCKER_BUILDKIT=1 docker compose build --parallel
```

Docker (local dev — `docker-compose-local.yml`):
```bash
docker-compose -f docker-compose-local.yml up --build -d
docker-compose -f docker-compose-local.yml up -d --build --no-deps <service-name>
docker-compose -f docker-compose-local.yml down
DOCKER_BUILDKIT=1 docker compose -f docker-compose-local.yml build --parallel
```

## Module Map

| Module | Port | Debug Port | Purpose |
|--------|------|-----------|---------|
| `gateway` | 8080 | 18080 | Spring Cloud Gateway — JWT validation, routing |
| `auth` | 8181 | 18181 | JWT issuance, refresh, S2S tokens |
| `reservation` | 8183 | 18183 | Core booking business logic |
| `common` | — | — | Shared library (no executable) |

Infrastructure: PostgreSQL 18.3 on port **15433**, Valkey (Redis fork) on port **6379**.

## Architecture

### Inter-Service Communication
`RestClient` (Spring Boot 3.2+ native) configured in `common/RestClientConfig.java`. **Servlet 환경에서만 활성화** (`@ConditionalOnWebApplication(type = SERVLET)`). The `internalRestClient` bean auto-propagates the `Authorization` header to internal services only. Domain whitelist: `*.local`, `*.internal`, `*service*`, `localhost`, `10.*`. For async/scheduler contexts without a user, a S2S token is generated automatically via `JwtProvider.generateS2SToken()`.

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
`ResponseAdvisor` (ResponseBodyAdvice) wraps responses matching configured path patterns. 실제 래핑 로직은 `ResponseBodyWrapper` 전략 인터페이스 구현체에 위임한다 (OCP). 새로운 응답 타입이 필요하면 `ResponseBodyWrapper`를 구현하고 `@Bean`으로 등록하면 된다.

내장 Wrapper 처리 순서 (`@Order` 기준):
1. `PassthroughBodyWrapper` — 이미 `AbstractView`이거나 `String`이면 그대로 통과
2. `NullBodyWrapper` — `null` → `NoDataView`
3. `PageResultBodyWrapper` — `PageResult` → `PageView`
4. `DefaultObjectBodyWrapper` — 일반 객체 → `DataView`

모든 View 클래스(`DataView`, `PageView`, `NoDataView`, `ErrorView`)는 `AbstractView`를 상속하며 `@SuperBuilder`를 사용한다. `ResponseAdvisor`와 `CommonWebMvcAutoConfiguration`은 **Servlet 환경에서만** 활성화된다 (`@ConditionalOnWebApplication(type = SERVLET)`). Gateway(WebFlux)에서는 View 객체가 Jackson에 의해 직접 JSON 직렬화된다.

예외 처리는 `ExceptionAdvisor`에 중앙 집중되며 `buildErrorResponse` 팩토리 메서드로 일관된 `ErrorView`를 반환한다. HTTP 상태 코드: `NoAuthenticationException` → 401, `NoAuthorizationException` → 403, `StorageException` → 404, `BaseException` → 500. S3 등 스토리지 라이브러리의 예외(`NoSuchKeyException` 등)는 `StorageException`으로 래핑하여 throw한다.

## Key Files

- `modules/common/src/main/java/com/hig/configuration/RestClientConfig.java` — inter-service HTTP client (Servlet-only)
- `modules/common/src/main/java/com/hig/core/aop/TransactionalBulkheadAspect.java` — bulkhead concurrency guard
- `modules/common/src/main/java/com/hig/security/JwtProvider.java` — all token operations
- `modules/common/src/main/java/com/hig/mvc/response/AbstractView.java` — 모든 View의 공통 추상 부모 (`@SuperBuilder`)
- `modules/common/src/main/java/com/hig/mvc/wrapper/ResponseBodyWrapper.java` — 응답 래핑 전략 인터페이스 (확장 포인트)
- `modules/common/src/main/java/com/hig/mvc/advisor/ResponseAdvisor.java` — response wrapping (ResponseBodyWrapper에 위임)
- `modules/common/src/main/java/com/hig/mvc/advisor/ExceptionAdvisor.java` — exception handling
- `modules/common/src/main/java/com/hig/autoconfigure/CommonWebMvcAutoConfiguration.java` — MVC 빈 자동 구성 (Servlet-only)
- `docker-compose.yml` — 운영 service wiring, env vars, JDWP debug ports
- `docker-compose-local.yml` — 로컬 개발용 service wiring
