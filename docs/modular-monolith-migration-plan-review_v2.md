# 모듈러 모놀리스 회귀 계획 리뷰 v2

- 검토일: 2026-11-17
- 대상: [모듈러 모놀리스 회귀 계획](modular-monolith-migration-plan.md), 1차 리뷰 반영 후 422행 버전
- 코드 기준: `bigbang/modular-monolith`, `HEAD 5a64caf`
- 비교 자료: [1차 리뷰](modular-monolith-migration-plan-review.md), 현재 코드·Gradle·프로필·배포 설정, Spring 및 JDK 공식 문서
- 검증 범위: 정적 분석. 통합 앱 구현·기동, Gradle 빌드, 운영 DB 조회, 성능 측정, 전환·롤백 리허설은 수행하지 않았다.
- 판정: **전환 방향은 타당하지만, 삭제 순서·호출 진입점·오류 계약·관리 포트 정책을 보완해야 실행 가능한 계획이 된다.**

## 1. 종합 판단

1차 리뷰의 단일 보안 체인, `PasswordEncoder` 보존, 실행 설정 통합, 입력 검증 이관, 메모리 제한, healthcheck와 재시작 정책 구분은 계획에 반영되어 있다. 이를 미반영 사항으로 다시 지적하지 않는다. 다만 반영 문구를 실제 코드에 적용했을 때 남는 의존성과 동작 차이는 추가로 확인해야 한다.

특히 **HTTP를 없애도 기존 Facade의 업무 흐름까지 생략해서는 안 되며, 내부 예외가 그대로 전달되는 것과 외부 오류 계약이 유지되는 것은 다른 조건**이다. 또 소비자별로 전환하면서 제공자 HTTP 매핑을 즉시 삭제하는 순서는 공용 엔드포인트와 충돌한다.

아래에서 ‘확정’은 문서·코드로 확인한 사실을 뜻한다. 아직 없는 `app`의 런타임 장애를 재현했다는 뜻은 아니다. 구현 선택에 따라 달라지는 사항은 조건을 별도로 적었다.

## 2. 수정 우선순위

| ID | 우선순위 | 유형 | 발견 사항 | 반영 시점 |
|---|---|---|---|---|
| V2-01 | 높음 | 연결 대상 명세 누락 | DB Service 직결 시 Facade의 검증·응답 결합·캐시 해제 누락 가능 | 1·3단계 |
| V2-02 | 높음 | 순서 모순·삭제 범위 누락 | 공용 internal 매핑을 너무 일찍 삭제하고, 소비자가 없는 매핑은 남을 수 있음 | 3단계 |
| V2-03 | 높음 | 컴파일 의존성 누락 | S2S 구현 삭제 목록에 `TokenProvider`, `RestClientConfig`, gateway 빌드 제외가 빠짐 | 2·3단계 |
| V2-04 | 높음 | 오류 계약 불일치 | 서비스 검증 예외와 Bulkhead 예외를 직접 받으면 기존 상태·코드가 달라질 수 있음 | 1·3단계 |
| V2-05 | 높음 | 보안 정책 충돌 | 모든 요청에 적용하는 허용 목록과 8081 healthcheck·선택적 8090 S2S가 충돌 | 2단계 |
| V2-06 | 중간 | 설정값 모순 | local 풀 10에 Bulkhead 19를 적용하면서 ‘풀 크기 - 1 유지’라고 설명 | 2단계 |
| V2-07 | 중간 | 필터 종료 순서 누락 | JWT 필터에서 MDC를 먼저 지우면 바깥 Access 로그에 회원 ID가 남지 않음 | 2단계 |

## 3. 주요 발견 사항

### V2-01. 어댑터의 연결 대상을 기존 업무 진입점까지 명시해야 한다

**계획 근거:** 144–156행, 263–278행은 어댑터가 제공자의 ‘Service’를 호출한다고 설명한다. 397행은 어댑터의 제공자 `..entity..` 참조도 금지한다.

**코드에서 확인한 사실:** 현재 internal 컨트롤러 중 예약·계약 컨트롤러는 DB Service가 아니라 Facade에 연결되어 있다.

| 기존 제공자 진입점 | DB Service 호출 외에 수행하는 일 | 우회 시 영향 |
|---|---|---|
| `InternalContractFacade.createContract` | IAM 회원 재검증, 정류소 조회·검증, 계약 응답 DTO 생성 | 검증 누락 또는 해당 책임이 `app`으로 이동 |
| `InternalContractFacade`의 조회 메서드 | 정류소 정보를 일괄 조회해 계약 응답에 결합 | 정류소명·번호가 누락된 응답 |
| `ReservationFacade.updateReservationStatus` | DB 상태 변경 후 해제된 선점의 Redis 캐시 제거 | 취소·만료 등으로 풀린 선점의 캐시가 남음 |
| `ReservationFacade.completeReservationToContract` | 정류소 재검증, 예약 완료·계약 생성 위임, 커밋 후 선점 캐시 제거 | 재검증·캐시 후처리 누락 |

근거: [InternalContractController](../modules/reservation/src/main/kotlin/com/media/bus/reservation/contract/controller/InternalContractController.kt), [InternalContractFacade](../modules/reservation/src/main/kotlin/com/media/bus/reservation/contract/service/InternalContractFacade.kt), [InternalReservationController](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/controller/InternalReservationController.kt), [ReservationFacade](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/service/ReservationFacade.kt).

또한 [ContractService.createContract](../modules/reservation/src/main/kotlin/com/media/bus/reservation/contract/service/ContractService.kt)는 `ContractEntity`를 반환한다. `app`이 이 메서드를 바로 호출해 DTO로 변환하면 계획의 Entity 참조 금지와 충돌한다. 기존 `InternalContractFacade.createContract`는 `ContractResponse`를 반환하므로 그 진입점을 유지하면 해결된다.

**판정:** Facade도 `@Service` 빈이므로 ‘Service’라는 표현 자체가 잘못된 것은 아니다. 그러나 구현자가 DB Service 직결로 해석할 수 있으며, 이를 안전하게 제한하는 호출 대상 표가 없다.

**수정안:** ‘제공자의 공개 애플리케이션 진입점(Service 또는 Facade)을 호출하고, 기존 컨트롤러가 위임하던 업무 흐름을 보존한다’로 명확히 한다. 포트 메서드별로 연결할 제공자 메서드, 입력 검증, 반환 DTO, 커밋 후 처리를 기록한다. `app`에는 DTO 변환과 연결만 두고 회원 재검증·캐시 제어를 복제하지 않는다.

**완료 조건:** 잘못된 회원·정류소의 계약 생성이 거절되고, 관리자 목록의 정류소 정보가 유지되며, 상태 변경·계약 완료 뒤 선점 캐시가 기존과 같이 해제된다. 어댑터가 Exposed DAO를 받지 않는다.

### V2-02. internal 매핑은 마지막 소비자 전환 후 삭제해야 한다

**계획 근거:** 263행은 `reservation→stop`을 `iam→stop`보다 먼저 전환한다. 265–269행은 ‘각 쌍마다’ 대응하는 `Internal*Controller` 메서드를 삭제하도록 한다.

**확정된 충돌:** 두 소비자가 같은 제공자 메서드를 공유한다.

| 제공자 HTTP 매핑 | reservation 소비자 | IAM 소비자 |
|---|---|---|
| `GET /api/v1/internal/stop` | PK·정류소 번호 조회 | PK·정류소 번호·이름 조회 |
| `POST /api/v1/internal/stop/bulk` | 정류소 일괄 조회 | 정류소 일괄 조회 |

근거: [reservation StopApi](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/client/StopApi.kt), [IAM StopApi](../modules/iam/src/main/kotlin/com/media/bus/iam/client/stop/StopApi.kt), [InternalStopController](../modules/stop/src/main/kotlin/com/media/bus/stop/controller/InternalStopController.kt).

`reservation→stop` 전환 직후 이 매핑을 삭제하면 아직 HTTP를 쓰는 IAM 호출이 실패한다. 기본안은 2·3단계 사이 운영 배포를 금지하므로 운영 장애를 뜻하지는 않지만, 쌍별 회귀 검증과 선택적 루프백 검증을 할 수 없는 순서다.

같은 문제가 설정 클래스 삭제에도 있다. IAM과 reservation의 `ServiceClientConfig`는 각각 두 제공자 프록시 빈을 가진다. 한 쌍을 전환한 뒤 설정 클래스 전체를 삭제하면 다른 쌍의 빈도 사라진다. 먼저 해당 `@Bean`만 삭제하고 마지막에 설정 클래스를 제거해야 한다.

**추가 누락:** 소비자 목록만 따라 삭제하면 다음 4개 매핑은 남는다. 현재 저장소의 `@HttpExchange` 인터페이스에는 대응 호출이 없다.

- `POST /api/v1/internal/iam/member/jwt`
- `POST /api/v1/internal/iam/member/login-id`
- `POST /api/v1/internal/iam/member/email`
- `GET /api/v1/internal/iam/commission/effective-rate`

근거: [MemberInternalController](../modules/iam/src/main/kotlin/com/media/bus/iam/member/controller/MemberInternalController.kt), [CommissionInternalController](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/controller/CommissionInternalController.kt), [IamApi](../modules/reservation/src/main/kotlin/com/media/bus/reservation/contract/client/IamApi.kt). 저장소 밖 호출자의 존재 여부는 이번 정적 검토로 확인하지 않았다.

**수정안:** 소비자→제공자 매핑표를 만들고, 제공자 메서드의 마지막 HTTP 소비자가 전환된 뒤 삭제한다. 또는 네 쌍의 어댑터 연결을 모두 끝낸 다음 internal 컨트롤러를 일괄 삭제한다. 호출자가 없는 4개 매핑도 외부 의존 확인 후 명시적으로 폐기 대상으로 넣는다.

**완료 조건:** 중간 상태를 검증한다면 아직 전환하지 않은 소비자도 동작해야 한다. 최종 상태에서는 소비자 포트 연결 완료와 별도로 전체 internal 매핑 0개를 검사한다.

### V2-03. S2S 삭제는 공통 인터페이스·자동 구성·gateway까지 묶어야 한다

**계획 근거:** 281행은 `S2STokenFilter`, `S2SRestClientFactory`, `JwtProvider.generateS2SToken`, `services.*` 삭제를 지정한다. 반면 `internalRestClient` 정리는 418행의 미확정 항목이고, gateway는 목표 구조에서 삭제하지만 빌드 대상에서 제거할 단계가 명시되지 않았다.

**확정된 의존성:**

| 삭제 대상 | 함께 정리해야 하는 참조 |
|---|---|
| `JwtProvider.generateS2SToken` | `JwtProvider : TokenProvider`와 `TokenProvider.generateS2SToken()` 계약 |
| `S2SRestClientFactory` | `RestClientConfig`의 import·반환 타입·팩토리 빈 생성 |
| S2S 토큰 발급 계약 | `RestClientConfig`의 무사용자 컨텍스트 토큰 전파 로직 |
| S2S 발급 메서드 | gateway `JwtAuthenticationFilter`와 해당 테스트의 직접 호출 |
| `RestClientConfig` | `AutoConfiguration.imports` 등록, 관련 테스트·테스트용 `TokenProvider` mock |

근거: [TokenProvider](../modules/common/src/main/kotlin/com/media/bus/common/security/TokenProvider.kt), [JwtProvider](../modules/auth-contract/src/main/kotlin/com/media/bus/contract/security/JwtProvider.kt), [RestClientConfig](../modules/common/src/main/kotlin/com/media/bus/common/configuration/RestClientConfig.kt), [공통 자동 구성 목록](../modules/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports), [gateway JWT 필터](../modules/gateway/src/main/kotlin/com/media/bus/gateway/filter/JwtAuthenticationFilter.kt), [settings.gradle.kts](../settings.gradle.kts).

문서에 열거한 대상만 삭제하면 공통 모듈 또는 gateway 컴파일이 깨진다. `app`의 컴포넌트 스캔에서 gateway를 제외하는 것과 Gradle 전체 빌드에서 제외하는 것은 별개다.

**수정안:** 3단계 마지막에 S2S 의존성 정리 작업을 별도로 둔다. 현재 main 코드에서 `internalRestClient` 주입 사용처는 확인되지 않았으므로 `RestClientConfig`·`TokenProvider` 폐기 여부를 이 단계에서 확정한다. 삭제한다면 자동 구성 등록과 테스트도 함께 정리한다. gateway 테스트 이관 후 `settings.gradle.kts`의 gateway include와 모듈 소스를 같은 전환 단위에서 제거한다.

**완료 조건:** main·test·자동 구성 리소스에 제거한 클래스·메서드 참조가 없고, 전체 빌드 및 `:modules:app:bootJar`가 통과한다. 외부 서울 API 클라이언트는 S2S 전용 코드와 구분해 유지한다.

### V2-04. 검증·예외 이관에 외부 오류 계약 보존 로직이 필요하다

**계획 근거:** 198행은 `@Validated` 서비스로 검증을 옮기는 선택지를 제시하고, 277행은 제공자의 `BusinessException`이 그대로 전파된다고 요약한다. 285행은 기존 상태·오류 코드 보존을 완료 조건으로 둔다.

#### 서비스 검증 예외는 현재 400 처리 대상이 아니다

현재 [ExceptionAdvisor](../modules/common/src/main/kotlin/com/media/bus/common/web/advisor/ExceptionAdvisor.kt)는 MVC의 `MethodArgumentNotValidException`을 400 / `00260`으로 처리한다. 서비스 메서드 검증은 기본적으로 `ConstraintViolationException`, 설정에 따라 `MethodValidationException`을 던진다. 이는 MVC 예외와 다른 타입이다. [Spring 메서드 검증 공식 설명](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html).

이 두 서비스 검증 예외의 전용 처리기는 현재 없다. 따라서 서비스 `@Validated` 방식을 선택하고 예외 처리를 그대로 두면 catch-all로 들어가 500 / `00100`이 된다. IAM의 제약 없는 요청 DTO가 제공자 DTO로 변환되는 계약 완료 경로가 구체적인 대상이다.

**수정안:** 제공자 검증 실패를 `BusinessException(CommonResult.VALIDATION_FAIL, ...)` 등 정해진 오류로 변환하거나, 입력 검증용 서비스 예외를 처리하는 공통 핸들러를 추가한다. 반환값 검증 실패 같은 서버 결함까지 일괄 400으로 바꾸지 않도록 범위를 구분한다. 검증 대상 서비스 프록시를 실제로 통과하는 테스트도 필요하다.

#### Bulkhead 실패는 예약 항목의 오류 코드가 달라진다

정류소 조회 시 Bulkhead가 포화된 상황에서 다음 차이가 발생한다.

| 경로 | 기존 HTTP 호출 | 직접 호출 후 별도 대응이 없을 때 |
|---|---|---|
| stop 진입 거절 | `BulkheadFullException` → 제공자 429 / `00270` | `BulkheadFullException`이 직접 전파 |
| 소비자 수신 | `S2SRestClientFactory`가 `BusinessException`으로 변환 | HTTP 변환 단계 없음 |
| `ReservationFacade.reserveOne` | `catch (BaseException)` → 항목 `errorCode=00270` | `catch (Exception)` → 항목 `errorCode=00100` |

근거: [TransactionalBulkheadAspect](../modules/common/src/main/kotlin/com/media/bus/common/core/aop/TransactionalBulkheadAspect.kt), [S2SRestClientFactory](../modules/common/src/main/kotlin/com/media/bus/common/client/S2SRestClientFactory.kt), [ReservationFacade의 예외 분기](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/service/ReservationFacade.kt), [CommonResult](../modules/common/src/main/kotlin/com/media/bus/common/result/type/CommonResult.kt).

예약 생성은 실패를 항목별 결과에 담으므로 바깥 `ExceptionAdvisor`를 수정하는 것만으로 이 사례가 해결되지 않는다. 소비자 또는 포트 경계에서 과부하를 동일한 의미의 오류로 다뤄야 한다.

또한 현재 S2S 변환기는 401·403을 `BusinessException`으로 보존하지 않고 500 `ServiceException`으로 바꾼다. 따라서 277행의 ‘제공자 4xx → 같은 상태’ 설명에는 이 예외 조건도 필요하다. 다만 폐기할 S2S 인증 장애 자체를 모놀리스에 재현할 필요는 없다.

**완료 조건:** 빈 계약명·음수 금액뿐 아니라 포트 제공자 과부하도 회귀 항목에 넣는다. HTTP 상태, 본문 코드, 예약의 항목별 코드, 필요한 응답 헤더를 구분해 비교한다.

### V2-05. 관리 포트와 임시 S2S의 보안 정책을 별도로 정의해야 한다

**계획 근거:** 229행은 단일 보안 체인, 240행은 허용 prefix 밖 404, 343행은 공개 목록 밖 모든 핸들러의 토큰 검증을 요구한다. 동시에 342행은 8081의 `/actuator/health`를 healthcheck에 사용하고, 380–381행은 선택적으로 8090 S2S를 허용한다.

**충돌:** 허용 목록을 포트 구분 없이 모든 요청에 적용하면 `/actuator/health`는 관리 포트에서도 404 대상이다. 임시 `/api/v1/internal/**` 역시 허용 목록에 없고, HTTP 클라이언트는 사용자 Bearer가 아닌 `X-Service-Token`을 보낸다. `S2STokenFilter`를 남기는 것만으로 앞선 허용 목록·JWT 필터를 통과하지는 못한다.

포트를 분리했다고 사용자 정의 보안 체인이 자동으로 올바른 관리 정책을 갖는 것은 아니다. 사용자 정의 `SecurityFilterChain`이 있으면 Actuator 보안 자동 구성이 물러나므로 접근 규칙을 직접 정의해야 한다. [Spring Boot 4.1.2 Actuator 보안](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

**수정안:** 공개 8080 정책, 관리 8081 정책, 선택적 로컬 8090 정책의 적용 범위와 순서를 적는다. 단일 체인을 유지한다면 관리 요청을 공개 API 허용 목록·JWT 검사에서 구분하고, health와 loggers의 권한을 각각 정의한다. 체인을 분리한다면 ‘체인 하나’ 대신 ‘서로 겹치지 않는 matcher와 명시적 순서’로 원칙을 수정한다. 8090을 쓰지 않는 기본안에서는 임시 분기가 필요 없다.

| 검증 요청 | 기대 결과 |
|---|---|
| 컨테이너 내부 `127.0.0.1:8081/actuator/health`, 정상 앱·의존성 | 사용자 토큰 없이 200 |
| 공개 8080 `/actuator/health`, `/actuator/loggers` | 토큰 유무와 무관하게 404 |
| 공개 8080 internal 경로 | 차단, 최종 상태에서는 매핑 없음 |
| 선택적 로컬 8090 internal 경로 | 유효한 S2S 토큰일 때만 통과, 사용자 Bearer를 별도로 요구하지 않음 |

### V2-06. local 풀 크기와 Bulkhead 동시 호출 수가 맞지 않는다

**계획 근거:** 220행은 Hikari 풀 `20 (local 10)`, 221행은 Bulkhead `19`와 ‘풀 크기 - 1 유지’를 함께 명시한다. 213행은 기본·local 모두 해당 표의 값으로 기동한다고 한다.

**확정된 모순:** local에서 `19 ≠ 10 - 1`이다. 이대로면 최대 19개 작업을 DB 진입 전에 통과시키면서 커넥션은 10개만 있어, 문서가 의도한 진입 제한이 커넥션 대기를 막지 못한다.

현재 local 설정도 Bulkhead 10 / 풀 10이다. 따라서 기존 local 값을 그대로 이관하는 것과 ‘풀 - 1’ 규칙을 적용하는 것은 같은 작업이 아니다. 근거: [reservation local 설정](../modules/reservation/src/main/resources/application-local.yml), [DB Bulkhead 구현](../modules/common/src/main/kotlin/com/media/bus/common/core/aop/TransactionalBulkheadAspect.kt).

**수정안:** 시작값을 운영 `20/19`, local `10/9`처럼 프로필별로 분리하거나, 다른 값을 선택한다면 근거와 목표를 수정한다. `hig.bulkhead.database-name`과 실제 인스턴스 이름의 일치도 함께 확인한다.

단, 풀보다 퍼밋이 하나 적다는 사실만으로 모든 풀 고갈이 방지되는 것은 아니다. 기존 Aspect는 같은 실행 흐름의 중첩 호출에서 퍼밋을 추가로 얻지 않으며, `REQUIRES_NEW`는 별도 연결을 사용할 수 있다. 감사 로그 등 중첩 트랜잭션은 부하 검증 대상이다. 전환으로 새로 발생했다고 단정할 문제는 아니지만 ‘bulkhead 유지’만으로 모듈별 자원 격리가 보장된다고 설명해서는 안 된다.

**완료 조건:** 각 프로필의 실제 풀·퍼밋 값을 확인하고, 과부하 시 대기·거절 동작을 검증한다.

### V2-07. MDC 제거는 바깥 Access 로그가 끝난 뒤여야 한다

**계획 근거:** 251행은 JWT 필터가 회원 ID를 MDC에 넣고 ‘요청이 끝나면’ 지우도록 한다. 252행은 Access 필터가 체인 실행 후 값을 읽는지 확인하도록 한다.

현재 순서는 다음과 같다.

```text
MdcLoggingFilter 진입
  AccessLoggingFilter 진입: START 로그
    Spring Security / JWT 필터 진입: 검증된 memberId 설정
      컨트롤러 실행
    JWT 필터 반환
  AccessLoggingFilter finally: ACCESS 로그
MdcLoggingFilter finally: MDC 정리
```

근거: [CommonLoggingAutoConfiguration](../modules/common/src/main/kotlin/com/media/bus/common/autoconfigure/CommonLoggingAutoConfiguration.kt), [AccessLoggingFilter](../modules/common/src/main/kotlin/com/media/bus/common/logging/AccessLoggingFilter.kt), [MdcLoggingFilter](../modules/common/src/main/kotlin/com/media/bus/common/logging/MdcLoggingFilter.kt).

**조건부 문제:** JWT 필터의 `finally`에서 `memberId`를 지우면, 뒤이어 실행되는 Access 필터의 `finally`에서는 이미 값이 없다. Access가 응답 시점에 읽는지만 확인해서는 충분하지 않다.

**수정안:** 현재처럼 바깥 `MdcLoggingFilter`가 최종 정리를 맡고 JWT 필터는 검증된 값 주입만 담당하거나, Access 로그 시점에 검증된 request attribute에서 값을 복원하는 정책을 정한다. 인증 전 `START` 로그에는 회원 ID가 없을 수 있음을 구분한다.

**완료 조건:** 위조 헤더 요청의 `ACCESS` 로그에는 토큰 회원 ID가 남고, 인증 실패 요청과 다음 요청에는 이전 회원 ID가 남지 않는다.

## 4. 추가로 정정하거나 명확히 할 사항

| 항목 | 확인 결과 | 수정 방향 |
|---|---|---|
| S2S 수량(14·115–122행) | 클라이언트 선언 메서드는 `3+1+12+10=26`개. 제공자 internal 매핑은 stop 10 + iam 5 + reservation 10 = 25개. 현재 클라이언트가 참조하는 고유 HTTP 메서드·경로는 21개 | ‘22개’ 대신 집계 단위를 명시. 클라이언트 메서드와 HTTP 매핑을 구분하고 V2-02의 미사용 4개도 포함 |
| `..entity..` 전체 참조 금지(397행) | 제공자 요청 DTO의 `PaymentCycle`, `PaymentMethod`, `ContractStatus` 등이 `entity.enums` 아래에 있음 | DAO/Table 접근 금지와 enum 사용을 구분. enum을 계약 패키지로 옮기거나 검사에서 명시적으로 구분해야 DTO 변환과 양립 |
| `fromName`만 쓰면 기존 4xx 유지(198행) | 여러 enum의 `fromName`은 nullable 반환. 잘못된 값에서 자동으로 4xx 예외가 발생하지 않음 | `null`이면 명시적 검증 오류. 검색 필터의 잘못된 enum을 `null` 필터로 바꾸어 전체 조회하지 않도록 테스트 |
| OpenAPI 이관 시점(208·296행) | 플러그인은 2단계에서 app으로 이동하지만 루트 `generateAllOpenApiDocs` 의존 변경은 4단계. 기존 집계 작업은 세 모듈 task 경로를 참조 | 집계 작업도 플러그인 이동과 함께 수정. 일반 `build`가 반드시 실패하는 사안은 아니지만 중간 단계의 문서 생성 작업은 깨짐 |
| JVM 힙 확인 명령(303행) | `java -XX:+PrintFlagsFinal -version`은 새 JVM을 실행한다. 앱의 `-Xmx`를 실행 인수로만 지정했다면 그 설정을 검사하지 못함 | 실행 중 앱 PID를 대상으로 `jcmd <pid> VM.flags -all` 확인. 환경변수로 같은 옵션을 주입해 우연히 같은 값이 나오는 경우와 구분. [JDK 25 jcmd](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html) |
| ‘DEBUG 로그에 토큰 포함’ 설명(342행) | 현재 Access 필터는 `password`, `accessToken`, `refreshToken`, `token` 필드를 마스킹함 | 로그 레벨 무단 변경과 민감 정보 노출 위험은 유지하되, 정상 토큰 필드가 그대로 기록된다고 단정하지 않음 |
| Liquibase 파일 이동(185–189행) | 현재 module master의 include는 `/db/changelog/...` 경로를 직접 지정 | 물리 include 경로 수정과 `logicalFilePath` 보존을 별도 작업으로 명시. 운영 이력 테이블이 여러 개면 단일 이력으로 어떻게 전환할지도 0단계 결과에 따라 확정 |
| ‘이전 compose만 띄우면 롤백 완료’(20·322행) | 현재 compose는 프로젝트 이름에 종속되는 `postgres_data`, `valkey_data` 볼륨을 사용하며 앱은 `image:` 대신 `build:`로 정의됨 | 같은 실제 볼륨·시크릿·환경을 재사용하는 명령과 이전 이미지의 정확한 참조를 남김. 프로젝트 이름, 이미지·설정, 볼륨 식별자가 같다는 전제가 있어야 성립 |

마지막 두 항목은 운영 환경을 조회해 확인한 장애가 아니라 실행 절차의 전제 조건이다. 계획에 이미 있는 운영 DB 사본 리허설에서 확인할 내용으로 구체화하면 된다.

## 5. 단계별 보완안

| 단계 | 추가할 작업 | 검증 기준 |
|---|---|---|
| 0 | 클라이언트 메서드·제공자 매핑·최종 제공자 진입점 대응표, 실제 DB/Valkey 볼륨·이미지 식별자 기록 | 26개 선언 메서드와 25개 매핑의 처리 방침이 모두 있음 |
| 1 | 소비자 포트와 제공자 Facade/Service 경계 확정, 서비스 검증 실패 변환 추가 | HTTP 구현을 유지한 상태에서 정상·실패 응답 계약 유지 |
| 2 | 관리 포트 정책, local 풀/퍼밋, MDC 정리 주체 확정. OpenAPI 집계 작업 동시 변경 | 기본·local 기동, 8080 차단·8081 health 성공, 인증된 ACCESS 로그 |
| 3 | 모든 소비자 연결 후 공용 매핑 삭제, 미사용 internal 매핑 폐기, S2S 참조 그래프 전체 정리 | Facade 후처리·검증·과부하 오류 코드 보존, internal 매핑 0개, 전체 빌드 성공 |
| 4 | 실행 중 JVM의 힙 확인, 정확한 이미지·볼륨을 사용하는 배포/복구 명령 작성 | 실측 힙·RSS, 실제 컨테이너 healthcheck, 프로세스 종료·응답 불능 복구 확인 |
| 5 | 동일 DB·Valkey 사본으로 신버전 전환 및 구버전 복귀 | Liquibase 재적용 없음, 세션·예약·계약 호환, 볼륨 교체·신규 생성 없음 |

## 6. 이번 리뷰의 검증 결과와 한계

- 계획 422행, 1차 리뷰, 저장소 공통·모듈 지침, 실제 호출·필터·예외·Gradle·설정 코드를 대조했다.
- 정적 집계로 HTTP 클라이언트 메서드 26개와 internal 컨트롤러 매핑 25개를 확인했다.
- 프레임워크 동작 중 서비스 검증 예외와 Actuator 보안 자동 구성은 공식 자료로 확인했다.
- 이번 산출물은 리뷰 문서다. 계획 원문·1차 리뷰·애플리케이션 코드는 수정하지 않았다.
- 운영 DB의 Liquibase 이력 위치, 저장소 밖 internal 호출자, 실제 앱 기동·성능·롤백 성공 여부는 확인되지 않았다. 위 완료 조건은 향후 구현 검증 항목이며 통과했다고 보고하는 항목이 아니다.
