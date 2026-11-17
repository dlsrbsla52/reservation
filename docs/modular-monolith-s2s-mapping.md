# 모듈러 모놀리스 S2S 대응표

- 상위 문서: [모듈러 모놀리스 회귀 계획](modular-monolith-migration-plan.md) 0단계 "S2S 대응표", 1단계 3·4
- 작성일: 2026-11-17, 기준 브랜치 `bigbang/modular-monolith`
- 용도: 포트 메서드마다 **어느 제공자 진입점을 부르고, 무엇을 변환하는지**의 기준. 3단계에서 이 표대로 `modules/app/.../adapter`를 구현했다

## 포트

소비자 모듈에 포트를 두고 `app`의 어댑터가 구현한다. 소비자는 포트 타입만 주입받는다. 예전 HTTP 클라이언트(`*ServiceClient`, `@HttpExchange` `*Api`)와 internal 컨트롤러는 지웠다.

| 포트 | 소비자 | 구현(`com.media.bus.app.adapter`) | 소비 클래스 |
|---|---|---|---|
| `com.media.bus.reservation.reservation.port.StopLookupPort` | reservation | `ReservationStopLookupAdapter` | `StopResolutionService` |
| `com.media.bus.reservation.contract.port.MemberLookupPort` | reservation | `ReservationMemberLookupAdapter` | `MemberVerificationService` |
| `com.media.bus.iam.port.StopAdminPort` | iam | `IamStopAdminAdapter` | `AdminStopPriceFacade` |
| `com.media.bus.iam.port.ReservationAdminPort` | iam | `IamReservationAdminAdapter` | `AdminReservationFacade`, `AdminBusinessContractFacade` |

## 대응표 (클라이언트 메서드 26개)

"검증 위치"는 예전에 제공자 컨트롤러에서만 하던 검증이 지금 어디서 실행되는지다. "어댑터 할 일"은 3단계에서 어댑터가 직접 해야 하는 변환이다.

### reservation → stop (3)

| 포트 메서드 | 제공자 매핑 | 제공자 진입점 | 검증 위치 | 어댑터 할 일 |
|---|---|---|---|---|
| `getStopByPk(Set<UUID>)` | `GET /api/v1/internal/stop?pk=` | `StopService.getBusStop(StopSearchCriteria.of(pk, null, null))` | `StopSearchCriteria.of` (stop 공개 함수) | `BusStopResponse` → reservation `StopInfo` |
| `getStopByStopId(String)` | `GET /api/v1/internal/stop?stopId=` | `StopService.getBusStop(StopSearchCriteria.of(null, stopId, null))` | 같음 | 같음 |
| `getStopsByPks(Collection<UUID>)` | `POST /api/v1/internal/stop/bulk` | `StopService.getBusStopsByIds` | **1단계 이관**: 빈 목록·200개 초과 → 400/`00260` | 빈 입력은 지금처럼 호출 없이 빈 리스트(이관한 검증이 빈 목록을 거절하므로 필수) |

### reservation → iam (1)

| 포트 메서드 | 제공자 매핑 | 제공자 진입점 | 검증 위치 | 어댑터 할 일 |
|---|---|---|---|---|
| `findMemberById(UUID)` | `POST /api/v1/internal/iam/member/id` | `MemberService.findByMemberId(memberId.toString())` | `@NotBlank`는 UUID 타입이라 불필요 | `MemberResponse` → reservation `MemberInfo`. 회원 없음 4xx는 `BusinessException` 그대로 전파 |

### iam → stop (12)

| 포트 메서드 | 제공자 매핑 | 제공자 진입점 | 검증 위치 | 어댑터 할 일 |
|---|---|---|---|---|
| `getStopByPk(UUID)` | `GET /api/v1/internal/stop?pk=` | `StopService.getBusStop(StopSearchCriteria.of(setOf(pk), null, null))` | `StopSearchCriteria.of` | `BusStopResponse` → iam `StopInfo`. **`unitPrice` 유지 계약 테스트**(기본값 `null`이라 빠뜨려도 컴파일됨) |
| `getStopByStopId(String)` | `GET /api/v1/internal/stop?stopId=` | 같음(`stopId`) | 같음 | 같음 |
| `getStopByStopName(String)` | `GET /api/v1/internal/stop?stopName=` | 같음(`stopName`) | 같음 | 같음 |
| `getStopsByPks(Collection<UUID>)` | `POST /api/v1/internal/stop/bulk` | `StopService.getBusStopsByIds` | **1단계 이관** | 빈 입력은 호출 없이 빈 리스트 |
| `getStopPage(page, size, keyword, stopsType)` | `GET /api/v1/internal/stop/page` | `StopService.getBusStopPage` | 기존부터 서비스(`StopType.parseFilter` → 400). 컨트롤러 보정 없음 | `PageResult` 필드 변환 |
| `getStopPrice(UUID)` | `GET /api/v1/internal/stop/price/{stopId}` | `StopPriceService.getPrice` | 없음 | `null` 그대로 |
| `createStopPrice(...)` | `POST /api/v1/internal/stop/price` | `StopPriceService.createPrice` | **1단계 이관**: 0 이하 단가 → 400/`00260` | `StopPriceResponse` → `StopPriceInfo` |
| `updateStopPrice(...)` | `PUT /api/v1/internal/stop/price/{stopId}` | `StopPriceService.updatePrice` | **1단계 이관** | 같음 |
| `deleteStopPrice(...)` | `DELETE /api/v1/internal/stop/price/{stopId}` | `StopPriceService.deletePrice` | 없음 | — |
| `getStopsInBounds(...)` | `GET /api/v1/internal/stop/map/bounds` | `StopMapQueryService.findMarkersInBounds(StopBoundsCriteria.of(...))` | `StopBoundsCriteria.of` (stop 공개 함수) | stop `StopMarkerResponse.stopsType`(enum) → iam `String` |
| `createSimpleStop(request, registeredById)` | `POST /api/v1/internal/stop/simple` | `StopService.createOneStop(registeredById, request)` | **1단계 이관**: DTO 제약 직접 검증(이전 파라미터 `@Valid`는 `@Validated`가 없어 실행되지 않았음) | iam DTO → stop `SimpleStopCreateRequest`. `stopsType` 문자열 → `StopType.fromName`, `null`이면 400/`00260` |
| `registerBulk()` | `POST /api/v1/internal/stop/register/bulk` | `StopRegisterService.registerAllFromPublicApi` | 없음 | **5xx 변환(V4-01)**: 제공자 `ServiceException`을 `cause`로 보존하고 `CommonResult.ERROR`(500/`00100`)로 던짐. 409(실행 중)는 그대로 |

### iam → reservation (10)

| 포트 메서드 | 제공자 매핑 | 제공자 진입점 | 검증 위치 | 어댑터 할 일 |
|---|---|---|---|---|
| `searchContracts(...)` | `GET /api/v1/internal/reservation/contract` | `InternalContractFacade.searchContracts` | **1단계 이관**: 검색어 100자, 시작일 범위 역전, 페이지 보정 | `status` 문자열 → `ContractStatus.fromName`, `null`이면 400/`00260`(예전 `MethodArgumentTypeMismatch`와 같은 코드) |
| `createContract(request)` | `POST /api/v1/internal/reservation/contract` | `InternalContractFacade.createContract` | **1단계 이관**: DTO 제약 | iam DTO → reservation `CreateContractRequest`. 회원 재검증·정류소 결합은 Facade가 함 |
| `getContractDetail(UUID)` | `GET /api/v1/internal/reservation/contract/{contractId}` | `InternalContractFacade.getContractDetail` | 없음 | DTO 변환 |
| `getContractsByMember(...)` | `GET /api/v1/internal/reservation/contract/member/{memberId}` | `InternalContractFacade.getContractsByMemberId` | **1단계 이관**: 페이지 보정 | DTO 변환 |
| `searchAdminReservations(...)` | `GET /api/v1/internal/reservation/reservations` | `ReservationFacade.searchAdminReservations` | **1단계 이관**: 페이지 보정 | `status` → `ReservationStatus.fromName`(`null`이면 400), `createdFrom/To` ISO 문자열 → `OffsetDateTime`(실패 시 400/`00260`) |
| `getReservationDetailForAdmin(UUID)` | `GET .../reservations/{reservationId}` | `ReservationFacade.getReservationDetailForAdmin` | 없음 | DTO 변환 |
| `assignReservation(...)` | `PUT .../reservations/{id}/assignee` | `ReservationFacade.assignReservation` | UUID 타입이라 불필요 | 예전 `code != "00000"` 검사는 불필요(실패는 예외로 옴) |
| `updateReservationStatus(...)` | `PUT .../reservations/{id}/status` | `ReservationFacade.updateReservationStatus` | **1단계 이관**: 상담 기록 1000자 | `status` → `ReservationStatus.fromName`, `null`이면 400/`00260`. 선점 캐시 해제는 Facade가 함 |
| `extendHold(...)` | `PUT .../reservations/{id}/hold` | `ReservationFacade.extendHold` | 타입이라 불필요 | — |
| `completeReservationToContract(...)` | `POST .../reservations/{id}/complete-to-contract` | `ReservationFacade.completeReservationToContract` | **1단계 이관**: DTO 제약 | iam DTO → reservation `CompleteToContractRequest`. 정류소 재검증·선점 캐시 해제는 Facade가 함 |

## 호출자 없는 제공자 매핑 (4) — 폐기함

`POST /api/v1/internal/iam/member/{jwt,login-id,email}`, `GET /api/v1/internal/iam/commission/effective-rate`. 운영 환경이 없어 저장소 밖 호출자도 없으므로 internal 컨트롤러와 함께 지웠다(서비스 메서드는 남아 있다).

합계: 고유 매핑 21개(stop 10, reservation 10, iam 1) + 호출자 없음 4개 = 제공자 매핑 25개.

## 모든 어댑터 공통 (구현: `callProvider`, `parseCode`)

- **과부하**: 제공자의 `BulkheadFullException`은 `BusinessException(result = CommonResult.BULKHEAD_FULL, cause = e, httpStatus = HttpStatus.TOO_MANY_REQUESTS)`로 바꾼다. `httpStatus`를 빼면 400이 된다(V3-01).
- **4xx**: 제공자의 `BusinessException`과 `StorageException`(404)은 그대로 전파한다. 예전 S2S 경로와 같은 상태·코드·메시지가 나간다. 판단 기준은 예외 타입이 아니라 `ExceptionAdvisor`가 매기는 HTTP 상태다 — `StorageException`은 `BusinessException`이 아니지만 404다(검증 후 수정, 아래 참고).
- **5xx**: 그 밖의 `BaseException`(`ServiceException` 등)은 `ServiceException(ERROR)`(500/`00100`)로 바꾸고 원인은 `cause`로 남긴다.
- **입력 검증 실패 응답**: 진입점 검증은 400/`00260`/기본 메시지로 던진다. 예전 S2S 경로도 제공자 필드 오류 목록(`errors`)을 버리고 같은 응답을 냈으므로 외부 계약이 같다.
- 어댑터에 `@Transactional`을 붙이지 않는다. Exposed Entity·Table·Repository를 받지 않는다.

## 1단계 검증 결과

- Liquibase: 로컬 DB 이력은 `public.databasechangelog` 한 테이블이고 `filename`은 `db/changelog/<파일명>.xml`. 파일을 `db/changelog/{iam,stop,reservation}/`로 옮기고 `logicalFilePath`를 이 값으로 고정했다. 로컬 DB 복제본에 세 서비스를 띄워 모두 `Run: 0`, 이력 64행이 그대로인 것을 확인했다. **운영 DB의 `filename`은 0단계 쿼리로 따로 확인해야 한다.**
- `./gradlew clean build` 통과, 세 서비스 bootJar로 실제 컨텍스트 기동 확인.

## 전환 결과 (2·3·4·6단계, 2026-11-17)

**구조**
- `modules/app` 실행 모듈 추가(`BusApplication`, 통합 `application*.yml`, 루트 Liquibase master). `gateway` 모듈과 서비스별 `*Application`·`SecurityConfig`·yml 삭제.
- 보안: 단일 `SecurityFilterChain` + `JwtAuthenticationFilter`(신뢰 헤더 6개 제거, 라우트 허용 목록 404, 공개 경로 정확 일치, 401/403). `MemberPrincipalExtractFilter`와 컨트롤러의 `@RequestHeader("X-User-Id")` 19곳 제거 → `@CurrentMember`. `MdcLoggingFilter`는 헤더를 읽지 않고, 인증 필터가 검증한 회원 ID만 MDC에 넣는다.
- S2S 인프라 삭제: `S2STokenFilter`, `S2SRestClientFactory`, `TokenProvider`, `JwtProvider.generateS2SToken`, `RestClientConfig`, internal 컨트롤러 5개.
- 설정: `allow-bean-definition-overriding: false`, Hikari 1개 풀 20(local 10), Bulkhead 19(local 9), Actuator `127.0.0.1:8081`, 기본 프로필 Swagger 끔.
- 배포: Dockerfile 실행 모듈 고정(`app.jar`), compose 1개 앱 + `mem_limit 1g`/`-Xmx640m` + healthcheck. `docker-compose-dev.yml` 삭제.
- OpenAPI: `app`에서 통합 생성. 스키마 이름 충돌 3쌍(지도 2, 상담 이력 1)에 고유 `@Schema(name)`.

**검증**
- `./gradlew clean build` 통과. 새 `app` 테스트 40개(필터·경로 정책·아키텍처·어댑터).
- `app.jar`를 로컬 DB 복제본에 기동: Liquibase `Run: 0 / Previously run: 64`, 빈 덮어쓰기 금지 상태로 기동 성공.
- HTTP 확인: 8081 health 200, 8080 `/actuator`·`/swagger-ui.html`·`/api-docs`·`/error`·`/api/v1/internal/**` 404, `//`·`..;` 400(방화벽). 토큰 없이 `X-User-*`만 → 401. 본인 토큰 + 위조 `X-User-Id` → 본인 정보, 로그 `[user:]`도 토큰 회원.
- 업무 흐름(복제 DB): 예약 생성·없는 정류소 거절(`00140`)·목록 정류소명 결합, 관리자 정류소 목록·단가 등록(0원 400, `unitPrice` 유지)·지도 마커, 예약 검색(잘못된 상태 400, size 500→100 보정)·상태 변경(1001자 400)·상담 이력, 계약 생성(잘못된 납부 주기 400, 없는 회원 404/`00256`, 정상 생성).
- `generateAllOpenApiDocs` 실제 실행: 경로 64개, internal·actuator 경로 0개, 두 지도 API가 서로 다른 스키마 참조.
- Docker 이미지 빌드 성공(`/app/app.jar`, healthcheck용 `wget` 존재).

**남은 것**
- 실제 메모리 측정(RSS, `jcmd`)과 리버스 프록시(TLS, `X-Forwarded-For` 신뢰 범위, S8)는 배포 환경이 생길 때.

## 검증 후 수정 (2026-11-17)

빈 DB·기존 DB 이력 비교, 일회용 PostgreSQL·Valkey로 띄운 `app.jar` HTTP 검증(어댑터 4개 업무 흐름, 가짜 서울 API로 일괄 등록 실패 재현)에서 찾은 것을 고쳤다.

| 항목 | 문제 | 수정 |
|---|---|---|
| 오류 계약 회귀 | `callProvider`가 `BusinessException`이 아닌 `BaseException`을 모두 500으로 바꿔, 관리자 계약 생성·예약→계약 완료에서 없는 정류소(`StorageException`)가 예전 404/`00140` 대신 500/`00100`이 됨 | `StorageException` 그대로 전파 + 어댑터 테스트 |
| 열린 기본 보안 체인 | `common`의 `CommonSecurityAutoConfiguration`이 `SecurityFilterChain`이 없으면 permitAll 체인을 등록. `app` 체인이 빠지면 인증 없이 기동 | 삭제. `BusApplicationContextTest`가 체인 1개·인증 필터 포함을 확인 |
| 전체 기동 미검증 | 빈 충돌·포트 구현 누락이 실제 기동에서만 드러남 | `BusApplicationContextTest`(H2, Redis 불필요) — `./gradlew build`에 포함 |
| 관리 포트 0 | `management.server.port=0`(임의 포트)이면 인증 필터가 관리 포트를 0으로 알아 Actuator 요청을 막음 | 필터가 요청 시점에 `local.management.port`를 확인 |
| 죽은 공개 경로 | 공개 목록의 `health-check` 4개는 핸들러가 없었음. reservation 것은 `GET /api/v1/reservation/{reservationId}`에 매칭 | 공개 목록에서 제거. 헬스체크는 관리 포트 `/actuator/health` |
| 오래된 잔재 | 포트·서비스 KDoc의 "S2S·internal·gateway", iam `client.*.dto` 패키지, internal 전용 DTO(`FindMemberBy*Request` 4개, `EffectiveCommissionRateResponse`, stop `BulkStopLookupRequest`·`Create/UpdateStopPriceRequest`), JSON 봉투 클래스 5개, `StopInfo.unitPrice` 기본값 | 주석 갱신, 포트 DTO를 `iam.port.dto.{stop,reservation}`로 이동, 죽은 DTO·테스트 삭제, 봉투 제거, 기본값 제거(필드 누락 시 컴파일 실패) |

전환과 무관하게 함께 고친 기존 버그:
- 없는 경로·지원하지 않는 메서드·Content-Type·필수 파라미터 누락이 catch-all로 500 → 404/`00280`·405/`00281`·415/`00282`·400/`00260`.
- 예약 요청 `stopId`의 `""`·`null` 원소가 NPE로 500 → 역직렬화 단계 400.
- `desiredContractStartDate`를 빼면 INSERT 제약 위반으로 항목 500 → 원래 설계(V001 NOT NULL)대로 필수 입력, 빠지면 400.
