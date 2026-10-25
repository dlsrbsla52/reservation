# 카카오맵 좌표 연동

DB에 저장된 정류소 좌표(`stop.stop.x_crd` / `y_crd`)를 프론트엔드 지도에 마커로 표시하기 위한
제약 조건·비용 분석 및 구현 계획.

- **지도 SDK**: 카카오맵 Web(JavaScript) SDK 채택
- **데이터 출처**: 서울 열린데이터광장 `getBusStopInfo` → `stop.stop` 테이블 (WGS84)
- **대상 규모**: 서울시 버스 정류소 약 11,000건

---

## 결론 요약

| 항목 | 결론 |
|------|------|
| 비용 | **사실상 0원.** 지도 로드 월 300만 건까지 무료이고, 마커 개수는 과금 대상이 아님 |
| 지오코딩 비용 | **0원.** 좌표를 이미 자체 DB에 보유하고 있어 카카오 Local API 호출 불필요 |
| 실제 블로커 | 카카오 제약이 아니라 **현재 API 스펙 쪽 3가지 문제** (아래 3장) |
| 최우선 작업 | ① 좌표 필드 직렬화명 정리 ② 좌표 컬럼 타입 변경 ③ bbox 조회 API 신설 |

---

## 1. 카카오맵 API 제약 조건

### 1.1 키 발급 및 앱 구성

| 항목 | 내용 |
|------|------|
| 키 종류 | **JavaScript 키** (REST API 키 아님) |
| 도메인 등록 | 앱 관리 > 플랫폼 > Web에 **사용 도메인 전체 등록 필수**. 미등록 도메인에서는 SDK 로드 실패 |
| 등록 대상 | `http://localhost:5173`(로컬), 스테이징 도메인, 운영 도메인을 **각각** 등록 |
| 활성화 | 앱 관리 > [카카오맵] > [사용 설정] **ON** |

> **주의 — 앱을 나누지 말 것**
> 무료 쿼터는 **개발자 계정당 첫 번째로 활성화한 앱 1개**에만 제공된다.
> dev/staging/prod용으로 앱을 3개 만들면 2번째 앱부터는 비즈월렛 연결 + 유료 API 사용 설정이 강제된다.
> **앱은 1개만 만들고 도메인만 여러 개 등록하는 구성**으로 간다.

### 1.2 SDK 사용상 제약

| 항목 | 내용 |
|------|------|
| 좌표계 | WGS84. DB 데이터가 이미 WGS84이므로 **변환 불필요** |
| 좌표 순서 | `new kakao.maps.LatLng(위도, 경도)` = **`(yCrd, xCrd)` 순서**. 뒤집으면 마커가 전부 엉뚱한 곳에 찍힘 |
| 로딩 방식 | SPA에서는 `autoload=false` + `kakao.maps.load(callback)` 패턴 필수 |
| 마커 성능 | **수천 개 마커 직접 렌더링 시 브라우저가 멈춤.** `&libraries=clusterer` (MarkerClusterer) 필수 |
| 쿼터 초과 | `429 Too Many Requests` 반환. 사용량은 앱 관리 > [통계] > [쿼터]에서 확인 |

### 1.3 약관

카카오 Local API(주소↔좌표 변환) 결과의 DB 영구 저장은 약관상 제한된다.
다만 본 프로젝트는 **서울 열린데이터광장 데이터를 자체 DB에 적재**하는 구조이므로 해당 없음.
향후 카카오 지오코딩 결과를 캐싱할 일이 생기면 그때 약관을 재확인할 것.

---

## 2. 비용

### 2.1 무료 쿼터 및 단가

| API | 무료 (일간) | 초과 단가 |
|-----|------------|----------|
| **지도 Web(JavaScript) SDK** | 300,000건 | **0.1원/건** |
| 주소→좌표 / 좌표→주소 / 좌표계 변환 | 각 100,000건 | 0.5원/건 |
| 키워드 / 카테고리 장소 검색 | 각 100,000건 | 2원/건 |
| 대중교통 / 도보 / 자전거 경로 조회 | 각 **1,000건** | **10원/건** |
| 정적 지도 조회 | 1,000건 | — |

**월간 전체 API 합산 3,000,000건** 한도가 별도로 존재하며, 실질적으로는 이쪽이 먼저 막힌다
(일 평균 10만 건 수준). 카카오는 일간 쿼터와 달리 **월간 쿼터를 전 API가 공유**한다는 점에 유의.

### 2.2 과금 단위

**쿼터는 "지도 로드" 단위로 소모되며, 마커 개수와 무관하다.**
정류소 11,000개를 렌더링해도 지도 로드 1건이다. 줌·팬·마커 조작은 추가 과금되지 않는다.

### 2.3 예상 비용

| 월 지도 로드 | 비용 |
|------------|------|
| 9만 (DAU 1,000 × 3회 × 30일) | **0원** |
| 300만 | **0원** (무료 한도 정확히 소진) |
| 500만 | 200만 초과 × 0.1원 = 20만원 |
| 1,000만 | 700만 × 0.1원 = 70만원 |

*모두 부가세 별도.*

현재 예상 트래픽 기준으로는 **무료 구간에 안전하게 들어간다.**

### 2.4 비용이 튀는 유일한 시나리오: 경로 조회

정류소 간 경로 표시(대중교통/도보 경로 조회 API)를 도입하면 비용 구조가 완전히 달라진다.
무료가 **일 1,000건**뿐이고 초과 단가가 **10원/건**이다.

> 예) 일 100,000건 호출 시 → 99,000건 × 10원 = **일 99만원**

로드맵에 경로 기능이 들어가면 **반드시 별도 설계·비용 재산정**이 필요하다.
(참고: 2026-02-02 ~ 12-31 한시적으로 건당 10원(80% 할인) 적용 중이며, 이후 정상가 50원 전환 예정.
요금 변경 시 최소 1개월 전 고지된다고 공지되어 있음.)

---

## 3. 현재 코드에서 확인된 블로커

### 3.1 좌표 필드 직렬화명 불일치 (우선순위: 최상)

**증상** — 생성된 OpenAPI 스펙(`openapi-stop.yaml`)의 `BusStopResponse`에
좌표 필드가 **두 벌**로 나온다.

```yaml
xCrd:
  type: string
  description: 경도 WGS84 (XCRD)
  writeOnly: true      # ← 응답에서 제외됨
yCrd:
  type: string
  description: 위도 WGS84 (YCRD)
  writeOnly: true      # ← 응답에서 제외됨
...
xcrd:                  # ← 설명·예시 없는 중복 필드
  type: string
ycrd:
  type: string
```

**원인** — `BusStopResponse.kt:23-27`

```kotlin
@param:Schema(description = "경도 WGS84 (XCRD)", example = "127.0627647339")
val xCrd: String,
```

1. `@param:` 어노테이션 사이트 타깃 때문에 springdoc이 **생성자 파라미터에서만** 스키마를 인식 →
   `xCrd`가 `writeOnly: true`로 문서화됨
2. Jackson 기본 네이밍은 게터명 앞쪽 연속 대문자를 소문자로 바꾼다.
   `xCrd` → 게터 `getXCrd()` → **직렬화명 `xcrd`**
   (프로젝트에 커스텀 `PropertyNamingStrategy` 설정 없음 — 기본 동작 확정)
3. 결과적으로 **문서상 필드(`xCrd`)와 실제 응답 필드(`xcrd`)가 다르고**, 문서상 필드는 심지어
   응답에서 제외된 것으로 표기됨 → OpenAPI 기반 프론트 클라이언트를 생성하면 좌표를 못 받는다

**조치**

- [x] 실제 응답 JSON을 한 번 찍어 직렬화명이 `xcrd`인지 확인 (원인 3번 검증)
      → **확인 완료.** 실제 직렬화 결과는 `{"...","xcrd":"127.0627647","ycrd":"37.6472686"}`
- [x] 지도용 신규 DTO(3.3)에서 필드명을 `lat` / `lng`로 정의해 문제를 원천 회피
- [x] 기존 `BusStopResponse`는 `@param:Schema` → `@get:Schema`로 변경하고
      `@get:JsonProperty("xCrd")`를 명시해 직렬화명 고정
- [x] `iam` 모듈의 `StopInfo` / `StopResponse`도 같은 패턴(`xCrd`)이므로 **S2S 역직렬화 영향 확인 필요**

> **확인 결과 — iam의 정류소 S2S 조회는 수정 전까지 런타임에서 깨져 있었다.**
> stop이 `xcrd`를 보내는데 `StopInfo`의 생성자 파라미터명은 `xCrd`(non-null)라서
> `KotlinInvalidNullException: missing (therefore NULL) value for creator parameter xCrd`가
> 발생했다. 즉 `StopServiceClient`의 정류소 조회 계열은 전부 실패하고 있었다.
>
> 조치: `StopInfo`에 `@JsonAlias("xcrd")` / `@JsonAlias("ycrd")`를 추가했다.
> **롤링 배포 중 구버전 stop 인스턴스가 `xcrd`를 계속 보내기 때문**이며,
> 구버전 인스턴스가 모두 교체된 뒤에는 별칭을 제거해도 된다.
> `StopResponse`(어드민 응답)도 `@get:JsonProperty`로 직렬화명을 고정했다.

### 3.2 좌표 컬럼이 `varchar` (우선순위: 상)

`StopTable.kt:17-18`

```kotlin
val xCrd = varchar("x_crd", 50)
val yCrd = varchar("y_crd", 50)
```

- 문자열이라 **bbox 범위 조회(`BETWEEN`)를 안정적으로 못 한다.** 문자열 비교는 표기 형식
  (자릿수, 앞자리 0, 지수 표기)에 따라 결과가 깨진다
- `x_crd` / `y_crd`에 **인덱스가 없어** 11,000행 풀스캔이 발생한다
- 프론트에서 매번 `parseFloat` 필요

**조치**

- [x] `numeric(10, 7)`으로 컬럼 타입 변경 + 데이터 마이그레이션 (`V004_stop_coordinate_numeric.xml`)
- [x] `(y_crd, x_crd)` 복합 인덱스 추가 — `idx_stop_coord` (PostGIS 도입 시 `GIST` 인덱스 검토)
- [x] 공공 API 원본은 문자열이므로 `StopEntity.createFromPublicApi`에서 파싱·검증 추가
      (파싱 실패 시 등록 거부 + WARN 로그 + `failedCount` 집계)

> **`double precision`이 아니라 `numeric(10, 7)`을 택한 이유**
> 좌표 자체는 근사값이라 double로도 충분하지만, `applyUpdate`의 **변경 감지**가 좌표를 비교한다.
> 부동소수점은 문자열 → double 파싱 시 미세한 오차가 생겨 값이 같은데도 변경으로 오탐지될 수 있고,
> 그러면 공공 API 재동기화마다 전체 정류소에 불필요한 UPDATE와 이력 행이 쌓인다.
>
> 다만 `BigDecimal`은 `equals`가 scale까지 비교하므로(`37.0 != 37.0000000`) 그대로 쓰면
> **같은 오탐지가 다른 경로로 재발한다.** 그래서 `Coordinate` 유틸에서 파싱 시 scale을 7로 정규화하고,
> 비교는 반드시 `Coordinate.isSame`(내부적으로 `compareTo`)을 쓰도록 강제했다.
>
> **마이그레이션 안전장치** — V004-1 changeSet이 사전에 숫자로 파싱 불가능한 행이 있는지 검사하고,
> 있으면 `HALT`로 배포를 중단시킨다. 캐스팅 도중 실패해 원인을 알기 어려운 상태가 되는 것을 막는다.

**이력 테이블도 함께 전환했다.** `stop_update_history`의 `old_x_crd` 등 4개 컬럼도 `numeric(10,7)`으로
바꿔 본 테이블과 타입을 일치시켰다(nullable이므로 비정상 값은 NULL로 흡수).

### 3.3 지도용 조회 API 부재 (우선순위: 상)

현재 `GET /api/v1/stop` (`StopController.kt:33-50`)은 `pk` / `stopId` / `stopName` / `keyword` +
페이지네이션(기본 `size=20`)만 지원한다. **bbox·반경 조회가 없다.**

정류소 11,000건을 20개씩 페이지네이션으로 긁어오는 방식은 지도에서 사용 불가하며,
전건을 한 번에 내려주는 것도 `BusStopResponse` 전체 필드(생성일시·수정일시·단가 등) 포함 시
페이로드가 과도하다.

**조치 — 신규 엔드포인트 (구현 완료)**

```
GET /api/v1/stop/map/bounds?swLat=&swLng=&neLat=&neLng=&limit=
```

- 지도 현재 영역(`map.getBounds()`)에 포함된 정류소만 반환
- 응답은 **지도 전용 경량 DTO**로 제한

```kotlin
data class StopMarkerResponse(
    val id: UUID,
    val stopId: String,
    val stopName: String,
    val lat: BigDecimal,   // yCrd
    val lng: BigDecimal,   // xCrd
)

data class StopBoundsResponse(
    val items: List<StopMarkerResponse>,
    val truncated: Boolean,
    val limit: Int,
)
```

- [x] 결과 건수 상한 설정 — 기본 1,000건 / 최대 2,000건, 초과 시 `truncated: true` 반환
- [ ] 줌 레벨이 낮을(넓을) 때는 서버 사이드 그리드 집계 반환 검토 (**미구현**)

> **`/stop/bounds`가 아니라 `/stop/map/bounds`로 둔 이유**
> 게이트웨이가 `/api/v1/stop` 하위 전체를 8182로 라우팅하므로 라우트 추가가 필요 없고,
> `map/` 한 단계를 넣어 두면 지도 전용 API 묶음(향후 그리드 집계·타일 등)이 URL에서도 드러난다.
>
> **`level` 파라미터는 의도적으로 받지 않는다.**
> 서버 사이드 그리드 집계가 아직 없어 동작에 아무 영향이 없는데, 스펙에 노출하면
> 프론트가 이미 지원되는 기능으로 오해한다. 집계를 도입할 때 함께 추가한다.
>
> **잘림 판정은 COUNT 쿼리 없이** `limit + 1`건을 읽어 수행한다. 같은 범위를 두 번 스캔하지 않는다.
> 상한에 걸려도 매 요청 동일한 부분집합이 나오도록 `(y_crd, x_crd)` 정렬을 고정했다
> (정렬이 없으면 팬/줌마다 다른 마커가 나타났다 사라져 깜빡임처럼 보인다).
>
> **단가는 붙이지 않는다.** 마커 표시에 불필요하고, 수천 건에 단가 결합을 추가하면
> 경량 DTO를 만든 이유가 사라진다. 마커 클릭 시 상세는 `GET /api/v1/stop?pk=...`로 별도 조회한다.

### 3.4 모듈 분리 여부 — `stop` 모듈 내 패키지 격리로 결정

별도 `map` 모듈을 만들지 않았다. 좌표 데이터의 소유권이 `stop.stop` 테이블에 있는데,
모듈 경계 규칙상 타 모듈 Repository 직접 참조가 금지되어 `map` 모듈은 bbox 조회를
`stop`에 S2S로 되물어야 한다. 지도는 `idle` 이벤트마다 호출되는 고빈도 API라
쿼리 한 방이면 끝날 일에 네트워크 왕복을 더하는 손해가 크고, 자기 소유 테이블이 없는
껍데기 모듈에 컨테이너·포트·게이트웨이 라우트·보안 설정이 전부 따라붙는다.

대신 `com.media.bus.stop.map` 하위에 **읽기 전용 슬라이스**로 격리해 이관 비용만 낮췄다.

```
com/media/bus/stop/
├── map/
│   ├── controller/StopMapController.kt      # GET /api/v1/stop/map/bounds
│   ├── dto/StopBoundsCriteria.kt            # 파라미터 검증 값 객체
│   ├── dto/StopMarkerResponse.kt
│   ├── dto/StopBoundsResponse.kt
│   ├── repository/StopMapRepository.kt      # 4개 컬럼만 projection (DAO wrap 안 함)
│   └── service/StopMapQueryService.kt
└── support/Coordinate.kt                     # 좌표 파싱·정규화·비교
```

**모듈 분리를 재검토할 트리거**

1. PostGIS 도입 (`geometry` 타입 + `GIST` 인덱스)
2. 경로 조회 기능 확정 — 외부 API 호출·쿼터 관리·서킷브레이커가 붙고, 장애 격리 근거가 생긴다 (2.4장)
3. 지도 데이터가 정류소를 넘어 노선/차고지/실시간 차량 위치까지 확장될 때
4. 줌 레벨별 그리드 집계를 Valkey에 사전 적재하는 배치성 워크로드가 생길 때

---

## 4. 프론트엔드 구현 방침

### 4.1 SDK 로드

```html
<script
  src="//dapi.kakao.com/v2/maps/sdk.js?appkey={JS_KEY}&autoload=false&libraries=clusterer">
</script>
```

```js
kakao.maps.load(() => {
  const map = new kakao.maps.Map(container, {
    center: new kakao.maps.LatLng(37.5665, 126.9780),
    level: 5,
  });
});
```

- `appkey`는 빌드 타임 환경변수로 주입 (`.env`), **소스에 하드코딩 금지**
- JavaScript 키는 도메인 제한으로 보호되므로 프론트 노출 자체는 허용되나, 도메인 등록을 반드시 좁게 유지

### 4.2 마커 렌더링

```js
// GET /api/v1/stop/map/bounds 응답의 items — 필드명이 lat/lng 라서 인자 순서와 1:1 대응된다
const markers = stops.map(s =>
  new kakao.maps.Marker({
    position: new kakao.maps.LatLng(s.lat, s.lng),  // (위도, 경도) 순서 주의
    title: s.stopName,
  })
);

new kakao.maps.MarkerClusterer({
  map, markers, averageCenter: true, minLevel: 6,
});
```

### 4.3 영역 조회 연동

- `kakao.maps.event.addListener(map, 'idle', handler)`에 **디바운스 300ms** 적용
- 이전 요청은 `AbortController`로 취소해 경합 방지
- 조회한 영역을 캐싱해 동일 영역 재요청 방지

---

## 5. 작업 체크리스트

### 백엔드

- [x] `BusStopResponse` 좌표 필드 직렬화명 고정 (`@get:Schema` + `@get:JsonProperty`)
- [x] `iam` 모듈 `StopInfo` / `StopResponse` S2S 역직렬화 영향 검증 (**깨져 있던 것 확인 후 수정**)
- [x] `stop.stop` 좌표 컬럼 `varchar` → `numeric(10,7)` 마이그레이션
- [x] `(y_crd, x_crd)` 인덱스 추가 (`idx_stop_coord`)
- [x] `StopMarkerResponse` DTO 신설
- [x] `GET /api/v1/stop/map/bounds` 구현 (건수 상한 + `truncated` 플래그 포함)
- [x] 공공 API 적재 시 좌표 파싱·유효성 검증 추가

**남은 백엔드 작업**

- [ ] 운영 DB에 V004 적용 전, 좌표 파싱 불가 행 사전 점검
      (`SELECT COUNT(*) FROM stop.stop WHERE TRIM(x_crd) !~ '^-?[0-9]+(\.[0-9]+)?$'`)
- [ ] 롤링 배포 완료 후 `StopInfo`의 `@JsonAlias("xcrd")` / `@JsonAlias("ycrd")` 제거
- [ ] 넓은 줌 레벨용 서버 사이드 그리드 집계 (필요성 확인 후)

### 인프라 / 계정

- [ ] 카카오 개발자 앱 **1개** 생성 후 [카카오맵] 사용 설정 ON
- [ ] JavaScript 키 발급 및 도메인 등록 (local / staging / prod 전부)
- [ ] JS 키를 환경변수로 주입하도록 배포 파이프라인 구성
- [ ] [통계] > [쿼터] 모니터링 및 임계치 알림 설정 (월 300만 건 대비)

### 프론트엔드

- [ ] SDK 로드 (`autoload=false`, `libraries=clusterer`)
- [ ] MarkerClusterer 적용
- [ ] `idle` 이벤트 디바운스 + `AbortController` 처리
- [ ] `429` 응답 시 사용자 안내 및 재시도 억제 처리

---

## 부록: 네이버 지도 대비 검토 결과

카카오로 결정했으나, 재검토 트리거를 명확히 하기 위해 비교 결과를 남긴다.

| 항목 | 카카오맵 | 네이버 Maps (NCP) |
|------|---------|------------------|
| 지도 로드 단가 | 0.1원 | 0.1원 (동일) |
| 지도 무료량 | 월 300만 (+ 일 30만 상한) | **월 1,000만** |
| 무료 쿼터 구조 | 전 API **합산** 월 300만 | API별 **독립** |
| 지오코딩 | 0.5원 / 일 10만 (합산 차감) | 0.5원 / 월 300만 독립 |
| 경로 조회 | 10원 / 일 1,000건 | 5원 / 월 6만 건 |
| 마커 클러스터링 | **공식 제공** (`libraries=clusterer`) | 예제 저장소 코드 직접 포함 필요 |
| 셋업 | 무료 구간은 결제수단 등록 불필요 | **결제수단(카드) 등록 필수** |

**카카오 선택 근거** — 현재 트래픽 전망에서 양쪽 모두 0원이므로 비용 차이가 없고,
클러스터링 공식 지원과 셋업 간편함에서 앞선다.

**재검토 트리거**

1. **월 지도 로드 300만 건 초과**가 예상될 때 → 네이버가 명확히 유리 (월 1,000만까지 무료)
2. **경로 조회 기능 도입**이 확정될 때 → 카카오 일 1,000건 무료는 실사용에 부족
3. 장소 검색 API를 상시 사용하게 될 때 → 카카오는 합산 월 쿼터를 함께 소모

---

## 참고

- [쿼터 | Kakao Developers](https://developers.kakao.com/docs/ko/getting-started/quota)
- [카카오맵 API 시작하기 | Kakao Developers](https://developers.kakao.com/docs/ko/kakaomap/common)
- [Kakao 지도 Web API 문서](https://apis.map.kakao.com/web/)
- [카카오맵 API 신규 기능 및 무료 쿼터 운영 방식 변경 안내](https://devtalk.kakao.com/t/api-notice-on-new-kakao-map-api-features-and-free-quota-policy/150222)
- [Maps API 요금 변경 사전 안내 | NAVER Cloud Platform](https://www.ncloud-forums.com/topic/99/)
