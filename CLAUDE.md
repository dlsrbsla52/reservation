# CLAUDE.md

## Response Rules

- 모든 설명, 주석, 커밋 메시지는 한국어로 작성한다.
- 코드 생성 시 중요 로직에는 반드시 한국어 주석을 포함한다.
- 구현 배경, 설계 의도, 잠재적 사이드 이펙트를 코드와 함께 설명한다.
- AWS 환경(Aurora, Secrets Manager)과 CI/CD(GitLab, Docker) 파이프라인을 고려한 아키텍처 관점의 조언을 병행한다.

## Overview

Spring Boot 4.0.5 MSA (root: `bus`), Kotlin 2.3.20, 6개 Gradle 모듈. ORM: Exposed 1.0.0. 공통 인프라: `modules/common`, 인증 계약: `modules/auth-contract`.

## Build & Run

```bash
./gradlew clean build                                         # 전체 빌드
./gradlew :modules:<module>:bootJar                           # 단일 모듈 JAR
./gradlew :modules:<module>:test                              # 단일 모듈 테스트
./gradlew :modules:<module>:test --tests "패키지.클래스.메서드"

# Docker (운영)
docker-compose up --build -d
docker-compose up -d --build --no-deps <service-name>
DOCKER_BUILDKIT=1 docker compose build --parallel

# Docker (로컬 개발)
docker-compose -f docker-compose-local.yml up --build -d
docker-compose -f docker-compose-local.yml up -d --build --no-deps <service-name>
DOCKER_BUILDKIT=1 docker compose -f docker-compose-local.yml build --parallel
```

## Module Map

| Module          | Port  | Purpose                                                     |
|-----------------|-------|-------------------------------------------------------------|
| `gateway`       | 8080  | Spring Cloud Gateway (WebFlux) — `modules/gateway/CLAUDE.md` |
| `iam`           | 8181  | JWT 발급·갱신·회원 관리 — `modules/iam/CLAUDE.md`           |
| `auth-contract` | —     | JWT/인증 계약 공유 라이브러리 — `modules/auth-contract/CLAUDE.md` |
| `stop`          | 8182  | 버스 정류소 CRUD — `modules/stop/CLAUDE.md`                 |
| `reservation`   | 8183  | 예약 핵심 비즈니스 — `modules/reservation/CLAUDE.md`        |
| `common`        | —     | 공통 인프라 라이브러리 — `modules/common/CLAUDE.md`         |

인프라: PostgreSQL 18.3 (port **15433**), Valkey 8.1.6 / Redis (port **6379**)

라우팅: `/api/v1/auth/**`, `/api/v1/admin/**` → 8181 / `/api/v1/stop/**` → 8182 / `/api/v1/reservation/**` → 8183

## Code Conventions (MUST FOLLOW)

- **Kotlin** — 순수 Kotlin 프로젝트. `@JvmStatic`, `@JvmField`, `java.util.Optional` 사용 금지. nullable 타입(`T?`) 사용
- **Virtual Threads 활성화** — `ThreadLocal` 금지, `ScopedValue` 사용. `synchronized` 대신 `ReentrantLock`
- **Enum** — 모든 코드성 Enum은 `BaseEnum` 구현 (`displayName` 필드). companion object에 `fromName()` 정의
- **Entity** — Exposed DAO 패턴. Table object + Entity class 분리. 정적 팩토리 메서드(`companion object`)로 생성 강제
- **모듈 경계** — 타 모듈 Repository 직접 참조 금지. 해당 모듈 Service 또는 API 경유
- **응답** — `common`의 `ApiResponse<T>` 래퍼 필수
- **예외** — Controller 내 try-catch 금지. `common` 글로벌 핸들러(`ExceptionAdvisor`)에 위임. 예외 클래스는 Kotlin 기본 인수 주생성자 사용
- **KDoc** — 모든 KDoc은 마크다운 스타일로 작성
- **DB 마이그레이션** — Liquibase changelog만 사용, 수동 DDL 금지
- **테스트** — Repository: `@Mock`, Guard/Validator: 익명 Stub. MockK 사용 권장
- **스키마 격리** — DB는 모듈별 schema (`?schema=auth|stop|member|reservation`)
