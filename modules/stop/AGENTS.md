# AGENTS.md — stop

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

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
  - `StopEntity.createFromRequest(request, registeredById)` — 수동 등록 팩토리. `registeredBySource = USER`
  - `StopEntity.createFromPublicApi(row)` — 공공 API 팩토리. `registeredBySource = SYSTEM`.
    좌표 검증은 호출 전에 `StopBulkPersistService`가 수행한다(불량 행은 예외 대신 건너뜀)
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

### 좌표 (`support/Coordinate.kt`)

`stop.stop`의 `x_crd` / `y_crd`는 **`NUMERIC(10, 7)`** (약 1.1cm 해상도)이며,
`(y_crd, x_crd)` 복합 인덱스 `idx_stop_coord`로 bbox 범위 조회를 지원한다.

- 문자열 ↔ `BigDecimal` 변환은 **반드시 `Coordinate`를 경유**한다 (범위 검증 + scale 7 정규화)
- 좌표 비교는 `Coordinate.isSame`을 쓴다. `BigDecimal.equals`는 scale까지 비교하므로
  (`37.0 != 37.0000000`) 그냥 쓰면 공공 API 재동기화마다 변경 없는 정류소를 변경으로 오탐지한다
- `parseXxx`는 실패 시 400 예외(수기 등록 경로), `parseOrNullXxx`는 null 반환(일괄 적재 경로)

### 지도 (`map/` 패키지)

지도 연동 전용 **읽기 전용 슬라이스**. 별도 모듈이 아니라 패키지로 격리했다
(배경 → `docs/feature/stop/Map-coordinate.md` 3.4장).

| 클래스 | 역할 |
|--------|------|
| `StopMapController` | `GET /api/v1/stop/map/bounds` |
| `StopBoundsCriteria` | 파라미터 검증 값 객체. `limit` 기본 1,000 / 최대 2,000 |
| `StopMapRepository` | 4개 컬럼만 projection. 잘림 판정용으로 `limit + 1`건 조회 |
| `StopMapQueryService` | 잘림 판정 → `truncated` 플래그 변환 |
| `StopMarkerResponse` | 마커 경량 DTO. 필드명은 `lat`(위도) / `lng`(경도) |

`StopService` / `StopRepository`와 섞지 말 것 — 커맨드 경로는 정합성이, 지도 경로는
응답 속도와 페이로드 크기가 우선이라 요구가 정반대다. 지도 응답에 단가는 붙이지 않는다.

### 응답 직렬화 주의

`xCrd` 같은 프로퍼티는 게터가 `getXCrd()`라서 Jackson 기본 네이밍이 **`xcrd`로 직렬화**한다.
`BusStopResponse`는 `@get:JsonProperty("xCrd")`로 이름을 고정했고
`BusStopResponseSerializationTest`가 회귀를 막는다. 새 DTO에 대문자 시작 약어를 쓸 때 같은 함정에 주의할 것.

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
