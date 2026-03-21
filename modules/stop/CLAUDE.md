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


## Database

- **Schema:** `stop`
- **Table:** `stop.stop`
- **Migration:** Liquibase (`src/main/resources/db/changelog/`)

## Key Classes

### Entity & Enum

- **`Stop`** (`entity/Stop.java`) — 정류소 엔티티. `DateBaseEntity` 상속. `@SuperBuilder` 사용.
  - `Stop.requestOf(SimpleStopCreateRequest, UUID registeredById)` — 수동 등록 팩토리. `registeredBySource = USER`
  - `Stop.fromPublicApi(SeoulBusStopRow)` — 공공 API 팩토리. `registeredById = null`, `registeredBySource = SYSTEM`
  - `stop.applyUpdate(SeoulBusStopRow, ChangeSource)` — 필드 변경 감지 후 `StopUpdateHistory` 반환. 변경 없으면 `null`
  - `@OneToMany updateHistories` — `StopUpdateHistory`와 양방향 관계 (LAZY)
- **`StopUpdateHistory`** (`entity/StopUpdateHistory.java`) — 정류소 변경 이력. `@ManyToOne(LAZY) Stop stop`으로 관계 설정. `stop_id` 컬럼을 FK로 활용 (`referencedColumnName = "stop_id"`).
- **`StopType`** (`entity/enums/StopType.java`) — 정류소 유형 enum. `BaseEnum` 구현.

| Enum | displayName |
|------|-------------|
| `VILLAGE_BUS` | 마을버스 |
| `ROADSIDE_ALL_DAY` | 가로변전일 |
| `ROADSIDE_TIMED` | 가로변시간 |
| `CENTER_LANE` | 중앙차로 |
| `GENERAL_LANE` | 일반차로 |
| `HANGANG_DOCK` | 한강선착장 |

- **`ChangeSource`** (`entity/enums/ChangeSource.java`) — 변경 주체. `USER` | `SYSTEM`


### Client

- **`SeoulBusApiClient`** (`client/`) — 서울 열린데이터광장 `busStopLocationXyInfo` API 클라이언트
  - Base URL: `http://openapi.seoul.go.kr:8088`
  - Page size: 1000
  - API key: `${seoul.api.key}` (환경변수 `SEOUL_API_KEY`)
  - `fetchTotalCount()` → 전체 건수 조회
  - `fetchStops(start, end)` → 범위 조회 (1-based)

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
