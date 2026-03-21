# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Response Rules

- 모든 설명, 주석, 커밋 메시지는 한국어로 작성한다.
- 코드 생성 시 중요 로직에는 반드시 한국어 주석을 포함한다.
- 구현 배경, 설계 의도, 잠재적 사이드 이펙트를 코드와 함께 설명한다.
- AWS 환경(Aurora, Secrets Manager)과 CI/CD(GitLab, Docker) 파이프라인을 고려한 아키텍처 관점의 조언을 병행한다.

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

## Code Conventions (MUST FOLLOW)

- Virtual Threads 활성화 상태 — `ThreadLocal` 사용 금지, 반드시 `ScopedValue` (Java 25) 사용
- 모든 코드성 Enum은 `BaseEnum` 인터페이스 구현 (필드: `name`, `displayName`)
- 새로운 Enum 추가 시 `BaseEnum` 미구현은 빌드 실패 원인이 됨
- Entity 설계 시 `@Builder` 단독 사용 금지, BaseEntity, DateBaseEntity를 적절하게 선택해서 사용해야 한다. — 정적 팩토리 메서드 또는 도메인 메서드를 통한 생성 강제
- Service 레이어에서 다른 모듈의 Repository 직접 참조 금지 — 반드시 해당 모듈의 Service 또는 API 경유
- API 응답은 반드시 `common` 모듈의 공통 `ApiResponse<T>` 래퍼 사용
- 예외는 `common`의 글로벌 핸들러로 위임 — 개별 Controller에서 try-catch 금지
- DB 마이그레이션은 반드시 Liquibase changelog로 관리 — 수동 DDL 실행 금지

### 패키지 구조 정책

- 인터페이스는 패키지 상위, 구현체는 `impl/` 하위에 위치한다
  ```
  guard/
    StopCommandGuard.java       ← 인터페이스
    impl/
      StopCommandGuardImpl.java ← 구현체
  ```
- Service는 인터페이스에 의존한다 — `Guard`/`Validator` 계층은 반드시 인터페이스 경유
- Repository는 Spring Data JPA 인터페이스(`JpaRepository`)가 이미 인터페이스이므로 별도 Port 추가 불필요 — `XxxRepository`를 직접 주입한다

### 테스트 정책

- **Java 25 + Mockito**: 각 모듈 `build.gradle`에 `test { jvmArgs '-Dnet.bytebuddy.experimental=true' }` 필수
- **서비스 레이어 단위 테스트**: Repository는 Mockito Mock, Guard/Validator는 익명 구현체 Stub을 우선 사용한다
  - `XxxRepository` (인터페이스) → `@Mock` (인터페이스 목킹이므로 ByteBuddy 불필요)
  - Guard/Validator → 익명 서브클래스 Stub
  - Mockito는 JwtProvider 등 외부 의존성에도 사용
- **Guard/Validator 단위 테스트**: JwtProvider, Repository를 Mockito Mock으로 테스트하는 것은 허용

## Key Files

- `modules/auth-contract/src/main/java/com/media/bus/contract/security/JwtProvider.java` — JWT implementation
- `modules/auth-contract/src/main/java/com/media/bus/contract/entity/member/MemberPrincipal.java` — authenticated user principal
- `modules/auth-contract/src/main/java/com/media/bus/contract/entity/member/MemberType.java` — member type enum
- `modules/common/src/main/java/com/media/bus/common/security/TokenProvider.java` — token provider interface
- `modules/common/src/main/java/com/media/bus/common/autoconfigure/CommonSecurityAutoConfiguration.java` — 보안 자동 구성
- `docker-compose.yml` — 운영 service wiring, env vars, JDWP debug ports
- `docker-compose-local.yml` — 로컬 개발용 service wiring