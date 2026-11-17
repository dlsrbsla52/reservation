# 모듈러 모놀리스 회귀 계획

- 브랜치: `bigbang/modular-monolith` (기준 `origin/main` `5a64caf`)
- 작성일: 2026-11-17
- 상태: 계획 (코드 변경 전). [1차 리뷰](modular-monolith-migration-plan-review.md)·[2차 리뷰](modular-monolith-migration-plan-review_v2.md) 검증 후 반영함 — [리뷰 반영](#리뷰-반영)

## 결론

| 항목 | 지금 (MSA) | 목표 (모듈러 모놀리스) |
|---|---|---|
| 실행 단위 | JVM 4개 (`gateway`, `iam`, `stop`, `reservation`) | JVM 1개 (`app`) |
| 진입점 | Spring Cloud Gateway(WebFlux) 8080 | `app` 8080 (앞단 TLS는 Caddy 등 리버스 프록시) |
| 인증 | gateway가 JWT 검증 → `X-User-*` 헤더 주입 → 서비스가 헤더 신뢰 | `app`의 서블릿 필터가 JWT를 직접 검증해 `MemberPrincipal` 생성 |
| 모듈 간 호출 | `@HttpExchange` S2S — 클라이언트 메서드 26개, 고유 HTTP 매핑 21개(제공자 internal 매핑은 25개) + `X-Service-Token` | 같은 프로세스 안의 포트(인터페이스) 호출 |
| DB | DataSource 3개(서비스별 풀 20) | DataSource 1개, 스키마는 그대로 `auth`/`audit`/`stop`/`reservation` |
| Liquibase | 서비스마다 같은 경로의 master 1개 | 루트 master 1개가 모듈별 master를 include |
| Gradle 모듈 경계 | 유지 | **유지** — `iam`/`stop`/`reservation`은 서로 컴파일 의존하지 않고, 연결은 `app`에서만 |

- **유지하는 것**: 스키마 분리, 모듈별 패키지, Gradle 모듈 경계, URL 계약(`/api/v1/**`). 다시 쪼갤 때 비용을 낮게 묶어 두기 위해서다.
- **DB 스키마와 데이터 변경 없음**: 롤백은 이전 compose를 다시 띄우는 것으로 끝난다.
- **가장 큰 위험**: 하위 서비스가 gateway가 넣어 준 `X-User-*` 헤더를 그대로 믿는다. `MemberPrincipalExtractFilter`뿐 아니라 컨트롤러 19곳이 `@RequestHeader("X-User-Id")`를 직접 읽는다. gateway를 없애면서 이걸 그대로 두면 다른 회원으로 행세할 수 있다([외부 노출 표면 점검](#외부-노출-표면-점검)).

## 리뷰 반영

### 1차 리뷰

[1차 리뷰](modular-monolith-migration-plan-review.md)의 지적을 코드와 대조해 검증했다. 6건 모두 사실이며, 아래 단계별 계획에 반영했다.

| ID | 판정 | 코드에서 확인한 근거 | 반영 위치 |
|---|---|---|---|
| R1 | 맞음 | 세 모듈 `SecurityFilterChain` 모두 `securityMatcher` 없이 전체 요청 대상. 이름만 바꿔 함께 등록하면 `UnreachableFilterChainException`으로 기동 실패. `PasswordEncoder` 빈이 iam `SecurityConfig` 안에 있음 | 1단계에서 보안 설정 이름 변경 제거, 2단계에서 단일 체인·JWT 이관·`PasswordEncoder` 분리를 함께 |
| R2 | 맞음 | iam `CompleteToContractRequest`는 제약 없음, reservation 쪽은 `@NotBlank`·`@Size(max=300)`·`@Positive`. `InternalContractController`는 `page.coerceAtLeast(0)`, `size.coerceIn(1, MAX_PAGE_SIZE)` 보정. `InternalStopController`는 `StopType.parseFilter`로 문자열 변환 | 1단계(검증 목록화·이관), 3단계 완료 조건 |
| R3 | 맞음(설명 일부 보정) | 세 모듈 모두 `application-local.yml`이 있고 포트, `allow-bean-definition-overriding: true`, 기존 Liquibase 경로를 담고 있음. 같은 이름 리소스는 병합되지 않고 클래스패스 첫 번째만 쓰이므로, `app`에 `application-local.yml`이 없으면 **라이브러리 JAR의 것 하나가 그대로 로드된다** | 2단계 |
| R4 | 맞음 | `MdcLoggingFilter`가 `request.getHeader("X-User-Id")`를 MDC `memberId`에 넣고, 순서가 `HIGHEST_PRECEDENCE + 200`이라 Spring Security(`-100`)보다 먼저 실행됨 | 2단계 |
| R5 | 맞음 | compose에 메모리 제한이 없어 `MaxRAMPercentage=60`이면 4GB 호스트에서 힙 상한 약 2.4GB. 목표 힙과 모순 | 목표 스펙, 4단계 |
| R6 | 맞음 | `restart: unless-stopped`는 프로세스 종료에만 반응하고 `unhealthy`로는 재시작하지 않음 | 위험 표 |
| 4절 모순 3건 | 맞음 | 공개 경로 `startsWith`/정확 일치, 루프백 주소 8080/8090, 트랜잭션 합류 조건 | 해당 단계 본문 |

**트랜잭션 지적 추가 확인**: S2S를 호출하는 쪽(`AdminReservationFacade`, `AdminStopPriceFacade`, `AdminBusinessContractFacade`, `AdminStopPriceController`, reservation의 `ReservationFacade`·`ContractFacade`·`InternalContractFacade`)에는 `@Transactional`이 없다. 파사드가 트랜잭션 밖에서 원격 호출을 하고, DB 작업은 각 Service의 `@Transactional` 안에서 하도록 이미 나뉘어 있다. 직접 호출로 바꿔도 트랜잭션은 지금처럼 Service 단위로 따로 열리므로, 처음 계획보다 위험이 낮다.

**리뷰에 없던 추가 발견**
- `modules/stop/src/main/resources/application-local.yml`의 DataSource URL이 `?schema=reservation`이다(복사 실수로 보임). 테이블이 스키마를 명시하고 pgjdbc가 `schema` 파라미터를 무시해서 지금은 영향이 없다. 2단계에서 local 설정을 `app`으로 옮길 때 같이 정리된다.
- `AdminStopPriceController`는 파사드를 거치지 않고 컨트롤러에서 S2S 클라이언트를 직접 쓴다. 포트를 추출할 때 이 경로도 포함해야 한다.

### 2차 리뷰

[2차 리뷰](modular-monolith-migration-plan-review_v2.md) 7건과 정정 사항 8건을 코드와 대조했다. 모두 사실이다.

| ID | 판정 | 코드에서 확인한 근거 | 반영 위치 |
|---|---|---|---|
| V2-01 | 맞음 | `InternalContractController`→`InternalContractFacade`, `InternalReservationController`→`ReservationFacade`에 위임. `InternalContractFacade.createContract`는 `verifyMemberIfPresent`·`resolveStop` 후 `ContractResponse` 반환. `ContractService.createContract`는 `ContractEntity` 반환. `ReservationFacade.updateReservationStatus`·`completeReservationToContract`는 `stopHoldGate.evict` 호출 | 목표 구조, 1단계 3, 3-1 |
| V2-02 | 맞음 | reservation·iam의 `StopApi`가 모두 `GET /api/v1/internal/stop`과 `POST /bulk`를 씀. 두 `ServiceClientConfig`가 각각 프록시 빈 2개를 가짐. iam이 제공하는 internal 매핑 5개 중 클라이언트가 쓰는 것은 `/member/id` 하나 | S2S 호출 목록, 3-1, 3-2 |
| V2-03 | 맞음 | `JwtProvider : TokenProvider`, `TokenProvider`는 `generateS2SToken()` 하나만 가짐. `RestClientConfig`가 `S2SRestClientFactory` 빈 생성과 `generateS2SToken` 호출. 참조가 `common` 테스트 4개, `gateway` main·test, reservation 테스트 1개에 있음 | 3-3 |
| V2-04 | 맞음 | `ExceptionAdvisor`에 `ConstraintViolationException`·`MethodValidationException` 처리기 없음(→ catch-all 500/`00100`). `ReservationFacade.reserveOne`은 `catch (BaseException)`·`catch (Exception)`으로 나뉘고 `BulkheadFullException`은 `BaseException`이 아님. `translateClientError`는 401·403을 `ServiceException`으로 변환 | 1단계 4, 3단계 표 |
| V2-05 | 맞음 | 허용 목록·JWT 검사를 포트 구분 없이 적용하면 8081 health와 8090 S2S가 막힘. 사용자 정의 `SecurityFilterChain`이 있으면 Actuator 보안 자동 구성이 적용되지 않음 | 2-3 포트별 정책 표 |
| V2-06 | 맞음 | 세 모듈 local 설정이 모두 풀 10·퍼밋 10. 계획의 "local 10 / 19"는 규칙과 모순 | 2-2 |
| V2-07 | 맞음 | 순서 `MdcLoggingFilter(+200)` → `AccessLoggingFilter(+300)` → Security. `ACCESS` 로그는 Access 필터 `finally`, MDC 정리는 Mdc 필터 `finally` | 2-4 |
| 정정 8건 | 맞음 | 클라이언트 메서드 26 / 고유 매핑 21 / 제공자 매핑 25(원안의 "22개"는 오기). `PaymentCycle` 등이 `contract/entity/enums`. `fromName` 13개 모두 nullable. `generateAllOpenApiDocs`가 세 모듈 task 참조. `PrintFlagsFinal`은 새 JVM. Access 로그가 토큰 필드 마스킹. include가 절대 경로. compose가 `build:`와 프로젝트 이름 종속 볼륨 사용 | 해당 본문 |

**2차 리뷰에 더한 것**
- **Bulkhead 공유**: 한 JVM으로 합치면 모든 모듈이 같은 `orderDatabase` 퍼밋(운영 19)을 나눠 쓴다. 지금은 서비스마다 19씩 따로 있다. 정류소 조회 폭주가 예약 쓰기의 퍼밋을 잠식할 수 있으니 3단계 부하 검증 대상에 넣었다.

### 바뀐 단계 순서

| 단계 | 처음 계획 | 리뷰 반영 후 |
|---|---|---|
| 0 | 준비 | 준비 (같음) |
| 1 | 충돌 제거(Liquibase, 빈 이름) | 충돌 제거(Liquibase, 클라이언트 빈 이름) + **소비자 포트 추출 + 제공자 입력 검증 이관**. 아직 HTTP 구현으로 동작 |
| 2 | `app` 생성(루프백 S2S) | `app` 생성 + **단일 보안 체인·JWT 이관·`X-User-*` 제거·설정 단일화·MDC 수정**을 한 단계로 |
| 3 | 게이트웨이 역할 이관 | 어댑터 연결, S2S HTTP와 internal 매핑 제거 |
| 4 | S2S 교체 | 배포 구성·메모리 제한·복구 정책 |
| 5 | 배포 구성 | 운영 DB 사본에서 전환·롤백 리허설 후 운영 전환 |
| 6 | 운영 전환 | 문서·지침 갱신 |

2단계와 3단계를 연달아 진행하고 그 사이에 운영 배포를 하지 않으면, 루프백 S2S와 임시 내부 커넥터(8090)가 필요 없다. 이 계획은 **루프백 단계를 두지 않는 것**을 기본으로 한다. 2단계 기동 확인에 루프백이 꼭 필요하면 로컬에서만 8090 커넥터 방식으로 쓴다.

## 왜 회귀하는가

- 트래픽이 거의 없는데 JVM 4개의 기본 메모리(각 300~500MB)가 서버 비용 대부분을 차지한다.
- `reservation`→`stop`·`iam`, `iam`→`stop`·`reservation`이 HTTP로 엮여 있어 부분 실패, 타임아웃, 4xx 변환(`S2SRestClientFactory.translateClientError`)을 계속 관리해야 한다.
- 4개 서비스의 기동 순서와 버전을 맞춰 배포해야 한다.

### 목표 스펙 (참고, 측정 전 목표치)

아래는 목표치이며 0단계 기준선과 4단계 측정으로 확정한다. 힙 상한은 compose `mem_limit`과 `-Xmx`를 함께 지정해야 보장된다(R5).

| 구성 | 메모리 |
|---|---|
| `app` (`mem_limit: 1g`, `-Xmx640m` 시작점) | 0.7~1GB |
| PostgreSQL 18 | 256~512MB |
| Valkey 8 | 50~100MB |
| Caddy, OS, Docker | 300~400MB |
| **합계** | **약 1.5~2.2GB** → 4GB 인스턴스(`t4g.medium` 급) 권장 |

EC2 한 대 + EIP 하나로 inbound(도메인 A 레코드)와 outbound(외부 화이트리스트) 고정 IP를 함께 해결한다. Fargate는 RDS·ElastiCache·ALB·NAT가 고정비로 붙어 이 규모에서는 맞지 않는다.

## 현재 구조에서 확인한 사실

### 모듈

| 모듈 | 소스(main/test) | 역할 |
|---|---|---|
| `common` | 38 / 10 | 공통 인프라, `ApiResponse`, `ExceptionAdvisor`, `S2SRestClientFactory` |
| `auth-contract` | 14 / 5 | `JwtProvider`, `MemberPrincipal`, `S2STokenFilter`, `@Authorize` |
| `gateway` | 4 / 2 | 라우팅, CORS, `JwtAuthenticationFilter`(GlobalFilter) |
| `iam` | 147 / 15 | 인증, 회원, 관리자 API(관리자 기능은 stop·reservation으로 S2S 위임) |
| `stop` | 42 / 14 | 정류소, 단가 |
| `reservation` | 65 / 8 | 예약, 계약, 선점(hold) 만료 스케줄러 |

### 합칠 때 충돌하는 지점

| # | 충돌 | 위치 | 해결 단계 |
|---|---|---|---|
| C1 | 세 모듈의 Liquibase master가 같은 classpath 경로 `/db/changelog/db.changelog-master.xml` | `modules/{iam,stop,reservation}/src/main/resources/db/changelog/` | 1단계 |
| C2 | 같은 빈 이름: `securityFilterChain`, `s2sTokenFilter`(3개 모듈), `stopApi`(iam·reservation) | 각 모듈 `SecurityConfig`, `ServiceClientConfig` | 보안: 2단계(단일 체인으로 교체) / 클라이언트: 1단계 |
| C3 | 같은 클래스 이름 → 같은 기본 빈 이름: `SecurityConfig`(3), `ServiceClientConfig`(2), `StopServiceClient`(2) | 위와 같음 | 보안: 2단계 / 클라이언트: 1단계 |
| C9 | 전체 요청 대상 `SecurityFilterChain` 3개 → 함께 등록 시 `UnreachableFilterChainException` (R1) | 각 모듈 `SecurityConfig` | 2단계 |
| C10 | 라이브러리 JAR에 `application.yml`·`application-local.yml`이 남음 (R3) | 각 모듈 `src/main/resources` | 2단계 |
| C11 | `MdcLoggingFilter`가 보안 필터보다 먼저 `X-User-Id`를 MDC에 기록 (R4) | `common/.../MdcLoggingFilter.kt` | 2단계 |
| C4 | `spring.main.allow-bean-definition-overriding: true`라 C2·C3이 **오류 없이 덮어써진다** | iam·stop·reservation `application.yml` | 2단계에서 `app`은 `false` |
| C5 | `@SpringBootApplication`이 4개 | 각 모듈 `*Application.kt` | 2단계 |
| C6 | 서비스가 `X-User-*` 헤더를 검증 없이 신뢰 | `auth-contract/.../MemberPrincipalExtractFilter.kt` | 2단계 |
| C7 | `/api/v1/internal/**` 차단을 gateway 라우트(`block-internal`)가 담당 | `gateway/application.yml` | 3단계(internal 매핑 삭제) |
| C8 | 서비스별 Hikari 풀 20 + bulkhead `orderDatabase` 19 | 각 `application.yml` | 2단계 |

### 충돌하지 않는 것 (확인함)

- **Exposed 테이블은 전부 스키마를 명시**한다(`"auth.member"`, `"stop.stop"`, `"reservation.reservation"`, `"audit.audit_log"`). DataSource 하나로 그대로 동작한다.
- **스키마 간 FK가 없다.** FK는 `auth` 내부 5건뿐이다.
- **URL prefix가 겹치지 않는다.** `/api/v1/admin` 클래스 매핑 2개는 둘 다 `iam` 소속이다.
- **`@RestControllerAdvice`는 `common`의 `ExceptionAdvisor` 하나뿐**이다.
- **Redis 키 prefix가 겹치지 않는다**: `refresh:`(auth-contract), `email-verify:`·`member-verify:`·`password-reset:`(iam), `reservation:hold:`(reservation).
- **스케줄러는 `ReservationHoldExpiryScheduler` 하나**이고 인스턴스도 하나라 분산 락이 필요 없다.

### S2S 호출 목록

| 호출 | 소비자 클라이언트 | 제공자 컨트롤러 | 엔드포인트 수 |
|---|---|---|---|
| reservation → stop | `reservation/.../client/StopApi`, `StopServiceClient` | `stop/.../InternalStopController` | 3 |
| reservation → iam | `reservation/contract/client/IamApi`, `IamServiceClient` | `iam/.../MemberInternalController`, `CommissionInternalController` | 1 |
| iam → stop | `iam/client/stop/StopApi`, `StopServiceClient` | `InternalStopController` | 12 |
| iam → reservation | `iam/client/reservation/ReservationApi`, `ReservationServiceClient` | `InternalReservationController`, `InternalContractController` | 10 |

엔드포인트 수는 **클라이언트 메서드** 기준이다(합계 26). 같은 HTTP 매핑을 파라미터만 바꿔 여러 메서드가 쓰기도 해서, 클라이언트가 실제로 부르는 **고유 HTTP 매핑은 21개**다.

- **공유 매핑**: `GET /api/v1/internal/stop`, `POST /api/v1/internal/stop/bulk`는 reservation과 iam이 함께 쓴다. 마지막 소비자를 전환하기 전에는 지우면 안 된다(3단계).
- **호출자 없는 매핑 4개**: 제공자 internal 매핑 25개 중 저장소 안에 클라이언트가 없는 것은 `POST /api/v1/internal/iam/member/jwt`, `/login-id`, `/email`, `GET /api/v1/internal/iam/commission/effective-rate`다. 저장소 밖 호출자가 없는지 확인한 뒤 3단계에서 폐기한다.

## 목표 구조

```
modules/
  common/          라이브러리 (변경 적음)
  auth-contract/   라이브러리 — 서블릿 JWT 인증 필터 추가, 헤더 신뢰 필터 제거
  iam/             라이브러리 (bootJar 끔)
  stop/            라이브러리
  reservation/     라이브러리
  app/             실행 모듈 — BusApplication, 통합 application.yml, 보안 설정, 모듈 간 어댑터
  (gateway/ 삭제)
```

의존 방향:

```
app ──► iam, stop, reservation ──► auth-contract ──► common
iam ✕ stop, iam ✕ reservation, reservation ✕ stop   (서로 컴파일 의존 금지)
```

모듈 간 호출은 **소비자가 포트를 정의하고, `app`이 제공자의 공개 애플리케이션 진입점(Service 또는 Facade)으로 구현**한다.

- 지금 internal 컨트롤러가 위임하던 진입점을 그대로 부른다. DB Service로 바로 건너뛰면 Facade가 하던 회원 재검증, 정류소 결합, 선점 캐시 해제(`stopHoldGate.evict`)가 빠진다.
- `app` 어댑터에는 DTO 변환과 연결만 둔다. 회원 재검증이나 캐시 제어를 어댑터에 복제하지 않는다.
- 어댑터는 Exposed DAO·Table·Entity를 받지 않는다. 예: `ContractService.createContract`는 `ContractEntity`를 반환하므로 쓰지 않고, `ContractResponse`를 돌려주는 `InternalContractFacade.createContract`를 쓴다.

```kotlin
// reservation 모듈 (소비자) — 지금 StopServiceClient 의 공개 메서드를 그대로 포트로 올린다
interface StopLookupPort {
    fun getStopByPk(pk: Set<UUID>): List<StopInfo>
    fun getStopByStopId(stopId: String): List<StopInfo>
    fun getStopsByPks(pks: Collection<UUID>): List<StopInfo>
}

// app 모듈 (어댑터) — stop 의 공개 진입점을 호출하고 reservation 의 DTO 로 변환한다
@Component
class ReservationStopLookupAdapter(private val stopQueryService: StopQueryService) : StopLookupPort { ... }
```

이렇게 하면 `reservation`은 `stop`을 모르고, 나중에 다시 쪼갤 때 어댑터만 HTTP 구현으로 바꾸면 된다.

## 단계별 계획

각 단계가 끝날 때마다 빌드와 테스트가 통과하는 상태를 유지한다. 커밋은 단계 단위로 사용자가 직접 한다.

### 0단계: 준비

| 작업 | 완료 조건 |
|---|---|
| 운영 DB에서 Liquibase 이력 위치와 파일 경로 확인(아래 쿼리) | `databasechangelog`의 스키마와 `filename` 값 기록. 이력 테이블이 여러 개면 단일 이력으로 합칠지, 모듈별로 유지할지 결정 |
| S2S 대응표 작성 | 클라이언트 메서드 26개 × 제공자 매핑 25개 × 최종 제공자 진입점(Service/Facade)을 한 표로. 모든 행에 처리 방침(어댑터 연결/폐기)이 있음 |
| 운영 배포 식별자 기록 | compose 프로젝트 이름, 실제 볼륨 이름(`<project>_postgres_data`, `<project>_valkey_data`), 현재 실행 중 이미지 ID·태그, `.env` 위치 |
| 운영 DB 백업(`docs/infra/postgres-backup-plan.md` 절차) | 덤프 파일 확보 |
| 현재 기준선 측정: 컨테이너별 RSS(`docker stats`), 전체 기동 시간 | 수치 기록(전환 후 비교용) |
| 주요 API 회귀 시나리오 정리(`HTTP/` 폴더 `.http` 활용) | 로그인·토큰 갱신·관리자 로그인·예약 생성·정류소 조회·관리자 위임 API |

```sql
-- JDBC URL 의 ?schema= 는 pgjdbc 파라미터가 아니라(pgjdbc 는 currentSchema) 무시될 가능성이 높다.
-- 그렇다면 세 서비스의 이력이 public.databasechangelog 한 테이블에 섞여 있다. 실제 위치를 확인한다.
SELECT table_schema, table_name FROM information_schema.tables WHERE table_name ILIKE 'databasechangelog%';
SELECT DISTINCT filename FROM <위에서 찾은 스키마>.databasechangelog ORDER BY 1;
```

### 1단계: 충돌 제거 (MSA 상태에서도 무해)

이 단계는 동작을 바꾸지 않으므로, 원하면 따로 `main`에 먼저 머지할 수 있다.

1. **Liquibase 경로 분리 (C1)**
   - 파일을 `db/changelog/{iam,stop,reservation}/`로 옮기고, master도 `db/changelog/{module}/db.changelog-master.xml`로 바꾼다.
   - **물리 경로와 논리 경로는 따로 다룬다.** 모듈 master의 `<include file="/db/changelog/V001__...xml"/>`처럼 절대 경로로 적힌 include를 새 위치로 고치는 것(물리)과, 이력 테이블에 기록된 파일 식별자를 유지하는 것(논리)은 별개 작업이다.
   - **각 changelog 파일에 `logicalFilePath`를 0단계에서 확인한 기존 `filename` 값 그대로 지정한다.** Liquibase는 `id + author + filename`으로 적용 여부를 판단하므로, 이걸 빠뜨리면 이미 적용된 changeset을 다시 실행하려다 기동이 실패한다.
   - 각 서비스의 `spring.liquibase.change-log`를 새 경로로 바꾼다.
   - 검증: 기존 DB에 서비스를 띄웠을 때 `databasechangelog`에 새 행이 생기지 않아야 한다.
2. **클라이언트 빈 이름 정리 (C2, C3 중 클라이언트)**
   - `ServiceClientConfig`, `StopServiceClient`, `@Bean fun stopApi`에 모듈 접두를 붙인다(`iamStopApi`, `reservationStopApi` 등).
   - `SecurityConfig`는 이름만 바꿔서는 해결되지 않으므로(R1) 여기서 건드리지 않고 2단계에서 단일 체인으로 교체한다.
3. **소비자 포트 추출**
   - 각 소비자의 `*ServiceClient` 공개 메서드를 포트 인터페이스로 올리고, 지금의 HTTP 구현이 그 포트를 구현하게 한다. 소비자 코드는 포트만 보게 된다.
   - 대상: iam `AdminReservationFacade`, `AdminStopPriceFacade`, `AdminBusinessContractFacade`, **`AdminStopPriceController`(컨트롤러에서 직접 사용)**, reservation `StopResolutionService`, `MemberVerificationService`.
   - 포트 메서드마다 연결할 **제공자 진입점**, 입력 검증, 반환 DTO, 커밋 후 처리를 0단계 대응표에 채운다. 확인된 예:

| 제공자 진입점 | DB Service 외에 하는 일 |
|---|---|
| `InternalContractFacade.createContract` | 회원 재검증(`verifyMemberIfPresent`), 정류소 조회·검증, `ContractResponse` 생성 |
| `InternalContractFacade` 조회 메서드 | 정류소 정보를 일괄 조회해 계약 응답에 결합 |
| `ReservationFacade.updateReservationStatus` | 상태 변경 후 풀린 선점의 Redis 캐시 제거(`stopHoldGate::evict`) |
| `ReservationFacade.completeReservationToContract` | 정류소 재검증, 예약 완료·계약 생성, 선점 캐시 제거 |
4. **제공자 입력 검증 목록화와 이관 (R2)**
   - 삭제 예정 `Internal*Controller`의 메서드마다 `@Valid` 대상 DTO, 문자열→enum·날짜 변환, 보정(`coerceAtLeast`·`coerceIn`), 검색 조건 생성 로직을 표로 정리한다.
   - 도메인 입력 검증은 제공 모듈의 진입 서비스로 옮긴다.
   - **오류 계약을 유지하는 방법을 정한다.** `ExceptionAdvisor`는 MVC의 `MethodArgumentNotValidException`만 400/`00260`으로 처리한다. 서비스 메서드 검증(`@Validated`)이 던지는 `ConstraintViolationException`·`MethodValidationException` 처리기는 없어서, 그대로 두면 catch-all로 500/`00100`이 된다. 둘 중 하나를 고른다.
     - 진입점에서 검증 실패를 `BusinessException(CommonResult.VALIDATION_FAIL, ...)`로 직접 던진다(권장, 범위가 명확함).
     - 입력 검증용 서비스 예외 처리기를 추가한다. 이 경우 반환값 검증 실패 같은 서버 결함까지 400이 되지 않게 파라미터 검증만 대상으로 한다.
   - **enum 변환**: 이 저장소의 `fromName`은 13개 모두 nullable을 반환하므로 잘못된 값에서 예외가 나지 않는다. `null`이면 명시적으로 `VALIDATION_FAIL`을 던진다. 검색 필터에서 잘못된 enum이 `null` 필터로 바뀌어 전체 조회가 되지 않게 테스트한다.
   - 같은 검증을 공개 컨트롤러도 하고 있는지 확인해 중복과 누락을 구분한다.
5. 검증: 각 모듈 `test`, `bootJar`. 이 단계까지는 MSA 그대로 동작해야 한다. 서비스 검증은 실제 서비스 프록시를 통과하는 테스트로 확인한다(단위 테스트에서 직접 생성한 객체는 검증이 실행되지 않는다).

### 2단계: `app` 생성과 보안·설정·로깅 통합

리뷰 R1·R3·R4 반영. 앱을 하나로 띄우는 순간 보안 체인과 설정 충돌이 같이 터지므로 한 단계로 묶는다. 이 단계가 끝나면 gateway 없이 공개 API를 안전하게 받을 수 있어야 한다.

**2-1. 모듈 구성**
1. `settings.gradle.kts`에 `modules:app`을 추가한다.
2. `iam`/`stop`/`reservation`의 `bootJar`를 끄고 `jar`를 켠다(라이브러리화). openapi 플러그인 설정을 `app`으로 옮기면서, 루트 `generateAllOpenApiDocs`가 참조하던 세 모듈의 `:generateOpenApiDocs` 경로도 **같이** `app` 기준으로 바꾼다(늦추면 중간 단계에서 문서 생성 작업이 깨진다).
3. `*Application.kt` 3개를 삭제하고 `app`에 `com.media.bus.BusApplication`을 둔다. 스캔 대상은 `com.media.bus` 하위 전체, `com.media.bus.gateway`는 제외한다.

**2-2. 설정 단일화 (R3)**
- 세 모듈의 `application.yml`·`application-local.yml`을 **라이브러리에서 삭제**하고 `app/src/main/resources`로 합친다. 같은 이름 리소스는 병합되지 않고 클래스패스 첫 번째 것만 쓰이므로, 하나라도 남으면 프로필에 따라 라이브러리 설정이 그대로 로드된다.
- 완료 조건: 라이브러리 JAR 안에 `application*.yml`이 없다(`unzip -l`로 확인). 기본·local 프로필 모두 아래 값으로 기동한다.

| 키 | 값 | 비고 |
|---|---|---|
| `server.port` | 8080 | 프런트가 보던 gateway 포트와 같다 |
| `spring.main.allow-bean-definition-overriding` | **false** | 충돌을 기동 실패로 드러낸다(C4) |
| `spring.datasource.url` | `jdbc:postgresql://.../${DB_NAME}` | 테이블이 스키마를 명시하므로 `?schema=`가 필요 없다 |
| `spring.datasource.hikari.maximum-pool-size` | 운영 20 / local 10 | 3개 풀 60 → 1개 풀 20 |
| `resilience4j.bulkhead.instances.orderDatabase.maxConcurrentCalls` | 운영 19 / local 9 | 프로필별로 "풀 크기 - 1"(C8). 지금 local은 풀 10·퍼밋 10이라 그대로 옮기면 규칙과 다르다. `hig.bulkhead.database-name`과 인스턴스 이름(`orderDatabase`) 일치도 확인 |
| `spring.liquibase.change-log` | `db/changelog/db.changelog-master.xml` (`app`) | iam → stop → reservation 순서로 include. local 프로필도 같은 경로 |
| `management.server.port` / `address` | 8081 / 127.0.0.1 | actuator 공개 포트 분리(S4). 접근 정책은 2-3의 포트별 정책 표 |
| `springdoc.*.enabled` | 운영 프로필 false | S6 |
| `reservation.hold.*`, `seoul.api.*`, `cookie.*`, `jwt.secret` | 기존 값 이관 | |
| `services.*` | 3단계까지 유지(HTTP 구현이 아직 남아 있음) | 루프백을 쓰지 않으면 2단계 앱은 S2S를 호출하는 기능이 동작하지 않는다. 3단계를 바로 이어서 진행한다 |

**2-3. 단일 보안 체인과 JWT 이관 (R1)**
- 세 모듈의 `SecurityConfig`를 **삭제**한다. 이름만 바꿔 함께 등록하면 모두 전체 요청 대상이라 `UnreachableFilterChainException`으로 기동하지 않는다.
- `app`의 보안 설정은 **공개 8080 정책과 관리 8081 정책을 구분**한다. 사용자 정의 `SecurityFilterChain`이 있으면 Spring Boot의 Actuator 보안 자동 구성이 물러나므로 관리 포트 규칙도 직접 적어야 한다. 단일 체인으로 두려면 관리 요청을 공개 API 허용 목록·JWT 검사 대상에서 빼고, 체인을 나누려면 서로 겹치지 않는 matcher와 `@Order`로 순서를 명시한다.

| 요청 | 기대 결과 |
|---|---|
| 컨테이너 안 `127.0.0.1:8081/actuator/health` | 토큰 없이 200 (compose healthcheck용) |
| `127.0.0.1:8081/actuator/loggers` | 정책을 정해서 적용(컨테이너 안에서만 닿으므로 허용하거나, 운영에서는 노출 제외) |
| 공개 8080 `/actuator/**` | 토큰과 무관하게 404 |
| 공개 8080 `/api/v1/internal/**` | 차단. 3단계 이후에는 매핑 자체가 없음 |
| (선택) 로컬 8090 internal 경로 | 유효한 S2S 토큰일 때만 통과. 사용자 Bearer를 요구하지 않음. 기본안에서는 8090을 쓰지 않으므로 이 분기가 필요 없다 |

- iam `SecurityConfig` 안의 `PasswordEncoder` 빈은 iam의 별도 설정(예: `PasswordEncoderConfig`)으로 옮겨 보존한다.
- gateway 기능을 아래처럼 옮긴다.

| gateway 기능 | 이관 방법 |
|---|---|
| `PUBLIC_PATHS` 화이트리스트 | **메서드 + 경로 정확 일치**로 바꾼다(S5). 기존 `startsWith` 비교는 쓰지 않는다. `uri.path`에는 쿼리가 없어 접두 비교가 필요 없다 |
| Bearer 검증 → 401 | 같은 필터에서 `JwtProvider.tryParseClaims` 사용. 응답 본문 없는 401 유지 |
| 관리자 발급처 검사(`/api/v1/admin/**` → `aud=ADMIN` + `memberType.isAdmin`) → 403 | 경로 prefix 기준 판정. 중복 슬래시 정규화 포함 |
| `X-User-*` 헤더 주입 | **폐기.** 필터가 `MemberPrincipal`을 만들어 `request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, ...)`로 바로 넣는다 |
| 요청의 `X-User-*` 헤더 덮어쓰기·제거 | **유지 필수.** 필터가 모든 요청에서 `X-User-*`를 제거하는 요청 래퍼를 씌우고, 컨트롤러 19곳은 `@CurrentMember`로 교체한다(S1) |
| 라우트 허용 목록 | 허용 prefix 외 경로는 404(S3) |
| `block-internal`(403) | internal 매핑은 3단계에서 삭제. 그 전까지는 `S2STokenFilter`를 유지하고 **운영에 배포하지 않는다** |
| `CorsConfig`(관리자 전용 origin 분리) | 서블릿판 `UrlBasedCorsConfigurationSource`로 같은 규칙 이관 |

**보안 필수 조건** (상세는 [외부 노출 표면 점검](#외부-노출-표면-점검))
- `MemberPrincipalExtractFilter` 빈 등록을 **제거**한다(`AuthContractAutoConfiguration.WebMvcBeans`).
- JWT 필터는 Spring Security 필터 체인 **안에** 둔다. `StrictHttpFirewall`이 먼저 `;`, `%2F`, `//`, `..` 경로를 거절해야 공개 경로 판정과 핸들러 매핑의 경로 해석이 어긋나지 않는다(S5).
- gateway 테스트 2개는 MockMvc 기반 필터 테스트로 옮긴다.

**2-4. 로그의 회원 ID (R4)**
- `MdcLoggingFilter`(`HIGHEST_PRECEDENCE + 200`, Spring Security보다 먼저 실행)에서 `X-User-Id` 읽기를 제거한다. 요청 ID는 지금처럼 이 필터에서 만든다.
- 회원 ID는 JWT 필터가 인증에 성공한 뒤 검증된 `MemberPrincipal`로 MDC에 **넣기만** 한다. **지우는 것은 지금처럼 가장 바깥 `MdcLoggingFilter`의 `finally`가 맡는다.** 필터 순서가 `MdcLoggingFilter(+200)` → `AccessLoggingFilter(+300)` → Spring Security(JWT) 이고, `ACCESS` 로그는 `AccessLoggingFilter`의 `finally`에서 남는다. JWT 필터가 자기 `finally`에서 지우면 `ACCESS` 로그에 회원 ID가 빠진다.
- 인증 전에 찍히는 `START` 로그에는 회원 ID가 없는 것이 정상이다.
- `AsyncMdcAspect`의 MDC 전파도 같이 확인한다.

**완료 조건**
- `./gradlew clean build`, `app` `bootJar`, 실제 앱 컨텍스트 기동(기본·local 프로필)
- 로그인·토큰 갱신·관리자 로그인·CORS(일반·관리자 origin)·공개 경로 회귀
- "토큰 없이 `X-User-*`만 보내 보호 API 호출 → 401"
- "본인 토큰 + 다른 회원 `X-User-Id` 헤더로 `/api/v1/auth/logout`·`/api/v1/member/modify` 호출 → 본인에게만 적용, `ACCESS` 로그에도 토큰의 회원 ID만 남음"
- 인증 실패 요청과 그다음 요청의 로그에 이전 회원 ID가 남지 않음
- `/actuator/**`, `/swagger-ui/**`, `/api-docs`가 8080에서 404, 컨테이너 안 8081 health는 200
- 기본·local 프로필의 실제 풀 크기·퍼밋 값 확인

### 3단계: 어댑터 연결, S2S HTTP와 internal 매핑 제거

1단계에서 추출한 포트에 `app`의 어댑터를 연결한다. 작은 것부터 진행한다: reservation→iam(1) → reservation→stop(3) → iam→reservation(10) → iam→stop(12).

**3-1. 쌍마다 하는 일**
1. `app`에 어댑터를 만들어 0단계 대응표의 **제공자 진입점(Service 또는 Facade)**을 호출하고 소비자 DTO로 변환한다. 제공자의 Repository·Entity는 직접 쓰지 않는다(AGENTS.md 모듈 경계 규칙).
2. 1단계에서 이관한 입력 검증이 제공자 진입점에서 실행되는지 확인한다(R2).
3. 소비자의 HTTP 구현과 `*Api`(`@HttpExchange`)를 삭제한다. `ServiceClientConfig`는 한 클래스에 두 제공자 프록시 빈이 같이 있으므로(iam: `stopApi`·`reservationApi`, reservation: `stopApi`·`iamApi`) **해당 `@Bean` 메서드만** 지우고, 클래스는 그 소비자의 마지막 쌍을 전환할 때 지운다.

**3-2. internal 매핑 삭제는 마지막에**
- 제공자 매핑은 쌍마다 지우지 않는다. `GET /api/v1/internal/stop`과 `POST /api/v1/internal/stop/bulk`는 reservation과 iam이 같이 쓰므로, reservation→stop 전환 직후 지우면 아직 HTTP를 쓰는 iam 호출이 깨진다.
- 네 쌍의 어댑터 연결이 모두 끝난 뒤 `Internal*Controller`를 일괄 삭제한다. 호출자 없는 매핑 4개(`/internal/iam/member/jwt`·`/login-id`·`/email`, `/internal/iam/commission/effective-rate`)도 저장소 밖 호출자가 없음을 확인하고 함께 폐기한다.

**3-3. S2S 의존성 정리**

아래를 한 작업으로 묶어 지운다. 일부만 지우면 `common`이나 `gateway` 컴파일이 깨진다.

| 삭제 대상 | 같이 정리할 참조 |
|---|---|
| `S2STokenFilter` | `app` 보안 설정의 internal 분기 |
| `S2SRestClientFactory` | `RestClientConfig`의 import·팩토리 빈, `S2SRestClientFactoryErrorTest` |
| `JwtProvider.generateS2SToken` | `JwtProvider : TokenProvider` 구현 선언, `common`의 `TokenProvider` 인터페이스 |
| `RestClientConfig`(`internalRestClient` 포함) | 주입 사용처 없음을 확인했으므로 폐기. `AutoConfiguration.imports` 등록, `RestClientConfigTest`, 테스트의 `TokenProvider` mock(`AsyncMdcAspectTest`, `TransactionalBulkheadAspectTest`, `IamApiContractTest`) |
| `gateway` 모듈 | `settings.gradle.kts`의 include, 모듈 소스와 테스트(`JwtAuthenticationFilterTest`는 2단계에서 MockMvc 테스트로 이관 완료 전제), gateway 전용 Spring Cloud BOM |
| `services.*` 설정 | `app` `application*.yml`, `.env.example` |

- 컴포넌트 스캔에서 `gateway`를 빼는 것(2단계)과 Gradle 빌드에서 빼는 것(여기)은 별개다.
- `stop`의 `SeoulBusApiClient`(외부 공공 API)는 S2S 코드가 아니므로 유지한다.

**동작이 바뀌는 지점 (검토 필수)**

| 항목 | 지금 | 교체 후 | 대응 |
|---|---|---|---|
| 입력 검증 | 제공자 컨트롤러의 `@Valid`, 파라미터 변환, `coerceIn` 보정이 실행됨 | 직접 호출에서는 실행되지 않음 | 1단계에서 진입 서비스로 이관(R2) |
| 트랜잭션 | 호출하는 파사드에 `@Transactional`이 없어, 원격 호출과 각 Service의 DB 작업이 별도 트랜잭션 | 같음. 파사드가 트랜잭션 밖에서 제공자 Service를 부르고, 제공자 Service가 자기 `@Transactional`을 연다 | 호출자 트랜잭션이 있고 전파가 `REQUIRED`일 때만 합류한다. 어댑터에 `@Transactional`을 일괄로 붙이지 않는다. 호출 경로별로 경계를 확인하고 필요할 때만 `REQUIRES_NEW` |
| 예외 | 제공자 4xx → `translateClientError` → `BusinessException`(같은 상태). 단 401·403은 `ServiceException`(500, S2S 인증 실패)으로 바뀜 | 제공자의 `BusinessException`이 그대로 전파. S2S 인증 단계가 없어지므로 401·403 변환은 재현할 필요 없음 | 응답 상태·본문 코드가 같은지 회귀 테스트로 확인 |
| 과부하(Bulkhead) | stop이 `BulkheadFullException` → 429/`00270` → 소비자가 `BusinessException`으로 받음 → `ReservationFacade.reserveOne`의 `catch (BaseException)`에서 항목 `errorCode=00270` | `BulkheadFullException`(`BaseException` 아님)이 그대로 올라와 `catch (Exception)`에서 항목 `errorCode=00100` | 포트 경계(어댑터)나 소비자에서 `BulkheadFullException`을 `BusinessException(CommonResult.BULKHEAD_FULL)`로 변환. 예약은 항목별 결과에 실패를 담으므로 `ExceptionAdvisor` 수정만으로는 해결되지 않음 |
| Bulkhead 공유 | 서비스마다 퍼밋 19를 따로 가짐 | 한 JVM에서 모든 모듈이 같은 `orderDatabase` 퍼밋을 나눠 씀 | 모듈별 자원 격리가 아님을 전제로 부하 검증. 같은 흐름의 중첩 호출은 퍼밋을 더 얻지 않지만 `REQUIRES_NEW`(감사 로그 등)는 커넥션을 하나 더 쓰므로 함께 확인 |
| 직렬화 | JSON 왕복으로 DTO 간 필드가 느슨하게 매핑됨(없는 필드 무시) | 어댑터에서 명시적으로 변환 | 컴파일러가 누락을 잡는다 |
| 선점 게이트 | `gate-ttl: 5s`가 S2S 왕복 + DB 저장을 감쌈 | 왕복이 사라져 여유가 생김 | 값은 그대로 둔다 |

모두 끝나면 `S2STokenFilter`, `S2SRestClientFactory`, `JwtProvider.generateS2SToken`, `services.*` 설정을 삭제한다.

**완료 조건**
- `/api/v1/internal/**` 매핑 0개(아키텍처 테스트로 고정). 포트 연결 완료와 별도로 검사한다
- 빈 계약명, 0·음수 금액, 길이 초과, 잘못된 enum·날짜, 페이지 범위 초과, **제공자 과부하**에 대해 기존 HTTP 상태·본문 코드·예약 항목별 코드·보정 규칙 유지
- 잘못된 회원·정류소로 계약 생성 시 거절, 관리자 목록의 정류소 정보 유지, 상태 변경·계약 완료 후 선점 캐시 해제(Facade 업무 흐름 보존)
- main·test·`AutoConfiguration.imports`에 3-3에서 지운 클래스·메서드 참조 없음. `./gradlew clean build`와 `:modules:app:bootJar` 통과
- `iam`/`stop`/`reservation` 사이 Gradle 의존 없음

### 4단계: 배포 구성·메모리 제한·복구 정책

| 파일 | 변경 |
|---|---|
| `Dockerfile` | `MODULE_NAME=app` 고정 |
| `docker-compose.yml` | `database`, `valkey`, `app`(8080), 필요하면 `caddy`(80/443). 서비스 4개 블록과 `*_SERVICE_URL` 삭제. `app`에 `mem_limit` 지정. `build:` 대신 태그가 있는 `image:`로 실행해 롤백 대상을 정확히 가리킬 수 있게 한다. 볼륨 이름(`postgres_data`, `valkey_data`)과 compose 프로젝트 이름은 **바꾸지 않는다**(바꾸면 새 빈 볼륨이 생긴다) |
| `docker-compose-local.yml`, `docker-compose-dev.yml` | 같은 토폴로지로 단순화. dev의 `host.docker.internal` 라우팅 override는 불필요 |
| `.env.example` | `*_SERVICE_URL` 삭제, `CORS_*` 유지 |
| `build.gradle.kts` | (2단계에서 `generateAllOpenApiDocs` 이관 완료) springdoc group으로 iam/stop/reservation 문서 분리 여부 결정 |
| CI(GitLab) | 빌드 대상 이미지 4개 → 1개 |

**메모리 (R5)**
- 컨테이너 제한 없이 `MaxRAMPercentage`만 주면 JVM이 호스트 메모리 기준으로 힙을 잡는다(4GB 호스트에서 60%면 약 2.4GB).
- `app`에 `mem_limit`(시작점 1g)을 걸고 `-Xmx`(시작점 640m)를 명시한다. 메타스페이스·코드 캐시·스레드 스택 등 힙 밖 메모리가 남도록 제한과 힙 사이에 여유를 둔다.
- Serial GC 적용 여부는 측정으로 정한다.
- 완료 조건: **실행 중인 앱 프로세스**를 대상으로 `jcmd <pid> VM.flags -all | grep MaxHeapSize`로 실제 상한 확인(`java -XX:+PrintFlagsFinal -version`은 새 JVM을 띄우므로 앱 실행 인수로 준 `-Xmx`를 보지 못한다). 유휴·일반 요청·정류소 일괄 등록 중 RSS와 지연 측정. DB·Valkey 여유 확보.

**복구 정책 (R6)**
- `restart: unless-stopped`는 **프로세스가 종료됐을 때만** 재시작한다. healthcheck가 `unhealthy`여도 프로세스가 살아 있으면 재시작하지 않는다.
- 응답 불능(행) 상태 대응을 별도로 정한다: 외부 감시(예: 업타임 모니터 + 알림)와 수동 복구 절차, 또는 unhealthy 컨테이너를 재시작하는 감시 도구 도입 중 하나.
- 완료 조건: 프로세스 강제 종료와 응답 불능을 각각 재현해 알림·복구 동작을 확인한다.

### 5단계: 리허설과 운영 전환·롤백

**리허설 (운영 DB 사본)**
1. 운영 DB·Valkey 사본에 새 이미지를 띄워 Liquibase가 changeset을 하나도 실행하지 않는지, 체크섬 오류가 없는지 확인한다.
2. 이전 이미지와 compose로 되돌려 DB·Valkey 데이터와 로그인 세션, 예약·계약을 정상 사용하는지 확인한다. 볼륨이 교체되거나 새로 생기지 않았는지도 확인한다.

**운영 전환**
1. 0단계 백업을 다시 한 번 받는다.
2. 기존 compose를 내리고 새 compose를 올린다. DB 스키마는 바뀌지 않는다.
3. 회귀 시나리오를 운영에서 확인한다. `JWT_SECRET`과 Valkey 데이터가 같으므로 기존 로그인 세션(refresh 토큰)은 유지된다.
4. 기준선과 비교해 RSS와 기동 시간을 기록한다.

**롤백**: 이전 이미지 태그로 기존 compose를 다시 올린다. 전환 기간에는 **새 Liquibase changeset을 추가하지 않는다.** 그래야 DB가 양쪽 버전과 호환된다.

롤백이 "이전 compose 재기동"으로 끝나려면 아래 전제가 맞아야 한다. 0단계에서 기록한 값으로 확인한다.
- 같은 compose 프로젝트 이름(`-p`)과 같은 실제 볼륨(`<project>_postgres_data`, `<project>_valkey_data`)을 쓴다. 새 볼륨이 생성되지 않아야 한다.
- 이전 버전 이미지가 태그나 ID로 남아 있다. 지금 compose는 `build:`로 정의돼 있어, 소스로 다시 빌드하면 "이전 이미지"가 아니다. 전환 전에 현재 실행 이미지에 태그를 붙여 둔다.
- 같은 `.env`(특히 `JWT_SECRET`)를 쓴다.

### 6단계: 문서와 지침 갱신

| 파일 | 변경 |
|---|---|
| `AGENTS.md` | 개요(MSA → 모듈러 모놀리스), 모듈 표(포트 삭제, `app` 추가, `gateway` 삭제), 라우팅·S2S 규약을 포트·어댑터 규약으로 교체, 빌드 명령 |
| `modules/*/AGENTS.md` | `gateway` 삭제, `app` 신규, 각 모듈의 S2S 클라이언트 설명을 포트로 교체 |
| `docs/infra/docker-compose-deployment.md` | 구성표를 `app` 하나로 |
| `docs/frontend-onboarding.md` | 진입 포트(8080)와 경로가 같으므로 변경점만 확인 |

## 외부 노출 표면 점검

gateway는 **허용 목록**으로 동작했다. 정의된 prefix만 하위 서비스로 보내고, 그 외 경로(`/actuator/**`, `/swagger-ui/**`, `/api-docs`, `/api/v1/internal/**`)는 외부에서 닿지 않았다. 또 `X-User-*` 헤더를 검증한 값으로 덮어써서 하위 서비스가 헤더를 믿어도 안전했다. 모놀리스는 앱에 있는 모든 핸들러가 8080에 그대로 열리므로, 이 두 성질을 앱 안에서 다시 만들어야 한다.

| # | 심각도 | 문제 | 근거 | 대응 | 단계 |
|---|---|---|---|---|---|
| S1 | **치명** | 컨트롤러가 `X-User-Id` 헤더를 직접 읽는다. 앱이 헤더를 지우지 않으면 본인 토큰으로 인증한 뒤 다른 회원 ID를 헤더에 넣어 로그아웃·세션 삭제·비밀번호 변경·회원정보 수정·탈퇴를 할 수 있다 | `@RequestHeader("X-User-Id")` 19곳: `AuthController` 6(logout, sessions, password/change 등), `MemberController` 4(me, modify, withdraw, deactivate), `AdminStopPriceController` 4, `AdminMemberController` 2, `AdminManagerCommissionController` 2, `AdminAuthController` 1 | ① 19곳을 `@CurrentMember`로 교체 ② JWT 필터가 모든 요청에서 `X-User-*`를 제거하는 래퍼 적용(이중 방어) ③ 아키텍처 테스트로 `@RequestHeader("X-User-…")` 사용 금지 | 2 |
| S2 | **치명** | `MemberPrincipalExtractFilter`가 헤더로 `MemberPrincipal`을 만든다 | `auth-contract/.../MemberPrincipalExtractFilter.kt` | 빈 등록 제거, JWT 필터가 principal 직접 생성 | 2 |
| S3 | 높음 | 허용 목록이 사라져 내부용 경로가 열린다 | gateway 라우트는 `/api/v1/{auth,member,admin,stop,reservation,contract}/**`만 전달 | JWT 필터에 같은 prefix 허용 목록, 그 외 404. Caddy에서도 같은 목록만 프록시 | 2 |
| S4 | 높음 | `/actuator/loggers`가 공개 포트에서 POST 가능해진다. 외부에서 로그 레벨을 바꿔 요청·응답 본문을 로그에 남기게 하거나 디스크를 채울 수 있다. `AccessLoggingFilter`가 `password`·`token`·`accessToken`·`refreshToken` 필드는 마스킹하지만, 그 밖의 개인정보(이름·전화번호 등)와 다른 로거의 DEBUG 출력은 보호되지 않는다 | 각 서비스 `management.endpoints.web.exposure.include: health, loggers`, `access: unrestricted`. 지금은 서비스가 내부망 전용이라 닿지 않음 | `management.server.port: 8081`, `management.server.address: 127.0.0.1`로 분리. compose healthcheck도 컨테이너 안에서 8081 호출 | 2 |
| S5 | 중간 | 공개 경로 판정이 `"METHOD:path".startsWith(...)` 접두 비교다. 지금은 우연히 겹치는 엔드포인트가 없지만, `/api/v1/auth/login-history` 같은 경로가 추가되면 자동으로 공개된다 | gateway `JwtAuthenticationFilter.isPublicPath`. 주석의 "query parameter 때문"은 이유가 되지 않는다(`uri.path`에는 쿼리가 없다) | 정확히 일치(method + path)로 바꾸고 `PathPatternParser`로 판정. 공개 목록에 없는 핸들러는 모두 토큰 필요 | 2 |
| S6 | 중간 | Swagger UI와 `/api-docs`가 공개되어 전체 API 구조(관리자·internal 포함)가 드러난다 | 각 서비스 `springdoc.*` 설정. 지금은 내부망 전용 | 운영 프로필에서 `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false`. 필요하면 관리 포트로 | 2 |
| S7 | 중간 | internal API가 공개 포트에 함께 뜬다 | 아래 상세 | internal 매핑 0개를 운영 배포 게이트로 | 3 |
| S8 | 낮음 | 세션 기기 정보의 클라이언트 IP를 `X-Forwarded-For` 첫 값으로 기록해 위조 가능 | `AuthCookieSupport.extractClientIp` | `server.forward-headers-strategy: native` + Caddy의 `trusted_proxies` 설정 후 `request.remoteAddr` 사용 | 4 |

### 이번 전환과 별개로 이미 있는 위험 (참고)

- `GET /api/v1/member/find/me`는 공개 경로인데, 이름·전화번호·이메일을 받아 `MemberResponse`를 돌려준다. 토큰 없이 회원 정보를 조회하거나 존재 여부를 확인할 수 있으니 응답 필드를 확인하고 마스킹이나 rate limit을 검토한다.
- 로그인, 비밀번호 재설정 요청에 rate limit이 없다. gateway에도 없었으므로 이번 전환에서 바뀌지는 않지만, Caddy나 Bucket4j로 추가하기 좋은 시점이다.

### internal API 상세

**CORS로는 막을 수 없다.** CORS는 브라우저가 다른 origin의 응답을 읽지 못하게 하는 장치일 뿐이라, `curl`·Postman·서버 간 호출은 CORS와 무관하게 요청을 보내고 응답을 받는다.

### 지금(MSA)의 방어

| 층 | 장치 | 위치 |
|---|---|---|
| 1 | gateway `block-internal` 라우트가 `/api/v1/internal/**` 외부 요청을 403 | `gateway/application.yml` |
| 2 | 하위 서비스가 내부망 전용(`expose`)이라 외부에서 직접 접근 불가 | `docker-compose.yml` |
| 3 | `S2STokenFilter`가 `X-Service-Token`(`type=s2s` JWT) 검증 | `auth-contract/.../S2STokenFilter.kt` |

모놀리스로 합치면 1·2층이 사라지고, internal API가 공개 API와 같은 8080 포트에 뜬다. 3층 하나만 남는데, S2S 토큰이 사용자 토큰과 같은 `JWT_SECRET`으로 서명되고 `type` 클레임으로만 구분되므로 단일 방어로 두기에는 약하다.

### 원칙: internal API를 없앤다

프로세스 안에서 호출하면 HTTP 엔드포인트 자체가 필요 없다. 3단계에서 `Internal*Controller`를 삭제하면 노출될 표면이 0이 된다.

- **운영 배포 게이트**: `/api/v1/internal/**` 매핑이 0개일 때만 운영에 배포한다.
- **회귀 방지 테스트**: `app`에 `RequestMappingHandlerMapping`의 전체 매핑을 조회해 `/api/v1/internal/` 접두 경로가 없으면 통과하는 테스트를 둔다. 누가 나중에 internal 컨트롤러를 다시 추가하면 빌드가 실패한다.

### 임시 방어 (루프백을 로컬에서 쓸 때만)

기본 계획은 2단계와 3단계를 연달아 진행해 루프백을 두지 않는다. 2단계 앱에서 S2S 기능을 로컬로 확인해야 할 때만 아래를 적용하고, 이 상태로는 운영에 배포하지 않는다.

| 층 | 장치 | 이유 |
|---|---|---|
| A | Tomcat 추가 커넥터 `127.0.0.1:8090`을 열고, 필터에서 `request.localPort != 8090`인 `/api/v1/internal/**` 요청은 404 | `localPort`는 요청을 받은 커넥터 포트라 헤더처럼 위조할 수 없다. 루프백 클라이언트는 `localhost:8090`을 부른다(8081은 actuator 관리 포트) |
| B | `S2STokenFilter` 유지 | 커넥터 분리가 잘못 설정돼도 토큰 없이는 통과 못 함 |
| C | 리버스 프록시(Caddy)에서 `/api/v1/internal/*` 404 | 앱 앞단에서 한 번 더 차단 |

`X-Forwarded-For`나 `remoteAddr`로 "내부 요청인지" 판단하지 않는다. 프록시 뒤에서는 `remoteAddr`가 프록시 IP가 되고, 헤더는 위조할 수 있다.

### 남는 공개 API의 인가

internal API를 없애도 관리자 API(`/api/v1/admin/**`)는 외부에 열려 있고, 이 API가 다른 모듈 기능을 위임 호출한다.

- **확인한 현황**: `iam` 관리자 컨트롤러 7개(메서드 41개)는 전부 클래스 레벨 `@Authorize`가 붙어 있다.
- **2단계 JWT 필터**가 `/api/v1/admin/**`에 `aud=ADMIN` + `memberType.isAdmin`을 계속 강제한다(gateway와 같은 2중 방어).
- **회귀 방지 테스트**: `/api/v1/admin/**`에 매핑된 핸들러가 클래스나 메서드에 `@Authorize`를 갖지 않으면 실패하는 테스트를 `app`에 둔다.

## 모듈 경계를 지키는 장치

- **컴파일 경계**: `iam`/`stop`/`reservation`은 서로 `project(...)` 의존을 두지 않는다. 어댑터는 `app`에만 둔다.
- **아키텍처 테스트(선택)**: `app`에 ArchUnit 테스트를 두어 `com.media.bus.reservation..`이 `com.media.bus.stop..`을 참조하지 않는지, 어댑터가 제공자의 `..repository..`와 Exposed Table·Entity 클래스를 참조하지 않는지 검사한다. 단, 요청 DTO가 쓰는 enum(`PaymentCycle`, `PaymentMethod`, `ContractStatus` 등)이 `..entity.enums..` 아래에 있으므로, 이 패키지는 예외로 두거나 enum을 계약용 패키지로 옮긴다.
- **스키마 경계**: 다른 모듈 스키마의 테이블을 조인하지 않는다. 필요하면 포트로 조회한다.

## 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| `MemberPrincipalExtractFilter`가 남아 헤더 위조 | 인증 우회 | 2단계 보안 필수 조건 + 보안 회귀 테스트 |
| 컨트롤러의 `X-User-Id` 헤더 직접 신뢰(S1) | 다른 회원 대상 로그아웃·비밀번호 변경·탈퇴 | `@CurrentMember` 교체 + 헤더 제거 래퍼 + 아키텍처 테스트 |
| actuator·swagger 공개(S4, S6) | 로그 레벨 조작, API 구조 노출 | 관리 포트 `127.0.0.1:8081` 분리, 운영 swagger 끔 |
| internal API가 공개 포트에 노출 | S2S 토큰 하나만 남은 상태로 내부 기능이 외부에 열림 | 3단계 완료(internal 매핑 0개)를 운영 배포 게이트로. 2·3단계 사이 운영 배포 금지 |
| `logicalFilePath` 불일치 | 기동 실패 또는 changeset 재실행 | 0단계에서 실제 `filename` 확인, 운영 DB 사본으로 먼저 기동 |
| 빈 덮어쓰기 | 엉뚱한 빈이 주입됨 | `allow-bean-definition-overriding: false` |
| 트랜잭션 경계 변화 | 호출자 트랜잭션이 있는 경로에서 제공자 변경이 같이 롤백될 수 있음. 지금 S2S 호출 파사드에는 `@Transactional`이 없어 영향은 낮음 | 3단계에서 호출 경로별 확인, 어댑터에 트랜잭션 일괄 추가 금지, 필요 시에만 `REQUIRES_NEW` |
| 입력 검증 유실(R2) | 빈 계약명·음수 금액 등이 서비스까지 전달, 잘못된 enum이 500 | 1단계 검증 목록화·이관, 3단계 완료 조건 |
| 오류 코드 변화(V2-04) | 서비스 검증 실패가 500/`00100`, 과부하가 예약 항목 `00100`으로 바뀜 | 진입점에서 `BusinessException`으로 변환, 어댑터에서 `BulkheadFullException` 변환 |
| Facade 우회(V2-01) | 회원 재검증·정류소 결합·선점 캐시 해제 누락 | 어댑터는 0단계 대응표의 제공자 진입점만 호출 |
| 공용 internal 매핑 조기 삭제(V2-02) | 아직 HTTP를 쓰는 다른 소비자 호출 실패 | 네 쌍 연결 후 일괄 삭제 |
| 롤백 시 새 볼륨 생성 | 빈 DB로 기동 | 프로젝트 이름·볼륨 이름 고정, 이전 이미지 태그 보존 |
| 하나의 장애가 전체로 번짐 | 한 모듈의 메모리 누수·스레드 고갈이 전체 API를 멈춤 | bulkhead 유지, `mem_limit`·`-Xmx`로 힙 상한 지정. `restart: unless-stopped`는 프로세스 종료만 복구하므로 응답 불능은 외부 감시·알림으로 대응(4단계, R6) |
| 단일 서버 장애 | 전체 중단 | EBS 스냅샷 정책, `pg_dump` S3 백업, 복구 절차 문서화(`docs/infra/postgres-backup-plan.md`) |

## 확인이 필요한 것

- [ ] 운영 DB의 `databasechangelog` 위치와 `filename` 값 (0단계 쿼리)
- [ ] `RestClientConfig.internalRestClient`(유저 JWT 전파)를 실제로 쓰는 곳이 있는지. 지금 확인한 바로는 주석 외 사용처가 없어 정리 대상이다
- [ ] 호출 경로별 트랜잭션 경계 (3단계)
- [ ] 호출자 없는 internal 매핑 4개의 저장소 밖 호출자 유무 (3단계 폐기 전)
- [ ] 관리 포트 8081의 `loggers` 노출 정책 (2단계)
- [ ] 프런트(`mbus-front`)가 gateway 고유 동작(예: 401 응답 본문 없음)에 의존하는지
- [ ] `/api/v1/member/find/me` 응답에 어떤 회원 정보가 담기는지(공개 엔드포인트)
- [ ] 3단계 이후 보안 검증은 `security-review`나 별도 리뷰어로 한 번 더 받는다(작성자 자기 검증만으로 끝내지 않음)
