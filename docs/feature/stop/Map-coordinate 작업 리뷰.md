# Map-coordinate 작업 리뷰

- 리뷰 대상: 워킹 트리 변경 전체 (`git diff HEAD`, `main...HEAD`는 비어 있음) 41개 파일
- 범위: 좌표 `VARCHAR → NUMERIC(10,7)` 마이그레이션, 신규 `map/` 조회 슬라이스(bbox 쿼리), stop → iam 방향 `stopsType` 필터 연결, JSON 필드명 수정
- 빌드/테스트: `:modules:stop`, `:modules:iam` 컴파일 및 테스트 통과

## 이슈 목록

### 1. [medium] bbox 정렬이 total order가 아니라 마커가 깜빡임
- **위치**: `modules/stop/src/main/kotlin/com/media/bus/stop/map/repository/StopMapRepository.kt:55`
- **내용**: `orderBy(yCrd ASC, xCrd ASC)`는 전순서(total order)가 아니라서, 좌표가 동일한 정류소가 있을 때 LIMIT 절단 결과가 요청마다 달라진다.
- **실패 시나리오**: 동일 좌표(127.0, 37.5)를 가진 정류소가 limit 경계에 걸리면 truncated=true 상황에서 Postgres가 어떤 행을 남길지 비결정적이다. 지도를 팬/줌할 때마다 다른 마커 부분집합이 반환되어, 주석이 방지한다고 명시한 "마커 깜빡임"이 그대로 발생한다.
- **권장 조치**: `StopTable.id`를 마지막 정렬 키로 추가.

### 2. [medium] @JsonAlias가 한 방향만 커버해 배포 순서 역전 시 500
- **위치**: `modules/iam/src/main/kotlin/com/media/bus/iam/client/stop/dto/StopInfo.kt:28`
- **내용**: `@JsonAlias("xcrd")`는 신규 iam ↔ 구 stop 방향만 보호하며, stop이 먼저 배포되면 구버전 iam이 역직렬화에 실패한다.
- **실패 시나리오**: stop 모듈이 iam보다 먼저 배포되면 구버전 iam이 `{"xCrd": ...}`를 수신한다. `xcrd` 필드가 없고 `StopInfo.xCrd`가 non-null String이므로 Jackson이 `MissingKotlinParameterException`을 던져, 롤아웃 구간 내내 관리자 정류소 목록/일괄 조회가 500을 반환한다.
- **권장 조치**: 배포 순서를 강제하거나, stop이 한 릴리스 동안 두 필드명을 모두 내보내도록 한다.

### 3. [medium] stop_update_history 변환에 numeric precondition 누락
- **위치**: `modules/stop/src/main/resources/db/changelog/V004_stop_coordinate_numeric.xml:66`
- **내용**: `V004-3`은 `stop_update_history` 좌표를 `NUMERIC(10,7)`로 변환하면서, `V004-1`이 `stop.stop`에 적용한 숫자 형식 precondition에 해당하는 가드가 없다.
- **실패 시나리오**: `stop_update_history`에 'N/A' 같은 레거시 값이 한 행이라도 있으면 `NULLIF(TRIM(COALESCE(...)),'')`로 걸러지지 않아 `V004-3`이 실패한다. Liquibase는 changeSet 단위로 커밋하므로 `V004-2`는 이미 커밋된 상태로 남아, history 테이블만 VARCHAR인 채 `StopUpdateHistoryTable`은 decimal로 매핑된다. 이후 공공 API 동기화가 히스토리를 쓸 때마다 런타임 실패로 이어진다.

### 4. [low] precondition 정규식이 좌표 크기 초과를 걸러내지 못함
- **위치**: `modules/stop/src/main/resources/db/changelog/V004_stop_coordinate_numeric.xml:31`
- **내용**: HALT precondition 정규식 `^-?[0-9]+(\.[0-9]+)?$`는 숫자 여부만 검사하고 크기는 검사하지 않는다.
- **실패 시나리오**: `NUMERIC(10,7)`은 정수부 3자리만 허용하므로 `1270.5` 같은 오염 값이 가드를 통과한 뒤 `V004-2`에서 `numeric field overflow`로 중단된다. precondition이 막으려던 실패가 그대로 재현된다.
- **권장 조치**: `ABS(x_crd::numeric) > 180 OR ABS(y_crd::numeric) > 90` 같은 크기 검사를 추가.

### 5. [low/medium] 지도 bounds를 clamp하지 않고 400으로 거부
- **위치**: `modules/stop/src/main/kotlin/com/media/bus/stop/map/dto/StopBoundsCriteria.kt:64-74`
- **내용**: `map.getBounds()` 값을 그대로 전달할 때 ±180을 넘는 경도나 날짜변경선을 감싸는 뷰포트가 clamp 없이 400으로 거부된다.
- **실패 시나리오**: 사용자가 카카오맵을 충분히 축소하면 `swLng`/`neLng`가 ±180을 벗어나 `Coordinate.parseLongitude`에서 400이 나거나, 뷰포트가 날짜변경선을 넘어 `swLng > neLng` 검증에 걸린다. 정상적인 축소 동작이 마커 없이 하드 에러가 된다.
- **권장 조치**: 순서 검증 전에 경도를 ±180, 위도를 ±90으로 clamp.

### 6. [low/medium] xcrd → xCrd 응답 필드명 변경이 하위호환 없이 破壊적
- **위치**: `modules/iam/src/main/kotlin/com/media/bus/iam/admin/dto/StopResponse.kt:20`, `modules/stop/src/main/kotlin/com/media/bus/stop/dto/response/BusStopResponse.kt:37`
- **내용**: 직렬화 필드명을 `xcrd`/`ycrd` → `xCrd`/`yCrd`로 바꾸면서 기존 이름을 병행 노출하지 않아 응답 계약이 깨진다.
- **실패 시나리오**: `GET /api/v1/stop` 및 관리자 정류소 엔드포인트에서 `xcrd`를 읽던 기존 프론트엔드가 에러 없이 `undefined`를 받는다. 마커 좌표가 조용히 빈 값/NaN이 되어 원인 추적이 어렵다.
- **권장 조치**: 한 릴리스 동안 두 이름을 모두 내보낸다.

### 7. [low] 벌크 등록 skip 가드가 좌표만 커버해 전체 실패 가능
- **위치**: `modules/stop/src/main/kotlin/com/media/bus/stop/service/StopBulkPersistService.kt:62`
- **내용**: 새로 추가된 행 단위 skip 가드는 좌표만 다루고, `StopEntity.createFromPublicApi`/`applyUpdate`가 호출하는 `StopType.fromDisplayName`은 인식 못 한 `STOPS_TYPE`에 대해 `error()`로 `IllegalStateException`을 던진다.
- **실패 시나리오**: 서울 열린데이터광장이 새 정류소 타입을 추가하면, `persistPage`가 `REQUIRES_NEW`이고 `StopRegisterService.registerAllStops`에 try-catch가 없어 전체 벌크 등록이 중단된다. 가드가 없애려던 바로 그 실패 모드다.
- **권장 조치**: 미지의 타입도 `failedCount` 경로로 처리.

### 8. [low] Schema example이 실제 직렬화 값과 불일치
- **위치**: `modules/stop/src/main/kotlin/com/media/bus/stop/dto/response/BusStopResponse.kt:48`
- **내용**: `@get:Schema`의 `example = "가로변시간"`은 한글 displayName을 문서화하지만, 실제로는 `stop.stopsType.name`(`ROADSIDE_TIMED`)이 직렬화되고 `stopsType` 필터(`StopType.parseFilter`)도 name만 허용한다.
- **실패 시나리오**: 문서의 example을 그대로 복사해 `?stopsType=가로변시간`으로 호출하면 400이 반환된다.

### 9. [low] 식별자 경로에서 stopsType 검증 불일치
- **위치**: `modules/stop/src/main/kotlin/com/media/bus/stop/controller/StopController.kt:44-52`
- **내용**: `pk`/`stopId`/`stopName`이 있는 경로에서는 `stopsType`이 무시될 뿐 아니라 검증조차 되지 않아, 같은 엔드포인트에서 같은 파라미터의 검증 동작이 달라진다.
- **실패 시나리오**: `?pk=<uuid>&stopsType=GARBAGE`는 200을 반환하지만 `?stopsType=GARBAGE`는 400을 반환한다. 클라이언트가 오타를 감지하지 못한 채 필터가 적용된 것으로 오해할 수 있다.

## 검증 완료 (조치 불필요)

- `lat`/`lng` ↔ `yCrd`/`xCrd` 매핑
- `truncated` 계산(`limit + 1` 조회 후 `take(limit)`)
- `Coordinate.isSame`이 `compareTo`로 `BigDecimal.equals`의 scale 민감도를 해결
- scale-7 `HALF_UP` 정규화가 Postgres `::NUMERIC(10,7)` 반올림과 일치 — 마이그레이션 직후 첫 재동기화에서 허위 변경 미발생
- `listPredicate`가 `countList`/`findListPaged`에 공유됨
- `StopBulkRegisterResult` 위치 인자 순서
- `modules/reservation`의 `StopInfo`는 좌표를 갖지 않아 필드명 변경 영향 없음
