# 모듈러 모놀리스 회귀 계획 리뷰 v4

- 검토일: 2026-11-17
- 대상: [모듈러 모놀리스 회귀 계획](modular-monolith-migration-plan.md), 3차 리뷰 반영 후 **579행** 작업트리 버전
- 코드 기준: `bigbang/modular-monolith`, `HEAD 8f199f1`
- 계획서 식별: SHA-256 `0a0097ef4aeda4626013310647e64848882297a50b90dfd5270ae57109a1267d`
- 비교 자료: 이전 리뷰 1~3차, 실제 Kotlin 코드·Gradle·프로필·배포 설정, Spring·springdoc·swagger-core 공식 문서 및 소스
- 검증 범위: 정적 분석. 통합 앱 기동이나 운영 장애를 재현한 결과는 아니다.
- 판정: **전환 방향은 일관된다. 다만 제공자 5xx의 외부 오류 계약과 통합 OpenAPI의 스키마 이름 충돌을 추가로 다뤄야 한다.**

## 1. 종합 판단

이전 리뷰에서 지적한 보안 체인 통합, 제공자 Facade 보존, 입력 검증 이관, Bulkhead의 429 지정, 실행 JAR 설정, 프록시 신뢰 범위는 현재 계획에 반영되어 있다. 이미 해결 방향이 정해진 항목을 미반영 문제로 다시 집계하지 않는다.

이번에는 **기존 오류 계약을 유지한다는 목표와 실제 직접 호출 결과의 불일치 1건**, **여러 서비스의 API 명세를 합칠 때 생기는 충돌 누락 1건**을 확인했다. 별도로 단계별 완료 시점과 표현을 명확히 할 사항을 정리했다.

| ID | 우선순위 | 유형 | 발견 사항 | 반영 시점 |
|---|---|---|---|---|
| V4-01 | 중간 | 외부 오류 계약 불일치 | 제공자 `ServiceException`이 직접 전파되면 기존 500/`00100`이 500/`00130`으로 바뀌는 실제 경로가 있음 | 0단계 기준선·3단계 오류 변환 |
| V4-02 | 중간 | 통합 명세의 이름 충돌 | IAM과 stop의 공개 DTO 이름이 같고 필드 타입은 다른데, 하나의 OpenAPI로 합칠 때의 식별 규칙이 없음 | 2단계 OpenAPI 이관 |

행 번호는 모두 위 579행 계획서 기준이다. 아래 완료 조건은 향후 구현 시 검증할 항목이며, 이번 리뷰에서 통과한 테스트를 뜻하지 않는다.

## 2. V4-01 — 4xx·과부하만 다루면 제공자 5xx의 외부 오류 계약이 달라진다

**계획 근거:** 402행은 예외 이관을 제공자 4xx와 `BusinessException` 중심으로 설명하고, 403~404행은 과부하와 `Retry-After`를 보완한다. 402행의 대응은 응답 상태·본문 코드 회귀 검증이며, 413~414행은 기존 오류 계약 유지를 완료 조건으로 삼는다. 그러나 **HTTP 5xx를 거치면서 사라지던 원본 예외 타입·코드·메시지**의 처리 방침은 없다.

현재 코드에는 이 차이가 발생하는 구체적인 경로가 있다.

```text
POST /api/v1/admin/stop/register/bulk
  → AdminStopPriceFacade.registerBulk
  → IAM StopServiceClient.registerBulk
  → InternalStopController.registerBulk
  → StopRegisterService.registerAllFromPublicApi
  → SeoulBusApiClient
```

서울 API가 응답 본문의 실패 결과를 반환하거나 필수 응답 본문이 없으면 `SeoulBusApiClient`는 `ServiceException`을 던진다. 이 예외의 기본 결과는 `CommonResult.SERVICE_ERROR`, 코드 `00130`이다.

| 처리 지점 | 현재 MSA | 직접 호출 후 별도 대응이 없을 때 |
|---|---|---|
| stop 내부 | `ServiceException` 발생 | 동일 |
| 제공자 HTTP 경계 | stop의 `ExceptionAdvisor.baseExceptionHandler`가 500/`00130`과 예외 메시지로 응답 | 경계가 사라짐 |
| IAM으로 전달 | S2S 팩토리는 4xx만 사용자 정의 변환. 500은 RestClient 기본 `HttpServerErrorException`으로 전달 | 원본 `ServiceException`이 그대로 전달 |
| 외부 관리자 응답 | IAM의 `defaultHandler` → **500/`00100`**, 일반 오류 메시지 | 통합 앱의 `baseExceptionHandler` → **500/`00130`**, 서울 API 오류 등 원본 메시지 |

HTTP 상태는 둘 다 500이지만 **본문 코드와 메시지는 유지되지 않는다.** 원본 예외가 `BaseException` 계열인지에 따라 소비자의 분기까지 달라질 수 있으므로, “직접 전파하면 기존 계약도 유지된다”는 전제를 두면 안 된다.

**코드 근거:**

- [AdminStopPriceController](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/controller/AdminStopPriceController.kt#L153), [AdminStopPriceFacade](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/facade/AdminStopPriceFacade.kt), [IAM StopServiceClient](../modules/iam/src/main/kotlin/com/media/bus/iam/client/stop/StopServiceClient.kt#L128): 관리자 일괄 등록의 호출 경로.
- [SeoulBusApiClient](../modules/stop/src/main/kotlin/com/media/bus/stop/client/SeoulBusApiClient.kt#L39): 본문 누락·업무 오류 결과에서 `ServiceException` 발생.
- [ServiceException](../modules/common/src/main/kotlin/com/media/bus/common/exceptions/ServiceException.kt), [CommonResult](../modules/common/src/main/kotlin/com/media/bus/common/result/type/CommonResult.kt): 기본 코드 `00130`과 일반 오류 코드 `00100`.
- [S2SRestClientFactory](../modules/common/src/main/kotlin/com/media/bus/common/client/S2SRestClientFactory.kt#L57): 사용자 정의 상태 처리 대상은 `is4xxClientError`뿐.
- [ExceptionAdvisor](../modules/common/src/main/kotlin/com/media/bus/common/web/advisor/ExceptionAdvisor.kt#L138): `BaseException`과 그 밖의 예외가 서로 다른 본문으로 변환됨.

RestClient의 기본 5xx 처리 결과가 `HttpServerErrorException`이라는 점은 [Spring 공식 API 문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestClient.RequestHeadersSpec.html#retrieve())에서도 확인했다. 위 외부 응답 차이는 이 동작과 저장소의 호출·예외 처리 코드를 연결한 정적 분석 결과다.

**수정안:**

1. 0단계 기준선에 관리자 정류소 일괄 등록의 **서울 API 업무 오류·본문 누락** 사례를 추가한다. 외부 HTTP 상태뿐 아니라 `code`와 메시지를 기록한다.
2. 3단계에서 기존 HTTP 경계를 대체하는 포트 어댑터의 오류 변환 범위를 정한다. 기존 계약을 보존한다면 이 경로의 제공자 `ServiceException`은 원인을 로깅용으로 보존하면서 외부에서는 기존 일반 오류로 표현한다.
3. 모든 `BaseException`을 일괄 일반 오류로 바꾸지는 않는다. 이미 보존하기로 한 검증·404·409·429를 깨뜨린다. 기존에 제공자가 어떤 HTTP 상태로 응답했는지를 기준으로 구분한다.
4. 원본 `00130`과 메시지를 공개하기로 선택할 수도 있다. 이 경우에는 **의도한 API 계약 변경**으로 명시하고, “기존 상태·코드 유지”의 예외 및 프런트 영향 범위를 적는다.

**완료 조건:** 동일한 서울 API 실패를 구버전 HTTP 경로와 새 포트 경로에 주었을 때, 외부 응답이 선택한 정책과 일치한다. 기존 4xx·429와 예약 항목별 실패 코드도 함께 유지된다. 공통 핸들러를 바꾸는 경우 기존 IAM 자체 오류 응답까지 바뀌는지 확인한다.

## 3. V4-02 — 통합 OpenAPI에서 같은 이름의 공개 DTO가 하나의 스키마 키를 공유한다

**계획 근거:** 268~293행은 OpenAPI 생성 작업을 `app`으로 옮겨 명세 생성 성공을 확인한다. 모듈별 문서 그룹의 분리 여부는 428행, 즉 4단계의 선택 사항이다. 클래스 이름이 같은 DTO들의 OpenAPI 식별 규칙은 정하지 않았다.

현재 서로 다른 JVM에서는 각자 문제없이 사용할 수 있는 다음 타입들이 통합 앱에서는 함께 문서화된다.

| 공개 API | 응답 DTO 패키지 | DTO 이름 | 중첩 마커의 `stopsType` |
|---|---|---|---|
| `/api/v1/admin/stop/map/bounds` | `com.media.bus.iam.admin.dto` | `StopBoundsResponse`, `StopMarkerResponse` | `String` |
| `/api/v1/stop/map/bounds` | `com.media.bus.stop.map.dto` | `StopBoundsResponse`, `StopMarkerResponse` | `StopType` enum |

두 경로는 internal API 삭제 후에도 남는 공개 API다. 따라서 internal 컨트롤러를 없애는 것으로 이 충돌이 해소되지 않는다. 두 마커 DTO는 JSON 필드 이름은 같지만 OpenAPI의 enum 제약과 필드 설명까지 동일한 타입은 아니다.

**코드 근거:** [IAM 지도 DTO](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/dto/StopBoundsResponse.kt#L27), [stop 지도 DTO](../modules/stop/src/main/kotlin/com/media/bus/stop/map/dto/StopMarkerResponse.kt#L21), [stop 지도 컨트롤러](../modules/stop/src/main/kotlin/com/media/bus/stop/map/controller/StopMapController.kt#L47), [IAM 관리자 컨트롤러](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/controller/AdminStopPriceController.kt). 클래스의 `@Schema`에는 고유 `name`이 지정되어 있지 않으며, 기존 설정에도 `springdoc.use-fqn`은 없다.

**충돌 근거:** swagger-core의 기본 이름 결정은 `@Schema(name=...)`이 없으면 단순 클래스명을 사용한다. 따라서 패키지가 달라도 두 타입 모두 `StopMarkerResponse`라는 스키마 키를 요구한다. [swagger-core 2.2.55 TypeNameResolver](https://github.com/swagger-api/swagger-core/blob/v2.2.55/modules/swagger-core/src/main/java/io/swagger/v3/core/jackson/TypeNameResolver.java).

저장소가 사용하는 springdoc 3.2.0은 추출한 스키마를 이름 키로 `components.schemas`에 합친다. 이미 같은 키가 있는 일반 스키마를 패키지별 별도 타입으로 자동 분리하지 않는다. 생성 작업이 성공해도 두 API가 같은 `$ref`를 공유하면서 한쪽의 enum 제약·설명을 다른 쪽에도 적용하거나 잃을 수 있다. [springdoc 3.2.0 스키마 병합 구현](https://github.com/springdoc/springdoc-openapi/blob/v3.2.0/springdoc-openapi-starter-common/src/main/java/org/springdoc/core/utils/SpringDocAnnotationsUtils.java).

이는 **기본 설정으로 단일 명세를 생성할 때의 충돌**이다. 아직 `app` 명세를 실제로 생성하지 않았으므로 어느 정의가 최종 산출물에 남았다고 단정하지 않는다. JSON 런타임 직렬화가 망가졌다는 주장도 아니다.

**수정안:** 2단계에서 다음 중 하나를 확정한다.

- 각 공개 DTO에 `IamStopMarkerResponse`, `StopMapMarkerResponse`처럼 고유한 `@Schema(name=...)`을 지정한다. 상위 `StopBoundsResponse`도 함께 구분한다.
- `springdoc.use-fqn: true`로 전체 클래스명을 스키마 식별에 사용한다. 생성 클라이언트의 타입 이름이 바뀔 수 있으므로 기존 소비 도구도 확인한다. 이 설정은 [springdoc 공식 속성 목록](https://springdoc.org/#springdoc-openapi-core-properties)에 정의되어 있다.
- 문서를 모듈별 그룹으로 분리하고 그룹별 DTO가 충돌하지 않도록 수집 범위를 제한한다. 이 선택을 한다면 그룹 설계를 4단계에서 2단계로 당긴다. 기본 통합 명세도 계속 산출한다면 그 명세에는 별도의 이름 충돌 대책이 필요하다.

**완료 조건:** `generateAllOpenApiDocs` 성공 외에 두 지도 API의 응답 `$ref`, 필드 타입·enum·설명을 확인한다. 각 경로가 의도한 DTO를 나타내고, 그룹 또는 처리 순서에 따라 정의가 우연히 달라지지 않아야 한다.

## 4. 단계와 표현을 명확히 할 사항

아래는 위 주요 발견 2건과 별개다. 이미 정한 방향을 구현자가 다르게 해석하지 않도록 보완할 항목이며, 현재 인증 우회나 운영 장애로 집계하지 않는다.

| 계획 위치 | 남은 모호함 또는 누락 | 권장 정리 |
|---|---|---|
| 292~293행과 369행 | 2단계 OpenAPI 항목에는 “최종 명세에 internal API가 없어야 한다”가 있으나 실제 internal 매핑 삭제는 3단계다. 2단계 완료 조건인지 전체 전환 완료 조건인지 불명확하다 | 2단계에서는 생성 성공, 3단계에서는 internal 경로 부재를 검사한다고 분리한다. 2단계부터 제외하려면 `springdoc.paths-to-exclude: /api/v1/internal/**` 등 명시적인 문서 필터를 추가한다. HTTP 접근 차단만으로 문서에서 사라지지는 않는다. [springdoc 경로 제외 설정](https://springdoc.org/#springdoc-openapi-core-properties) |
| 291행 | 새 `openapi` 프로필의 DB·Redis 처리 방법은 선택 대상으로 적었지만, 활성화되는 예약 만료 스케줄러는 언급하지 않았다 | 문서 생성 프로필에 `reservation.hold.expiry-enabled: false`를 명시한다. 현재 스케줄러는 `matchIfMissing=true`이고 기본 30초 후 실행된다. 로컬 DB를 재사용하는 선택에서는 문서 생성 중 만료 처리가 실행될 수 있다. [ReservationHoldExpiryScheduler](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/hold/ReservationHoldExpiryScheduler.kt#L20) |
| 333·476행 | “모든 `X-User-*` 제거”를 접두 검사로 구현하면 기존 신뢰 헤더인 `X-Email-Verified`는 남는다 | 기존 gateway의 신뢰 헤더 6개를 기준으로 삭제 목록을 명시한다. 특히 `X-Email-Verified`를 포함한다. principal을 JWT에서만 만드는 목표 구조에서는 이 헤더가 남았다는 사실만으로 인증 우회가 되지는 않지만, 기존 헤더 정제 동작을 유지한다는 설명은 불완전하다. [MemberPrincipal 헤더 상수](../modules/auth-contract/src/main/kotlin/com/media/bus/contract/security/MemberPrincipal.kt#L46) |

## 5. 다시 결함으로 판단하지 않은 항목

- **2단계에서 S2S 업무가 아직 동작하지 않는 상태:** 90·310행이 루프백 생략과 3단계 연속 진행을 명시한다. 단계 사이 운영 배포도 금지하므로 그 자체가 모순은 아니다.
- **직접 호출과 트랜잭션 합류:** 실제 소비자 Facade에 트랜잭션이 없고 401행도 합류 조건을 구분한다. 모든 작업이 자동으로 하나의 큰 트랜잭션이 된다는 근거는 없다.
- **단일 체인 또는 분리 체인 선택:** 절 제목은 “단일 보안 체인”이지만 314행은 공개·관리 요청을 한 체인에서 구분하거나 서로 겹치지 않는 체인을 둘 수 있다고 명시한다. 정책 일관성이 충족되면 구현 선택의 문제다.
- **Liquibase 이력 테이블 위치:** 운영 조회 전에는 다중 이력의 존재를 확정할 수 없다. 0단계 결과가 다중 이력이면 그때 306행의 단일 master 구성을 재검토해야 한다는 조건을 유지한다.
- **롤백 보장:** 20·445~459행이 같은 이미지·볼륨·환경과 사전 리허설을 전제로 한다. 검증 전제는 남지만 “무조건 재기동만 하면 된다”는 이전의 단정은 보완되어 있다.

## 6. 반영 및 검증 범위

| 단계 | 추가할 내용 | 확인할 결과 |
|---|---|---|
| 0 | 관리자 일괄 등록의 제공자 5xx 기준선 | HTTP 상태·본문 코드·메시지가 구분되어 기록됨 |
| 2 | OpenAPI 스키마 식별 정책, 문서 생성 프로필의 스케줄러 비활성화, 헤더 정제 목록 구체화 | 공개 DTO가 서로 잘못 참조되지 않음. 문서 생성이 예약 만료 작업을 시작하지 않음 |
| 3 | 제공자 5xx 오류 변환 또는 의도한 계약 변경 명시, 최종 명세의 internal 경로 검사 | 선택한 오류 계약과 API 명세가 일치함 |
| 5 | 기존 전환·롤백 리허설에 오류·문서 회귀 결과 포함 | 이전 버전과 새 버전의 외부 계약 차이가 설명되고 검증됨 |

이번 작업에서는 계획서와 이전 리뷰 3개를 읽고 실제 호출 경로, 예외 처리기, 자동 구성, 컨트롤러·DTO, Gradle·프로필·Docker 설정을 대조했다. 프레임워크 동작은 공식 문서와 로컬에 있는 springdoc 3.2.0·swagger-core 2.2.55 소스로 교차 확인했다.

**변경한 파일은 이 리뷰 하나다.** 계획 원문은 작업 시작 시 이미 수정된 상태였으며 이번 작업에서는 편집하지 않았다. 애플리케이션 코드와 이전 리뷰도 변경하지 않았다.

Gradle 빌드·테스트, 통합 OpenAPI 생성, 앱·컨테이너 실행, 운영 DB·Valkey 조회, 성능 측정, 전환·롤백 리허설은 수행하지 않았다. 현재 계획의 구현 전 검토로서 확인한 사실과 향후 검증 조건을 구분했다.
