# 예약 시스템 개발 프로젝트

> **`Java 25 (LTS)`** · **`Spring Boot 4.1.2`** · **`Kotlin 2.3.20 (K2)`** 기반 **모듈러 모놀리스(Modular Monolith)**

## 시스템 아키텍처 (모듈러 모놀리스)

애플리케이션 하나(`app`, JVM 1개, 포트 8080)가 도메인 모듈 세 개를 함께 실행합니다. 도메인 모듈은 Gradle 모듈로 분리되어 있고 **서로 컴파일 의존하지 않습니다.** 모듈 사이의 호출은 소비 모듈이 정의한 **포트(인터페이스)** 를 `app`의 **어댑터**가 구현하는 방식으로만 연결됩니다.

> 2026-11 MSA(Spring Cloud Gateway + 서비스 3개, HTTP S2S 호출)에서 전환했습니다. 트래픽 대비 JVM 4개의 메모리 비용, S2S 부분 실패·오류 변환 관리, 배포 순서 맞추기 부담을 줄이기 위해서입니다. 스키마 분리·모듈 경계·URL 계약은 그대로 두어 다시 쪼갤 때 어댑터만 HTTP 구현으로 바꾸면 되게 했습니다. 배경과 단계: [`docs/modular-monolith-migration-plan.md`](docs/modular-monolith-migration-plan.md), 모듈 간 호출 대응표: [`docs/modular-monolith-s2s-mapping.md`](docs/modular-monolith-s2s-mapping.md)

| 모듈 | 종류 | 역할 |
|------|------|------|
| **app** | 실행 | 진입점(`BusApplication`), 단일 보안 필터 체인·JWT 인증 필터, 통합 설정(`application*.yml`), Liquibase 루트 master, 모듈 간 포트 어댑터 |
| **iam** | 도메인 | JWT 발급·갱신, 인증·인가, 회원 라이프사이클(가입·조회·수정·제재·탈퇴), 관리자 API |
| **stop** | 도메인 | 정류소·단가 관리, 서울 열린데이터광장 일괄 등록, 지도 조회 |
| **reservation** | 도메인 | 예약 생성·변경·취소, 정류소 광고면 선점, 계약 |
| **auth-contract** | 라이브러리 | `JwtProvider`, `MemberPrincipal`, `@Authorize`·`@CurrentMember` |
| **common** | 라이브러리 | `ApiResponse`, 글로벌 예외 처리, Exposed 기반 엔티티, Bulkhead, 로깅, 진입점 입력 검증 |

```
                     ┌──────────────────────── app (8080) ────────────────────────┐
 Client ──HTTPS──▶   │ SecurityFilterChain → JwtAuthenticationFilter → Controller │
                     │                                                            │
                     │   iam  ◀──port──  adapter  ──port──▶  stop                 │
                     │   reservation ──port──▶ adapter ──▶ stop / iam            │
                     └───────────────┬───────────────────────────┬────────────────┘
                                     │ DataSource 1개             │
                          PostgreSQL (schema: auth·audit·stop·reservation)   Valkey
```

의존 방향:

```
app ──► iam, stop, reservation ──► auth-contract ──► common
iam ✕ stop, iam ✕ reservation, reservation ✕ stop   (서로 컴파일 의존 금지 — ModuleBoundaryTest)
```

## 기술 스택 요약

> Kotlin은 언어, JDK 25는 컴파일 타깃 + 런타임입니다. 두 축을 분리해서 선정 근거를 기록합니다.

| 레이어 | 선택 | 선정 이유 |
|--------|------|-----------|
| **언어** | Kotlin 2.3.20 (K2 컴파일러) | Null 안전성, `data class`, `sealed interface`, JDK API 직접 호출 가능 |
| **런타임/타깃** | JDK 25 (LTS, 2025-09) | Virtual Thread 핀닝 해소(JEP 491, JDK 24) + `ScopedValue` 정식 표준화(JEP 506, JDK 25) |
| **프레임워크** | Spring Boot 4.1.2 (Spring Framework 7) | Boot 4 BOM 통합, 최신 Auto-Configuration |
| **웹** | Spring MVC (Tomcat) + Virtual Thread | 블로킹 코드 그대로 높은 동시성 |
| **ORM** | Exposed 1.6.1 (DAO + DSL) | Kotlin 친화적 type-safe SQL, JPA 대비 가벼움 |
| **DB** | PostgreSQL 18.3 | DataSource 1개, 모듈별 schema 격리 (`auth`·`audit` / `stop` / `reservation`) |
| **캐시/세션** | Valkey 8.1.6 (Redis 호환) | Redis BSL 라이선스 회피, BSD-3-Clause |
| **인증** | JWT (jjwt 0.13.0, HMAC-SHA) | 발급·검증이 한 애플리케이션 안에 있어 대칭키로 충분 |
| **회복성** | Resilience4j 2.4.0 (Bulkhead, Semaphore) | DB connection pool 고갈 사전 차단 |
| **로깅** | Log4j2 + LMAX Disruptor 4.0 | 비동기 락프리 로깅, Virtual Thread 핀닝 회피 |
| **API 문서** | springdoc-openapi 3.2.0 | 통합 OpenAPI 3 명세 + Swagger UI |
| **마이그레이션** | Liquibase | 모듈별 changelog를 루트 master가 include |
| **테스트** | JUnit 5 + MockK 1.13.10 | Kotlin 친화적 Mock |
| **빌드/배포** | Gradle 9.8.2 (Kotlin DSL) + Docker BuildKit | 멀티 스테이지 + `--mount=type=cache` 캐시 마운트, 실행 이미지 1개 |

## 빌드와 실행

```bash
./gradlew clean build                                          # 전체 빌드 + 테스트
./gradlew :modules:app:bootJar                                 # modules/app/build/libs/app.jar
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'
```

로컬에서 앱을 직접 실행할 때는 인프라만 컨테이너로 올립니다. 비밀값(`JWT_SECRET` 등)은 프로젝트 루트 `.env`에 둡니다(`.env.example` 참고, git 제외).

```bash
docker compose -f docker-compose-local.yml up -d database valkey
```

## API 문서 (Swagger UI)

> 통합 Swagger UI 하나를 제공합니다. **`local` 프로필에서만** 열리고, 기본(운영) 프로필에서는 404입니다.

| 대상 | Swagger UI | OpenAPI JSON |
|------|------------|--------------|
| 전체 API | http://localhost:8080/swagger-ui.html | http://localhost:8080/api-docs |

PostgreSQL·Redis 없이 명세 파일을 생성하려면 다음 태스크를 실행합니다. 앱을 `openapi` 프로필(H2 메모리 DB, `127.0.0.1:8090`)로 띄워 내려받습니다.

```bash
./gradlew generateAllOpenApiDocs   # → modules/app/build/docs/openapi/openapi.yaml
```

- 모듈이 합쳐지면서 같은 이름의 DTO가 한 명세에 섞이지 않도록 고유 스키마 이름을 붙였습니다(`AdminStopBoundsResponse` / `StopMapBoundsResponse` 등). `WebSurfaceArchitectureTest`가 스키마 이름 중복을 막습니다.

---

## Docker Compose 배포 및 로컬 통합 테스트

단일 서버 배포, 메모리 제한, 로컬 실행, 프론트엔드 확장 기준은 [Docker Compose 단일 서버 배포 가이드](docs/infra/docker-compose-deployment.md)를 참고합니다.

```bash
docker compose -f docker-compose-local.yml up --build -d   # 로컬 전체 스택
docker compose up --build -d                               # 운영 단일 서버
docker compose up -d --build --no-deps app                 # 앱만 재배포
```

### 구성 요약

| 컴포넌트 | 버전 | 포트 | 용도 |
|----------|------|------|------|
| app | — | 8080 (Actuator는 컨테이너 안 `127.0.0.1:8081`) | 전체 API. 운영 compose는 `mem_limit: 1g`, `-Xmx640m` |
| PostgreSQL | 18.3 | 15433 (로컬만 노출) | 주 데이터베이스 (모듈별 schema 격리) |
| Valkey (Redis 호환) | 8.1.6 | 6379 (로컬만 노출) | 캐시 / Refresh Token / 분산 락 |

---

## 모듈 간 호출 (포트 · 어댑터)

모듈 사이에는 HTTP 호출이 없습니다. 같은 프로세스 안에서 포트 인터페이스를 통해 호출합니다.

| 포트 (소비 모듈) | 어댑터 (`app`) | 호출하는 제공 모듈 진입점 |
|------------------|----------------|---------------------------|
| `reservation…StopLookupPort` | `ReservationStopLookupAdapter` | `StopService` |
| `reservation…MemberLookupPort` | `ReservationMemberLookupAdapter` | `MemberService` |
| `iam.port.StopAdminPort` | `IamStopAdminAdapter` | `StopService`, `StopPriceService`, `StopMapQueryService`, `StopRegisterService` |
| `iam.port.ReservationAdminPort` | `IamReservationAdminAdapter` | `InternalContractFacade`, `ReservationFacade` |

```kotlin
// reservation 모듈 (소비자) — stop 을 모른다
interface StopLookupPort {
    fun getStopsByPks(pks: Collection<UUID>): List<StopInfo>
}

// app 모듈 (어댑터) — stop 의 공개 진입점을 호출하고 reservation DTO 로 변환한다
@Component
class ReservationStopLookupAdapter(private val stopService: StopService) : StopLookupPort {
    override fun getStopsByPks(pks: Collection<UUID>): List<StopInfo> {
        if (pks.isEmpty()) return emptyList()
        return callProvider { stopService.getBusStopsByIds(pks).map(::toStopInfo) }
    }
}
```

**규칙**
- 어댑터는 제공 모듈의 **공개 진입점(Service·Facade)** 만 부릅니다. Facade가 있으면 Facade를 불러 회원 재검증·정류소 결합·선점 캐시 해제 같은 업무 흐름을 건너뛰지 않습니다.
- 어댑터는 Repository·Exposed Entity를 받지 않고, `@Transactional`을 붙이지 않습니다. 트랜잭션은 제공 모듈 Service가 엽니다.
- 입력 검증은 제공 모듈 진입점에 있습니다(`common`의 `InputValidator`가 DTO의 Bean Validation 제약을 실행). 직접 호출에서는 컨트롤러 `@Valid`가 실행되지 않기 때문입니다.
- 오류 변환은 `callProvider { }`가 맡습니다: 4xx `BusinessException`은 그대로, `BulkheadFullException`은 429/`00270`, 그 밖의 서버 오류는 500/`00100`.
- HTTP internal API(`/api/v1/internal/**`)는 만들지 않습니다. `WebSurfaceArchitectureTest`가 매핑이 생기면 빌드를 실패시킵니다.

---

## JWT & 인증 아키텍처

### JWT 구조: Header.Payload.Signature

JWT는 `.`으로 구분된 3개의 Base64URL 인코딩 파트로 구성됩니다.

```
eyJhbGciOiJIUzUxMiJ9  .  eyJzdWIiOiI1NTJmLi4uIn0  .  xK9mT2...
       Header                     Payload                Signature
```

**Payload** — 이 프로젝트의 Access Token 클레임 구성
```json
{
  "sub":           "550e8400-e29b-41d4-a716-446655440000",
  "aud":           ["USER"],
  "loginId":       "user01",
  "email":         "user@example.com",
  "memberType":    "MEMBER",
  "emailVerified": true,
  "permissions":   "READ,WRITE",
  "iat":           1713000000,
  "exp":           1713003600
}
```

- `aud`는 발급처입니다. 일반 사이트 `USER`, 관리자 사이트 `ADMIN`.
- Refresh Token에는 `type: "refresh"` 클레임이 있고, 인증 필터는 `type`이 있는 토큰을 액세스 토큰으로 받지 않습니다(401).

> ⚠️ **Payload는 암호화가 아닌 인코딩입니다.** 비밀번호·개인식별정보 등 민감 데이터를 절대 포함하지 마십시오. Signature는 위·변조 감지용이며 기밀성을 제공하지 않습니다.

### 왜 비대칭키(RS256)가 아닌 HMAC인가?

토큰 발급(iam)과 검증(인증 필터)이 **같은 애플리케이션 안**에 있습니다. 외부 서비스에 검증을 위임하지 않으므로 공개키 배포(JWKS)가 필요 없고, secret 하나만 관리하면 됩니다. `jwt.secret`은 **반드시 256bit(32byte) 이상**이어야 합니다.

### 인증 흐름

```
[로그인]
  Client → POST /api/v1/auth/login
         → JwtAuthenticationFilter: 공개 경로(메서드+경로 정확 일치) → 토큰 검증 생략
         → iam: 자격증명 검증 → Access Token(body) + Refresh Token(HttpOnly Cookie)

[인증이 필요한 API 호출]
  Client → Authorization: Bearer <access_token>
         → StrictHttpFirewall: `;`, `%2F`, `//`, `..` 경로 거절(400)
         → CorsFilter: preflight 처리
         → JwtAuthenticationFilter
              1. 신뢰 헤더(X-User-* 등 6개) 제거 — 클라이언트가 보낸 회원 헤더는 무시
              2. 허용 prefix(/api/v1/{auth,member,admin,stop,reservation,contract}) 밖 → 404
              3. 토큰 서명·만료 검증, 액세스 토큰 여부 확인 → 실패 시 본문 없는 401
              4. /api/v1/admin/** → aud=ADMIN + 관리자 회원 유형이 아니면 본문 없는 403
              5. MemberPrincipal 을 request attribute 로, 회원 ID 를 MDC 로
         → AuthorizeHandlerInterceptor: @Authorize 평가 → 미인증 401, 권한 불일치 403
         → CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입

[토큰 갱신]
  Client → POST /api/v1/auth/token/refresh
         → iam: Redis 저장 토큰과 비교 → 새 Access Token + 새 Refresh Token (Token Rotation)

[로그아웃]
  Client → POST /api/v1/auth/logout
         → iam: Redis에서 해당 세션 Refresh Token 삭제 → 서버 측 즉시 무효화
```

### 토큰 정책

| 토큰 종류 | TTL | 저장소 | 특징 |
|-----------|-----|--------|------|
| Access Token (일반, `aud=USER`) | 60분 | 없음 (Stateless) | 서명 검증만 수행. 권한 변경 시 최대 60분 지연 허용 (의도적 설계) |
| Access Token (관리자, `aud=ADMIN`) | 15분 | 없음 (Stateless) | 탈취 시 피해가 커서 짧게 둔다. `/api/v1/admin/**`은 이 토큰만 통과 |
| Refresh Token | 7일 | Redis (`refresh:{memberId}` Hash, 세션 단위) | 서버 측 무효화 가능. 재발급 시 Token Rotation, 발급처(`aud`) 유지 |

> Access Token에 Redis 블랙리스트를 적용하지 않은 것은 의도적 결정입니다. 권한 변경의 즉시 반영이 필요해지면 `JwtProvider`에 블랙리스트 조회 로직을 추가하십시오.

### Spring Security 설계 원칙

Spring Security는 **필터 체인·방화벽·CORS·세션 정책 등 인프라**로 쓰고, 인증·인가 판정은 커스텀 계층이 합니다.

```
SecurityFilterChain (app, 하나)
  ├─ StrictHttpFirewall / CorsFilter
  ├─ JwtAuthenticationFilter       인증 + 경로 허용 목록 + 관리자 발급처 (예전 gateway 역할)
  └─ authorizeHttpRequests: anyRequest().permitAll()   (판정은 필터와 인터셉터가 끝낸다)
HandlerInterceptor
  └─ AuthorizeHandlerInterceptor + @Authorize           인가 (카테고리·권한·이메일 인증)
```

- **회원 정보는 토큰에서만 만든다.** 예전 MSA는 gateway가 넣은 `X-User-*` 헤더를 하위 서비스가 그대로 믿었습니다. 모놀리스에서는 앱의 모든 핸들러가 8080에 열리므로 헤더 신뢰 경로(`MemberPrincipalExtractFilter`, 컨트롤러의 `@RequestHeader("X-User-Id")`)를 없앴습니다. 컨트롤러는 `@CurrentMember MemberPrincipal`로만 받습니다(`WebSurfaceArchitectureTest`가 강제).
- **인증 필터는 빈으로 등록하지 않는다.** 빈이면 Spring Boot가 서블릿 필터로도 등록해 체인 밖에서 한 번 더 돕니다.
- **관리자 API는 2중 방어.** 인증 필터의 발급처 검사 + 모든 관리자 핸들러의 `@Authorize`(테스트로 누락 방지).

**AOP 대신 HandlerInterceptor를 사용하는 이유**

AOP(`@Around`)는 내부적으로 `RequestContextHolder`(ThreadLocal 기반)를 통해 `HttpServletRequest`에 접근합니다. Virtual Thread 환경에서 `ThreadLocal` 사용을 지양하므로, `HttpServletRequest`를 직접 받는 `HandlerInterceptor`를 채택했습니다. `MemberPrincipal`도 SecurityContext(ThreadLocal) 대신 request attribute에 둡니다.

---

## 로깅 (Logging & MDC 전파)
> **Log4j2 + LMAX Disruptor** 비동기 로거를 사용합니다. 모든 환경에서 사람이 바로 읽을 수 있는 패턴 로그를 stdout으로 출력하며, Docker 로그 회전으로 디스크 사용량을 제한합니다.

### MDC 자동 주입 필드

| 필드 | 주입 주체 | 설명 |
|------|-----------|------|
| `requestId` | `MdcLoggingFilter` | HTTP 요청 단위 고유 ID (`X-Request-ID` 헤더 또는 UUID 자동 생성, 응답 헤더로도 반환) |
| `memberId` | `JwtAuthenticationFilter` | 토큰 검증에 성공한 회원 ID. 클라이언트 헤더로는 바뀌지 않는다. 요청 끝에 `MdcLoggingFilter`가 제거 |

### 실제 사용법

```kotlin
private val log = LoggerFactory.getLogger(MyService::class.java)

// 로그 호출만 하면 requestId, memberId가 자동으로 붙는다
log.info("처리 시작 — itemId={}", itemId)
log.error("처리 실패", exception)
```

### `@Async` 비동기 MDC 자동 전파

`@Async` 메서드는 별도 스레드에서 실행되어 MDC가 전파되지 않습니다. 세 레이어에서 자동 처리합니다.

1. **`AsyncMdcAspect`** — `@Async` 메서드 호출 시 호출 스레드 MDC 스냅샷을 캡처·복원
2. **`ThreadPoolConfig.mdcTaskDecorator`** — Executor 제출 시점 MDC를 실행 스레드에 주입
3. **`AsyncConfigurer.getAsyncExecutor()`** — executor 미지정 `@Async`도 위 두 레이어가 적용된 `IoBoundExecutor`를 기본으로 사용

### 수동 비동기 MDC 전파 (`CompletableFuture.runAsync` 등)

```kotlin
import com.media.bus.common.logging.MdcContextUtil

CompletableFuture.runAsync(MdcContextUtil.wrap {
    log.info("비동기 작업 — MDC 자동 전파됨")
})
```

### 로그 포맷

```text
2026-11-17 20:34:56.631 INFO  [tomcat-handler-26] [req:32d1c138-...] [user:01a1e949-...] c.m.b.c.l.AccessLoggingFilter - ACCESS GET /api/v1/member/me
```

### 참조 파일

| 파일 | 역할 |
|------|------|
| `common/logging/MdcLoggingFilter.kt` | requestId MDC 주입, 요청 끝에 MDC 정리 |
| `app/security/JwtAuthenticationFilter.kt` | 검증된 회원 ID → MDC `memberId` |
| `common/logging/MdcContextUtil.kt` | 수동 비동기 MDC 전파 유틸 (`wrap(Runnable)`, `wrap(Callable)`) |
| `common/core/aop/AsyncMdcAspect.kt` | `@Async` 자동 MDC 전파 Aspect |
| `common/configuration/ThreadPoolConfig.kt` | MDC `TaskDecorator` + 기본 Executor (`AsyncConfigurer`) |

## 관측성(Observability) 현황

> 단일 EC2의 메모리와 디스크 사용량을 우선해 별도 관측성 스택을 운영하지 않습니다.

| 영역 | 현재 운영 방식 |
|------|------------------|
| **로그** | Log4j2 패턴 stdout + Docker `json-file` 로테이션 |
| **요청 상관관계** | `requestId` MDC. 요청 하나가 한 프로세스 안에서 끝나므로 모듈을 넘나들어도 같은 ID가 유지된다 |
| **헬스체크** | Actuator `/actuator/health` — 관리 포트(컨테이너 안 `127.0.0.1:8081`)에서만. compose healthcheck가 사용 |
| **런타임 로그 레벨** | 관리 포트의 `/actuator/loggers` (공개 8080에서는 404) |
| **에러 수집** | 없음. Sentry Gradle 플러그인만 선언돼 있고(`apply false`) 적용하지 않았다 |

Prometheus, Tempo, Loki, Alloy, Grafana는 현재 Compose 구성에 포함하지 않습니다. 서비스 규모와 운영 요구가 커진 뒤 별도 인스턴스 또는 관리형 서비스로 재검토합니다.

## 디버깅 (Remote JVM Debug)

`docker-compose-local.yml`의 `app` 컨테이너는 JDWP를 **18080**으로 엽니다.

1. IntelliJ `Run/Debug Configurations` → **+** → **Remote JVM Debug**
2. **Host**: `localhost` / **Port**: `18080` / **Use module classpath**: `bus.modules.app.main`
3. `docker compose -f docker-compose-local.yml up -d` 후 Breakpoint를 걸고 디버그 실행 → `Connected to the target VM`

💡 기동 과정(빈 생성, 컨텍스트 초기화)을 디버깅하려면 `JAVA_TOOL_OPTIONS`의 `suspend=n`을 `suspend=y`로 바꿔 재시작합니다. IDE에서 `:modules:app:bootRun`을 직접 디버그 실행해도 됩니다.

## 주의사항
> 이 프로젝트는 Java Virtual-Thread를 기본 사양으로 간주합니다.\
> Virtual Thread 환경에서는 스레드에 종속적인 데이터를 다룰 때 `ThreadLocal` 사용을 지양하고 `ScopedValue`(JDK 25+) 사용을 권장합니다.\
> \
> Virtual Thread를 DB 커넥션과 1:1로 매핑하면 순식간에 connection pool이 고갈될 수 있습니다.\
> 이를 막기 위해 Resilience4j Bulkhead를 AOP로 **`@Transactional`** 을 얻는 모든 로직에 적용합니다(`TransactionalBulkheadAspect`).\
> \
> 모놀리스로 합치면서 **모든 모듈이 같은 `orderDatabase` 퍼밋(19, Hikari 풀 20 − 1)을 나눠 씁니다.** 예전처럼 서비스마다 따로 있던 격리는 없으므로, 정류소 조회 폭주가 예약 쓰기 퍼밋을 잠식할 수 있다는 점을 전제로 부하를 검증하십시오.

---

## 테스트 현황

> 단위 테스트는 MockK, 일부 Repository는 in-memory H2 기반입니다. PostgreSQL 전용 기능(부분 유니크 인덱스, 제외 제약)은 기본 비활성 `StopHoldPostgresIT`로 확인합니다.

| 모듈 | 테스트 파일 | 주요 대상 |
|------|------------|-----------|
| `app` | 6 | 인증 필터(헤더 위조·발급처·라우트), 경로 정책, **웹 표면 아키텍처**(internal 매핑 0개·관리자 `@Authorize`·`X-User-*` 헤더 금지·스키마 이름 중복), **모듈 경계**, 어댑터 계약 |
| `common` | 7 | AOP(Bulkhead·Async MDC), MDC Filter, 입력 검증, PageResult |
| `auth-contract` | 4 | MemberType, Interceptor / ArgumentResolver |
| `iam` | 15 | Validator, AuthService, RoleResolution, Repository, MemberService, 관리자 서비스·컨트롤러 `@Authorize` 커버리지 |
| `stop` | 15 | Seoul Bus API Client, Entity 변경 이력, Guard, Service, 진입점 입력 검증 |
| `reservation` | 8 | 예약 Facade(선점·부분 실패), 계약 Facade 입력 검증, PostgreSQL IT |

```bash
./gradlew test                                                # 전체
./gradlew :modules:app:test                                   # 아키텍처·보안·어댑터
./gradlew :modules:iam:test --tests "패키지.클래스.메서드"
```

---

## 설계 트레이드오프 및 알려진 제약

> 트레이드오프를 명시적으로 기록하여 회수(언제·왜 바꿀지) 시점을 판단하기 쉽도록 합니다.

### 1. 모듈러 모놀리스 (장애 격리 vs 운영 비용)

- **선택**: JVM 1개 + Gradle 모듈 경계 + 포트·어댑터
- **장점**: 메모리·배포·S2S 오류 처리 비용 감소, 모듈 간 호출이 함수 호출이라 부분 실패가 없음
- **단점**: 한 모듈의 메모리 누수·스레드 고갈이 전체 API를 멈춘다. Bulkhead 퍼밋도 공유한다
- **회수 시점**: 특정 모듈만 따로 확장해야 하거나 장애 격리가 필요해지면, 그 모듈의 포트 어댑터를 HTTP 구현으로 바꾸고 별도 실행 모듈로 분리한다(스키마·URL은 이미 분리돼 있음)

### 2. Access Token 무상태 (권한 반영 지연)

- **선택**: TTL 일반 60분·관리자 15분 + Redis 블랙리스트 없음
- **단점**: 권한 박탈/계정 정지가 토큰 만료까지 지연된다(관리자 토큰 갱신은 iam이 거부)
- **회수 시점**: 즉시 반영이 필요해지면 `JwtProvider`에 `jti` 블랙리스트 또는 `iat` 비교 로직 추가

### 3. Bulkhead는 Semaphore 카운터 (Hikari pool과 자동 동기화 X)

- **선택**: Resilience4j `Bulkhead`(semaphore) + `maxConcurrentCalls=19` (Hikari pool=20 − 1 여유, local은 10 − 1)
- **단점**: semaphore 카운터와 실제 connection 점유가 자동 동기화되지 않는다. `REQUIRES_NEW`(감사 로그 등)는 커넥션을 하나 더 쓴다
- **보완**: HikariCP `leakDetectionThreshold`, `connectionTimeout` 튜닝 검토

### 4. 예약 동시성 — DB 제약 + Redis 게이트

- **정합성**: 부분 유니크 인덱스(광고면당 진행 중 예약 1건), 제외 제약(계약 기간 겹침 금지), 상태 전이 `SELECT ... FOR UPDATE`
- **부하 차단**: Redis 게이트(`StopHoldGate`)가 몰린 요청 중 1건만 DB로 보낸다. 정합성은 맡기지 않고 Redis 장애 시 통과시킨다
- 상세: [`modules/reservation/AGENTS.md`](modules/reservation/AGENTS.md) "선점(hold) 정책"

### 5. 프록시 뒤 클라이언트 IP

- 지금 앱은 `X-Forwarded-For`를 신뢰하지 않는다(`forward-headers-strategy` 미설정). TLS 리버스 프록시를 앞에 둘 때 `server.tomcat.remoteip.internal-proxies`를 프록시 주소 하나로 좁혀 설정하고, 앱 8080은 호스트에 공개하지 않는다([배포 가이드](docs/infra/docker-compose-deployment.md))

### 6. CI/CD 파이프라인 미구성

- 로컬 + Docker Compose만 구성. `.gitlab-ci.yml` / `.github/workflows` 없음

### 7. AWS Aurora / Secrets Manager 통합 미적용

- `JWT_SECRET` 등 시크릿이 환경변수(`.env`) 의존

### 8. 부하/내구성 테스트 도구 미포함

- 동시성·Bulkhead 공유·VT 효과 검증용 도구(k6 / Gatling 등) 미설정
- **권장**: 정류소 조회 폭주 중 예약 생성 시나리오를 k6 스크립트로 확보

---

## 로드맵(우선순위)

1. **메모리 실측** — 유휴·일반 요청·정류소 일괄 등록 중 RSS와 `jcmd VM.flags`로 `mem_limit`·`-Xmx` 확정
2. **GitHub Actions 1장** — build + test + docker build 검증을 PR 게이트로
3. **TLS 리버스 프록시(Caddy)** — HTTPS, 프록시 신뢰 범위, 앱 8080 비공개
4. **부하 시나리오(k6)** — 공유 Bulkhead 퍼밋 아래에서 조회·예약 혼합 부하
5. **응답 불능 감시** — `restart: unless-stopped`는 프로세스 종료만 복구하므로 외부 업타임 모니터 + 알림
6. **Testcontainers 전환** — H2 → PostgreSQL 18 (PostgreSQL 전용 기능 상시 검증)
7. **AWS 시크릿 관리 PoC** — Secrets Manager + Aurora IAM 인증
