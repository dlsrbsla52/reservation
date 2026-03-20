# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`modules/stop`은 버스 정류소 데이터를 관리하는 Spring Boot 마이크로서비스 (port 8182). 서울 열린데이터광장 공공 API 연동을 통한 일괄 등록과 수동 CRUD를 지원한다.

Base package: `com.media.bus.stop`

## Build Commands

```bash
./gradlew :modules:stop:bootJar                    # Build JAR
./gradlew :modules:stop:bootRun                    # Run locally
./gradlew :modules:stop:test                       # Run tests
./gradlew :modules:stop:test --tests "com.media.bus.stop.ClassName.methodName"
```

## Dependencies

- `modules/common` — 공통 인프라 (DateBaseEntity, 응답 래핑, 예외 처리, RestClient, Bulkhead)
- `modules/auth-contract` — JWT 토큰 검증 (`JwtProvider`), `MemberPrincipal`, `MemberType`

## API Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/v1/stop/{stopId}` | — | 정류소 단건 조회 |
| `POST` | `/api/v1/stop/simple` | Admin | 수동 정류소 단건 등록 |
| `POST` | `/api/v1/stop/register/bulk` | Admin | 서울 공공 API 전체 일괄 등록 |
| `GET` | `/api/v1/stop/health-check` | — | 헬스 체크 |

## Database

- **Schema:** `stop`
- **Table:** `stop.stop`
- **Migration:** Liquibase (`src/main/resources/db/changelog/`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID (v7) | PK (BaseEntity) |
| `stop_id` | VARCHAR(50), UNIQUE | 정류소 번호 (공공 API `STOPS_NO`) |
| `stop_name` | VARCHAR(50) | 정류소명 |
| `x_crd` | VARCHAR(50) | 경도 (WGS84) |
| `y_crd` | VARCHAR(50) | 위도 (WGS84) |
| `node_id` | VARCHAR(50) | 노드 ID |
| `stops_type` | VARCHAR(50) | `StopType` enum (EnumType.STRING) |
| `created_at` | TIMESTAMPTZ | 생성일시 (DateBaseEntity) |
| `updated_at` | TIMESTAMPTZ | 수정일시 (DateBaseEntity) |

## Key Classes

### Entity & Enum

- **`Stop`** (`entity/Stop.java`) — 정류소 엔티티. `DateBaseEntity` 상속. `@SuperBuilder` 사용.
  - `Stop.requestOf(SimpleStopCreateRequest)` — 수동 등록용 팩토리
  - `Stop.fromPublicApi(SeoulBusStopRow)` — 공공 API 데이터 변환 팩토리 (`StopType.fromDisplayName()` 사용)
- **`StopType`** (`entity/enums/StopType.java`) — 정류소 유형 enum. `BaseEnum` 구현.

| Enum | displayName |
|------|-------------|
| `VILLAGE_BUS` | 마을버스 |
| `ROADSIDE_ALL_DAY` | 가로변전일 |
| `ROADSIDE_TIMED` | 가로변시간 |
| `CENTER_LANE` | 중앙차로 |
| `GENERAL_LANE` | 일반차로 |
| `HANGANG_DOCK` | 한강선착장 |

### Service

- **`StopService`** — 정류소 단건 CRUD (조회, 수동 등록)
- **`StopRegisterService`** — 서울 공공 API 일괄 등록. 페이지 단위(1000건)로 호출하며 중복 `stopId`는 건너뜀.

### Client

- **`SeoulBusApiClient`** (`client/`) — 서울 열린데이터광장 `busStopLocationXyInfo` API 클라이언트
  - Base URL: `http://openapi.seoul.go.kr:8088`
  - Page size: 1000
  - API key: `${seoul.api.key}` (환경변수 `SEOUL_API_KEY`)
  - `fetchTotalCount()` → 전체 건수 조회
  - `fetchStops(start, end)` → 범위 조회 (1-based)

### Validation

- **`StopModifyValiData`** (`valid/`) — 수정 권한 검증
  - `isMemberAuthenticationAdmin(token)` — ADMIN 권한 확인 (MEMBER/BUSINESS 거부)
  - `isStopRegistered(stopId)` — 정류소 존재 여부 확인

### DTOs

| Class | Package | Purpose |
|-------|---------|---------|
| `SimpleStopCreateRequest` | `dto.request` | 수동 등록 요청 (record, validated) |
| `BusStopResponse` | `dto.response` | 정류소 조회 응답 |
| `StopBulkRegisterResult` | `dto.response` | 일괄 등록 결과 (saved/skipped/total) |
| `SeoulBusStopApiResponse` | `dto.external` | 서울 API 응답 래퍼 |
| `SeoulBusStopRow` | `dto.external` | 서울 API 단건 행 DTO |

## Configuration

```yaml
# 주요 설정 (application.yml)
server.port: 8182
spring.datasource.url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/default?schema=stop
spring.threads.virtual.enabled: true

# 서울 공공 API
seoul.api.key: ${SEOUL_API_KEY}
seoul.api.base-url: http://openapi.seoul.go.kr:8088

# Bulkhead
hig.bulkhead.database-name: orderDatabase
resilience4j.bulkhead.instances.orderDatabase:
  maxConcurrentCalls: 19
  maxWaitDuration: 200ms
```

## Tests

- `SeoulBusApiClientTest` — 서울 공공 API 실제 연결 통합 테스트 (네트워크 필요)
