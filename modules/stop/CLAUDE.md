# CLAUDE.md — stop

`modules/stop`은 버스 정류소 데이터를 관리하는 Spring Boot 마이크로서비스 (port **8182**).
서울 열린데이터광장 공공 API 연동을 통한 일괄 등록과 수동 CRUD를 지원한다.

Base package: `com.media.bus.stop`

## Build

```bash
./gradlew :modules:stop:bootJar
./gradlew :modules:stop:bootRun
./gradlew :modules:stop:test
./gradlew :modules:stop:test --tests "com.media.bus.stop.ClassName.methodName"
```

## Dependencies

- `modules/common` — 공통 인프라 (예외 처리, 응답 래핑, RestClient, Bulkhead)
- `modules/auth-contract` — JWT 토큰 검증 (`JwtProvider`), `MemberPrincipal`, `MemberType`

## Database

- **Schema:** `stop`
- **ORM:** Exposed DAO 패턴
- **Migration:** Liquibase (`src/main/resources/db/changelog/`)

## 주요 클래스

### Entity & Enum (Exposed DAO)

- **`StopEntity`** / **`StopTable`** — 정류소 엔티티. 정적 팩토리 메서드로 생성
  - `StopEntity.requestOf(request, registeredById)` — 수동 등록 팩토리. `registeredBySource = USER`
  - `StopEntity.fromPublicApi(row)` — 공공 API 팩토리. `registeredBySource = SYSTEM`
  - `stop.applyUpdate(row, changeSource)` — 필드 변경 감지 후 `StopUpdateHistoryEntity` 반환. 변경 없으면 null
- **`StopUpdateHistoryEntity`** / **`StopUpdateHistoryTable`** — 정류소 변경 이력
- **`StopType`** — 정류소 유형 enum. `BaseEnum` 구현. companion object에 `byDisplayName` 캐시로 O(1) 역방향 조회
- **`ChangeSource`** — 변경 주체. `USER` | `SYSTEM`

| StopType Enum | displayName |
|------|-------------|
| `VILLAGE_BUS` | 마을버스 |
| `ROADSIDE_ALL_DAY` | 가로변전일 |
| `ROADSIDE_TIMED` | 가로변시간 |
| `CENTER_LANE` | 중앙차로 |
| `GENERAL_LANE` | 일반차로 |
| `HANGANG_DOCK` | 한강선착장 |

### Client

- **`SeoulBusApiClient`** — 서울 열린데이터광장 `busStopLocationXyInfo` API 클라이언트. 생성자 주입으로 `apiKey`, `baseUrl` 설정
  - Base URL: `http://openapi.seoul.go.kr:8088`
  - Page size: 1000
  - API key: `${seoul.api.key}` (환경변수 `SEOUL_API_KEY`)
  - `fetchTotalCount()` → 전체 건수 조회
  - `fetchStops(start, end)` → 범위 조회 (1-based)

### 검색

- **`StopSearchCriteria`** — sealed interface로 검색 조건을 타입 안전하게 표현. `when` 완전성 검사 활용

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
