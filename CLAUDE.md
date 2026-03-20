# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Multi-module Spring Boot 3.4.13 MSA project (root project name: `bus`) using Java 25. Six modules share a single Gradle root with common infrastructure in `modules/common` and auth contracts in `modules/auth-contract`.

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

| Module          | Port | Debug Port | Purpose                                            |
|-----------------|------|------------|----------------------------------------------------|
| `gateway`       | 8080 | 18080      | Spring Cloud Gateway (WebFlux) — JWT validation, routing |
| `auth`          | 8181 | 18181      | JWT issuance, refresh, S2S tokens                  |
| `auth-contract` | —    | —          | Shared JWT/auth contracts (JwtProvider, MemberPrincipal, MemberType) |
| `stop`          | 8182 | 18182      | Bus stop CRUD, 서울 공공 API 연동                   |
| `reservation`   | 8183 | 18183      | Core booking business logic                        |
| `common`        | —    | —          | Shared library — 상세는 `modules/common/CLAUDE.md` 참조 |

Infrastructure: PostgreSQL 18.3 on port **15433**, Valkey 8.1.6 (Redis fork) on port **6379**.

## Architecture

### Gateway Routing
```yaml
/api/v1/auth/**, /api/v1/member/**  → auth-service:8181
/api/v1/stop/**                      → stop-service:8182
/api/v1/reservation/**               → reservation-service:8183
```

### JWT Tokens
Three token types managed in `auth-contract` module's `JwtProvider.java` (implements `common`'s `TokenProvider` interface):
- **Access**: 60 min, stateless (signature-only verification)
- **Refresh**: 7 days, Redis-backed (supports revocation)
- **S2S**: 1 hour, system-to-system calls (`type: "s2s"` claim)

`MemberPrincipal` record carries: `id` (UUID), `loginId`, `email`, `memberType`, `emailVerified`.

`MemberType` enum (implements `BaseEnum`): `MEMBER`, `BUSINESS`, `ADMIN_USER`, `ADMIN_MASTER`, `ADMIN_DEVELOPER`.

### Data Layer
- PostgreSQL with **schema-per-module** isolation (`?schema=auth`, `?schema=stop`, `?schema=member`, `?schema=reservation`)
- **Liquibase** for migrations (changelogs in `src/main/resources/db/changelog/`)
- **QueryDSL 5.1.0** for type-safe queries; `JPAQueryFactory` bean in `QueryDslConfig.java`
- HikariCP pool size: 20 per module

### Cross-Cutting Concerns
`modules/common`이 제공하는 공통 인프라 — 응답 래핑, 예외 처리, 동시성 제어(Bulkhead), 로깅, 보안 필터 체인 등의 구현 상세는 `modules/common/CLAUDE.md` 참조.

핵심 제약:
- Virtual Threads 활성화 상태 — `ThreadLocal` 대신 `ScopedValue` (Java 25) 사용
- 모든 코드성 Enum은 `BaseEnum` 인터페이스 구현 (필드: `name`, `displayName`)

## Key Files

- `modules/auth-contract/src/main/java/com/media/bus/contract/security/JwtProvider.java` — JWT implementation
- `modules/auth-contract/src/main/java/com/media/bus/contract/entity/member/MemberPrincipal.java` — authenticated user principal
- `modules/auth-contract/src/main/java/com/media/bus/contract/entity/member/MemberType.java` — member type enum
- `modules/common/src/main/java/com/media/bus/common/security/TokenProvider.java` — token provider interface
- `modules/common/src/main/java/com/media/bus/common/autoconfigure/CommonSecurityAutoConfiguration.java` — 보안 자동 구성
- `docker-compose.yml` — 운영 service wiring, env vars, JDWP debug ports
- `docker-compose-local.yml` — 로컬 개발용 service wiring
