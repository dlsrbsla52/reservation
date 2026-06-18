# 예약 시스템 개발 프로젝트

> **`Java 25 (LTS)`** · **`Spring Boot 4.0.5`** · **`Kotlin 2.3.20 (K2)`** 기반 MSA(Microservices Architecture) 보일러플레이트

## 시스템 아키텍처 (MSA Multi-Module 구조)
본 프로젝트는 **MSA(Microservices Architecture)** 구조를 지향하며, 단일 프로젝트(Monorepo) 내에서 멀티 모듈 기반으로 구성됩니다. 각 서브 모듈은 도메인 특성에 맞게 독립적인 웹 애플리케이션으로 동작합니다.

- **common**: 전체 프로젝트에서 공유하는 공통 도메인/DTO, 유틸리티, 글로벌 예외 처리, Exposed ORM 엔티티, AWS SDK, DB 설정 및 Security 기본 설정이 응집된 핵심 라이브러리 모듈입니다.
- **auth-contract**: JWT 토큰 구조 및 인증 계약을 정의하는 공유 라이브러리 모듈입니다. `iam`과 타 서비스 간의 인증 인터페이스 계약을 담당합니다.
- **gateway**: 클라이언트의 모든 요청을 단일 진입점으로 받아 각 마이크로서비스로 라우팅하는 Spring Cloud Gateway 모듈입니다. (Expected Port: 8080)
- **iam**: JWT 토큰 발급·갱신 프로세스와 사용자의 인증(Authentication)/인가(Authorization) 및 회원의 라이프사이클(가입, 조회, 수정, 제재, 탈퇴 등) 및 회원 관련 비즈니스 도메인을 관리하는 모듈입니다. (Expected Port: 8181)
- **stop**: 정류소의 관리 매니저를 담당하는 서비스 모듈입니다. 각 정류소의 매칭 상태와 가격(유동인구, 판매가격, 결제 시기, 재계약 시기) 등 비즈니스 도메인을 책임지는 메인 워커 모듈입니다. (Expected Port: 8182)
- **reservation**: 시스템의 Core 비즈니스인 실제 예약 생성, 변경, 취소 등의 트랜잭션 로직을 책임지는 메인 워커 모듈입니다. (Expected Port: 8183)

## 기술 스택 요약

> Kotlin은 언어, JDK 25는 컴파일 타깃 + 런타임입니다. 두 축을 분리해서 선정 근거를 기록합니다.

| 레이어 | 선택 | 선정 이유 |
|--------|------|-----------|
| **언어** | Kotlin 2.3.20 (K2 컴파일러) | Null 안전성, `data class`, `sealed interface`, JDK API 직접 호출 가능 |
| **런타임/타깃** | JDK 25 (LTS, 2025-09) | Virtual Thread 핀닝 해소(JEP 491, JDK 24) + `ScopedValue` 정식 표준화(JEP 506, JDK 25) |
| **프레임워크** | Spring Boot 4.0.5 (Spring Framework 7) | Boot 4 BOM 통합, 최신 Auto-Configuration |
| **API Gateway** | Spring Cloud Gateway 5.x (WebFlux/Netty) | Reactive 라우팅, Spring Cloud `2025.1.1 Oakwood` Boot 4 공식 호환 |
| **ORM** | Exposed 1.0.0 (DAO + DSL) | Kotlin 친화적 type-safe SQL, JPA 대비 가벼움 |
| **DB** | PostgreSQL 18.3 | 모듈별 schema 격리 (`auth` / `stop` / `reservation`) |
| **캐시/세션** | Valkey 8.1.6 (Redis 호환) | Redis BSL 라이선스 회피, BSD-3-Clause |
| **인증** | JWT (jjwt 0.13.0, HS256) | 단일 조직 내부망 MSA에 적합한 대칭키 |
| **회복성** | Resilience4j 2.4.0 (Bulkhead, Semaphore) | DB connection pool 고갈 사전 차단 |
| **로깅** | Log4j2 + LMAX Disruptor 4.0 | 비동기 락프리 로깅, Virtual Thread 핀닝 회피 |
| **추적** | Micrometer Tracing + OpenTelemetry API | `traceId`/`spanId` MDC 자동 주입 |
| **에러** | Sentry 4.11.0 | 운영 환경 예외 수집 |
| **API 문서** | springdoc-openapi 3.0.2 | OpenAPI 3.0 Swagger UI |
| **마이그레이션** | Liquibase | 모듈별 changelog, 스키마 분리 |
| **테스트** | JUnit 5 + MockK 1.13.10 | Kotlin 친화적 Mock |
| **빌드/배포** | Gradle 9.4.0 (Kotlin DSL) + Docker BuildKit | 멀티 스테이지 + `--mount=type=cache` 캐시 마운트 |

## API 문서 (Swagger UI)

> 각 마이크로서비스는 **springdoc-openapi**를 통해 Swagger UI를 독립적으로 제공합니다.
> 로컬 실행 후 아래 URL로 접근할 수 있습니다.

| 서비스 | Swagger UI | OpenAPI JSON |
|--------|------------|--------------|
| `iam` (인증/회원) | http://localhost:8181/swagger-ui.html | http://localhost:8181/api-docs |
| `stop` (정류소) | http://localhost:8182/swagger-ui.html | http://localhost:8182/api-docs |
| `reservation` (예약) | http://localhost:8183/swagger-ui.html | http://localhost:8183/api-docs |

> **참고**: Gateway(8080)를 통해서는 Swagger UI에 접근할 수 없습니다. 각 서비스 포트로 직접 접근해야 합니다.

실행 중인 서비스에서 YAML 스펙을 바로 추출할 수 있습니다.

```bash
curl --fail --silent --show-error http://localhost:8181/api-docs.yaml --output openapi-iam.yaml
curl --fail --silent --show-error http://localhost:8182/api-docs.yaml --output openapi-stop.yaml
curl --fail --silent --show-error http://localhost:8183/api-docs.yaml --output openapi-reservation.yaml
```

PostgreSQL, Redis, Docker 없이 세 서비스의 OpenAPI YAML을 한 번에 생성하려면 다음 태스크를 실행합니다.

```bash
./gradlew generateAllOpenApiDocs
```

생성 결과는 각 모듈의 `build/docs/openapi/` 아래에 저장됩니다. 서비스별 생성도 지원합니다.

```bash
./gradlew :modules:iam:generateOpenApiDocs
./gradlew :modules:stop:generateOpenApiDocs
./gradlew :modules:reservation:generateOpenApiDocs
```

---

## Docker Compose 배포 및 로컬 통합 테스트

단일 서버 Docker Compose 배포, 로컬 전체 스택 실행, Next.js 프론트엔드 배포 기준, 하이브리드 개발 방식은 [Docker Compose 단일 서버 배포 가이드](docs/infra/docker-compose-deployment.md)를 참고합니다.

로컬 전체 스택은 다음 명령으로 실행합니다.

```bash
docker compose -f docker-compose-local.yml up --build -d
```

운영 단일 서버 배포는 필수 환경 변수를 설정한 뒤 다음 명령으로 실행합니다.

```bash
docker compose up --build -d
```

### 인프라 구성 요약

| 컴포넌트 | 버전 | 포트 | 용도 |
|----------|------|------|------|
| PostgreSQL | 18.3 | 15433 | 주 데이터베이스 (모듈별 schema 격리) |
| Valkey (Redis 호환) | 8.1.6 | 6379 | 캐시 / 세션 / 분산 락 |
| Next.js | 프로젝트별 | 3000 | 웹 프론트엔드 |
| Loki | 3.0.0 | 내부망 | 로그 저장소 |
| Alloy | 1.16.3 | 내부망 | 컨테이너 stdout 로그 수집 |
| Grafana | 11.0.0 | 3300 | 로그 조회 UI |


## MSA 내부 통신 (RestClient)
> 본 프로젝트는 마이크로서비스 간의 동기 통신을 위해 Spring Boot 3.2+ 표준인 **`RestClient`**를 사용합니다.
> 모든 내부 통신은 `common` 모듈에 정의된 `internalRestClient` 빈(Bean)을 통해 이루어지며, 다음과 같은 보안 및 편의 기능을 내장하고 있습니다.
>
> 1. **인증 컨텍스트 자동 전파 (Security Context Propagation)**
     >    - 외부에서 유입된 유저의 JWT 토큰을 Interceptor 수준에서 가로채어, 내부 서비스 호출 시 `Authorization` 헤더에 자동으로 주입합니다.
> 2. **S2S (System-to-System) Fallback**
     >    - 스케줄러(@Scheduled)나 비동기(@Async) 등 유저 컨텍스트가 없는 환경에서 호출될 경우, 미리 정의된 시스템 전용 토큰을 자동으로 주입하여 인가 장애를 방지합니다.
> 3. **엔드포인트 화이트리스트 (Security Whitelisting)**
     >    - 내부 인증 정보가 외부 API(예: Google, Kakao 등)로 유출되는 것을 차단하기 위해, 신뢰할 수 있는 도메인으로의 요청에만 토큰을 주입합니다.
>    - **허용 도메인**: `*.local`, `*.internal`, `*service*`, `localhost`, `10.* (Internal IP)`
>
> 💡 **사용 방법**
> ```kotlin
> @Service
> class MyService(
>     private val internalRestClient: RestClient
> ) {
>     fun callOtherModule() {
>         internalRestClient.get()
>             .uri("http://iam-service/api/v1/member/profile")
>             .retrieve()
>             .body(MemberDto::class.java)
>     }
> }
> ```
>
> 자세한 구현 내용은 `com.common.configuration.RestClientConfig`를 참조하시기 바랍니다.

### 🛡 S2S 통신 인증(S2STokenFilter) vs 사용자 인가(@Authorize) 비교
본 프로젝트는 단일 서비스가 아닌 분산 환경(MSA)이므로 "누가(혹은 어느 서비스가) 요청했는가"를 식별하기 위해 인증/인가 단계를 명확히 분리하여 운영합니다.

1. **S2STokenFilter (시스템 간 인증)**:
    - **적용 대상**: 외부 사용자가 아닌 시스템(Gateway, 백그라운드 워커, 스케줄러, 내부 타 모듈 등)
    - **목적**: 해당 API가 인터넷을 통한 접속이 아니라, **신뢰할 수 있는 내부 망(Service-to-Service)에서 넘어온 호출인지 1차로 확인**하여 외부로부터의 접근을 원천 차단합니다.
    - **구현**: `X-Service-Token` 헤더를 검증합니다. (예: `iam` 서비스의 `/api/v1/member/**` 등 서비스 간 정보 조회 API에 주로 적용)
    - **설정**: 각 서비스의 `SecurityConfig`에서 이 필터를 Bean으로 등록할 때 적용할 경로 리스트(`listOf(...)`)를 주입하여 유연하게 적용 범위를 제어합니다.

2. **@Authorize (사용자 인가)**:
    - **적용 대상**: 모바일/웹 등의 클라이언트를 통해 접근하는 실제 **사용자(Member/User)**
    - **목적**: 사용자의 자격 증명(MemberType, MemberCategory, EmailVerified 여부, Permission 등)을 확인하여 **비즈니스 로직 연산(API) 수행 권한이 있는지 세밀하게 제어**합니다.
    - **구현**: 유저 토큰이 Gateway에서 검증 및 해석되어 주입된 `X-User-...` 헤더 기반의 `MemberPrincipal` 객체를 `AuthorizeHandlerInterceptor`가 AOP 동작 이전에 가로채어 검사합니다.

> 💡 **Best Practice Tip**: 타 서비스(예: `reservation`)가 회원 상세 데이터 조회를 위해 `iam` 서비스를 호출할 때 유저 컨텍스트를 억지로 조작하여 `@Authorize`를 뚫으려 하지 마십시오. **S2STokenFilter**를 사용해 시스템 간 인증만 통과시킨 후 데이터를 자유롭게 조회하도록 분리된 엔드포인트를 두는 방식이 도메인 모델의 순수성을 지키는 데 적합합니다.

---

## JWT & 인증 아키텍처

### JWT 구조: Header.Payload.Signature

JWT는 `.`으로 구분된 3개의 Base64URL 인코딩 파트로 구성됩니다.

```
eyJhbGciOiJIUzI1NiJ9  .  eyJzdWIiOiI1NTJmLi4uIn0  .  xK9mT2...
       Header                     Payload                Signature
```

**Header** — 서명 알고리즘 명시
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload** — 이 프로젝트의 Access Token 클레임 구성
```json
{
  "sub":           "550e8400-e29b-41d4-a716-446655440000",
  "loginId":       "user01",
  "email":         "user@example.com",
  "memberType":    "MEMBER",
  "emailVerified": true,
  "permissions":   "READ,WRITE",
  "iat":           1713000000,
  "exp":           1713003600
}
```

**Signature** — Header + Payload를 secret key로 HMAC-SHA256 해싱한 위·변조 감지용 봉인값
```
HMAC-SHA256(base64url(Header) + "." + base64url(Payload), secretKey)
```

> ⚠️ **Payload는 암호화가 아닌 인코딩입니다.** Base64URL은 누구나 디코딩할 수 있으므로 비밀번호·개인식별정보 등 민감 데이터를 절대 포함하지 마십시오. Signature는 위·변조 감지용이며 기밀성을 제공하지 않습니다.

---

### 왜 RS256이 아닌 HS256인가?

| 항목 | HS256 (이 프로젝트) | RS256 |
|------|---------------------|-------|
| 방식 | HMAC-SHA256 (대칭키, secret 1개 공유) | RSA-SHA256 (비대칭키, Private/Public 분리) |
| 적합한 구조 | 단일 조직 내부망 MSA | 외부 서비스에 검증 위임 (JWKS 공개) |
| secret 관리 | 환경변수 1개 | Private Key 별도 관리 필요 |
| 성능 | 빠름 | 느림 (RSA 연산) |

모든 모듈이 동일 조직 내부망에 위치하고 `modules/auth-contract`를 통해 `JwtProvider`를 공유하는 구조이므로 HS256이 적합합니다. `jwt.secret`은 **반드시 256bit(32byte) 이상**이어야 합니다.

---

### 인증 흐름

```
[로그인]
  Client → POST /api/v1/auth/login
         → Gateway (PUBLIC_PATHS 해당, 토큰 검증 스킵)
         → IAM: 자격증명 검증 → Access Token(body) + Refresh Token(HttpOnly Cookie)

[인증이 필요한 API 호출]
  Client → Authorization: Bearer <access_token>
         → Gateway JwtAuthenticationFilter
              tryParseClaims() 단일 호출로 서명·만료 검증
              실패 → 401 즉시 응답 (하위 서비스 도달 차단)
              성공 → X-User-* 헤더 + X-Service-Token(S2S) 주입 → 하위 서비스 라우팅
         → 하위 서비스 (stop / reservation)
              MemberPrincipalExtractFilter: X-User-* 헤더 → MemberPrincipal 복원
              AuthorizeHandlerInterceptor: @Authorize 평가 → 없으면 401, 불일치 403
              CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입

[토큰 갱신]
  Client → POST /api/v1/auth/token/refresh
         → IAM: Redis 저장 토큰과 비교 → 새 Access Token + 새 Refresh Token (Token Rotation)

[로그아웃]
  Client → POST /api/v1/auth/logout
         → IAM: Redis에서 Refresh Token 삭제 → 서버 측 즉시 무효화
```

---

### 토큰 정책

| 토큰 종류 | TTL | 저장소 | 특징 |
|-----------|-----|--------|------|
| Access Token | 60분 | 없음 (Stateless) | 서명 검증만 수행. 권한 변경 시 최대 60분 지연 허용 (의도적 설계) |
| Refresh Token | 7일 | Redis (`refresh:{memberId}`) | 서버 측 무효화 가능. 재발급 시 Token Rotation 적용 |
| S2S Token | 1시간 | 메모리 캐시 | `type: "s2s"` 클레임으로 구분. double-checked locking + `ReentrantLock` 캐싱 |

> Access Token에 Redis 블랙리스트를 적용하지 않은 것은 의도적 결정입니다. Redis 의존성 제거와 낮은 레이턴시를 우선합니다. 권한 변경의 즉시 반영이 필요한 경우 `JwtProvider`에 블랙리스트 조회 로직을 추가하십시오.

---

### Spring Security 설계 원칙

이 프로젝트는 Spring Security를 인증·인가 엔진으로 사용하지 않습니다. **Spring Security는 CSRF·세션·CORS 등 인프라 설정 도구로만 활용**하고, 실제 인증·인가는 커스텀 계층이 담당합니다.

```
일반적인 Spring Security JWT 방식:
  SecurityFilterChain → JwtFilter → SecurityContextHolder.setAuthentication(...)
  → @PreAuthorize, hasRole() 등

이 프로젝트 방식:
  SecurityFilterChain → anyRequest().permitAll()        (인프라 설정만)
  Gateway GlobalFilter → JWT 검증 → X-User-* 헤더 주입  (Edge Authentication)
  하위 서비스 → MemberPrincipalExtractFilter            (헤더 → 컨텍스트 복원)
             → AuthorizeHandlerInterceptor + @Authorize (인가)
```

**이 설계를 선택한 이유**

일반적인 방식으로 구현하면 하위 서비스 전부가 JWT를 직접 파싱해야 합니다. 서비스 수가 늘어날수록 다음 문제가 발생합니다:

1. **중복 HMAC 연산**: Gateway, IAM, stop, reservation 등 모든 서비스가 같은 토큰을 각각 검증
2. **secret 분산**: 모든 서비스 환경변수에 `jwt.secret` 배포 및 관리 필요
3. **Spring Security 내부 의존**: `SecurityContext`, `GrantedAuthority`, `Authentication` 등 버전 변경 시 전 서비스에 영향

이 프로젝트는 **Edge Authentication 패턴**을 채택합니다. Gateway 단일 경계에서 JWT를 검증하고, 하위 서비스는 신뢰된 `X-User-*` 헤더만 읽어 컨텍스트를 복원합니다.

**AOP 대신 HandlerInterceptor를 사용하는 이유**

AOP(`@Around`)는 내부적으로 `RequestContextHolder`(ThreadLocal 기반)를 통해 `HttpServletRequest`에 접근합니다. 이 프로젝트는 Virtual Thread 환경에서 `ThreadLocal` 사용을 지양하므로, `HttpServletRequest`를 직접 파라미터로 받는 `HandlerInterceptor`를 채택했습니다.

**Java 버전 업그레이드와의 관계**

이 설계의 일부 제약(예: `synchronized` 금지)은 Java 21 시절 Virtual Thread 핀닝 문제를 피하기 위한 것이었습니다. Java 24(JEP 491)에서 이 핀닝 문제가 JVM 레벨에서 해소되었고, Java 25(JEP 506)에서 `ScopedValue`가 정식 표준화되었습니다.

그러나 **이 아키텍처를 일반적인 Spring Security 방식으로 되돌릴 이유는 없습니다.** Virtual Thread 제약 해소는 이 설계의 부수적 이유였을 뿐이며, 핵심 이유인 Edge Authentication 패턴의 아키텍처적 이점(단일 검증, secret 집중, 낮은 결합도)은 Java 버전과 무관하게 유효합니다.

---

## 로깅 (Logging & MDC 전파)
> 본 프로젝트는 **Log4j2 + LMAX Disruptor** 비동기 로거와 **Micrometer Tracing(OTel 브릿지)**를 통해
> 모든 로그에 분산 추적 컨텍스트(MDC)를 자동으로 주입합니다.

### MDC 자동 주입 필드

| 필드 | 주입 주체 | 설명 |
|------|-----------|------|
| `traceId` | Micrometer Tracing | 분산 트레이스 ID (OpenTelemetry) |
| `spanId` | Micrometer Tracing | 현재 Span ID |
| `requestId` | `MdcLoggingFilter` | HTTP 요청 단위 고유 ID (`X-Request-ID` 헤더 또는 UUID 자동 생성) |
| `memberId` | `MdcLoggingFilter` | 인증된 사용자 Principal name |
| `service` | `log4j2-spring.xml` | `spring.application.name` 값 |

### 실제 사용법

```kotlin
// 1. 각 클래스에 Logger 선언
private val log = LoggerFactory.getLogger(MyService::class.java)
// 혹은 companion object에서 선언
companion
object {
    private val log = LoggerFactory.getLogger(MyService::class.java)
}

// 2. 로그 호출만 하면 requestId, traceId, memberId가 자동으로 붙는다
log.info("처리 시작 — itemId={}", itemId)
log.error("처리 실패", exception)
```

### `@Async` 비동기 MDC 자동 전파

`@Async` 메서드는 별도 스레드에서 실행되어 MDC가 전파되지 않습니다.
본 프로젝트는 이를 두 레이어에서 자동 처리합니다:

1. **`AsyncMdcAspect`** — `@Async` 메서드 호출 시 호출 스레드 MDC 스냅샷을 캡처·복원
2. **`ThreadPoolConfig.mdcTaskDecorator`** — Executor 제출 시점 MDC를 실행 스레드에 주입
3. **`AsyncConfigurer.getAsyncExecutor()`** — executor 미지정 `@Async`도 위 두 레이어가 적용된 `IoBoundExecutor`를 기본으로 사용

```kotlin
// executor 지정하거나 생략하거나 — 모두 requestId, traceId 등이 자동 전파된다
@Async("IoBoundExecutor")
fun sendNotification(memberId: String): CompletableFuture<Void> {
    log.info("알림 전송 — memberId={}", memberId)  // requestId, traceId 자동 포함
    return CompletableFuture.completedFuture(null)
}
```

### 수동 비동기 MDC 전파 (`CompletableFuture.runAsync` 등)

Spring `@Async` 외부에서 직접 스레드를 생성하는 경우 `MdcContextUtil.wrap()`을 사용합니다.

```kotlin
import com.media.bus.common.logging.MdcContextUtil

// Runnable 래핑
CompletableFuture.runAsync(MdcContextUtil.wrap {
    log.info("비동기 작업 — MDC 자동 전파됨")
})

// Callable 래핑
CompletableFuture.supplyAsync(MdcContextUtil.wrap {
    log.info("결과 반환 작업")
    result
})
```

### 환경별 로그 포맷

| 프로파일 | 포맷 | 용도 |
|----------|------|------|
| `local` | 컬러 패턴 로그 | 개발 가독성 |
| 운영/스테이징 | JSON 구조화 로그 | CloudWatch Logs Insights 쿼리 최적화 |

```json
{"@timestamp":"...","level":"INFO","traceId":"...","spanId":"...","requestId":"...","memberId":"...","service":"stop","message":"..."}
```

### 참조 파일

| 파일 | 역할 |
|------|------|
| `common/logging/MdcLoggingFilter.kt` | requestId, memberId → MDC 주입 필터 |
| `common/logging/MdcContextUtil.kt` | 수동 비동기 MDC 전파 유틸 (`wrap(Runnable)`, `wrap(Callable)`) |
| `common/core/aop/AsyncMdcAspect.kt` | `@Async` 자동 MDC 전파 Aspect |
| `common/configuration/ThreadPoolConfig.kt` | MDC `TaskDecorator` + 기본 Executor (`AsyncConfigurer`) |
| `common/src/test/.../MdcLoggingFilterDemo.kt` | 동작 시각화 데모 (`./gradlew :modules:common:demoLogging`) |

## 관측성(Observability) 현황

> 코드 측에서 노출되는 신호와 수집 백엔드를 분리해 정직하게 기록합니다.
> 현재는 **신호 노출까지는 완비**, **수집 백엔드는 로컬 미구성** 상태입니다.

| 영역 | 코드 측 준비 | 수집 백엔드 |
|------|--------------|-------------|
| **메트릭** | Spring Boot Actuator + Micrometer | ⚠️ Prometheus 미구성 |
| **추적** | Micrometer Tracing + OpenTelemetry API | ⚠️ OTel Collector / Tempo 미구성 |
| **로그** | Log4j2 JSON Layout (운영 프로파일) | ⚠️ Loki / CloudWatch Logs Insights 미연동 |
| **에러** | Sentry SDK | ✅ Sentry DSN 환경변수만 주입하면 동작 |
| **Bulkhead 메트릭** | Resilience4j Micrometer 바인더 | Actuator `/metrics` 엔드포인트로 노출 |

> 💡 로컬에서 빠르게 관측성 백엔드를 띄우려면 `grafana/otel-lgtm` 단일 컨테이너를 `docker-compose-local.yml`에 추가하는 방식이 가장 가볍습니다(Grafana + Loki + Tempo + Mimir 일체형).

## 디버깅 (Remote JVM Debug)
> 본 프로젝트는 Docker Compose로 구동되는 각 마이크로서비스에 대해 원격 디버깅(Remote JVM Debug) 환경을 기본 제공합니다.
> `docker-compose.yml` 리소스에 JDWP(Java Debug Wire Protocol) 설정 및 포워딩이 구성되어 있습니다.

### 디버깅 모드 접속 방법
1. **IntelliJ Remote Debugger 설정**
    - 우측 상단 `Run/Debug Configurations` -> **Edit Configurations...**
    - **+** 기호 -> **Remote JVM Debug** 선택
    - **Name**: `Remote: Auth Service` 등 원하는 이름 설정
    - **Host**: `localhost` / **Port**: 해당 서비스의 디버그 포트 입력
        - `gateway`: `18080`
        - `iam`: `18181`
        - `stop`: `18182`
        - `reservation`: `18183`
    - **Use module classpath**: 디버깅 타겟 모듈 지정 (예: `reservation.modules.iam.main`)
2. **실행 및 Attach**
    - `docker-compose up -d` 로 컨테이너 기동
    - 비즈니스 로직에 Breakpoint(중단점) 지정
    - 생성한 `Remote JVM Debug` 환경을 실행(디버그 아이콘 클릭)하여 컨테이너에 Attach
    - 정상 연결 시 `Connected to the target VM` 메시지 확인 가능

💡 **애플리케이션 초기화 시점 디버깅**
컨테이너 기동 과정(예: 빈 생성, 컨텍스트 초기화)을 디버깅해야 한다면, `docker-compose.yml`의 `JAVA_TOOL_OPTIONS` 환경 변수 중 `suspend=n`을 `suspend=y`로 변경 후 컨테이너를 재시작하십시오. 디버거가 Attach 될 때까지 애플리케이션 Bootstrap이 일시 정지(Hold)됩니다.

## 주의사항
> 이 프로젝트는 Java Virtual-Thread를 기본 사양으로 간주하고 있습니다.\
> Virtual Thread 환경에서는 스레드에 종속적인 데이터를 다룰 때 `ThreadLocal` 사용을 지양하고 `ScopedValue`(JDK 25+) 사용을 강력히 권장합니다. \
> `ThreadLocal`을 잘못 사용하면 Virtual Thread가 Carrier Thread에 고정(pinning)되어 **심각한 성능 저하와 메모리 누수를 유발할 수 있습니다.** \
> 해당 현상은 java 25에 들어 Jvm 레벨에서 해소 되었기 때문에 java 25 이상을 사용하는 환경에서 발생하지 않습니다.\
> \
> 서버의 고가용성을 위해 Virtual-Thread를 DB 커넥션과 1:1로 매핑 시키면 \
> 순식간에 connection pool이 고갈되는 현상이 발생할 수 있습니다.\
> 이를 해결하기 위해 Bulkhead 처리가 필요합니다. \
> \
> 이는 검증된 라이브러리인 Resilience4j 통해 Bulkhead를 구현 했습니다. \
> AOP를 통해 **`@Transactional`** 을 얻는 모든 로직에 대해 세마포어를 적용합니다. \
> \
> 이는 **`@Transactional`** 사용하며 트랜잭션을 얻는 그 순간부터 Server에 존재하는 \
> `DB Connection Pool`을 사용하기 때문입니다. \
> \
> 자세한 내용은 `com.common.boilerplate.core.aop.TransactionalBulkheadAspect` 참조 바랍니다.

---

## 테스트 현황

> 단위 테스트는 MockK, Repository는 in-memory H2 기반입니다.
> 현재 26개 테스트 클래스가 모듈별로 분산되어 있습니다.

| 모듈 | 클래스 수 | 주요 대상 |
|------|----------|-----------|
| `common` | 8 | AOP(Bulkhead·Async MDC), MDC Filter, RestClient, PageResult |
| `auth-contract` | 4 | MemberType, 인증 Filter / Interceptor / ArgumentResolver |
| `iam` | 9 | Validator, AuthService, RoleResolution, Repository, MemberService |
| `stop` | 5 | Seoul Bus API Client, Entity 변경 이력, Guard, Service |
| `reservation` | 0 | **(미구현 — 우선 보완 대상)** |
| `gateway` | 0 | **(미구현 — 우선 보완 대상)** |

### 실행

```bash
./gradlew test                                                # 전체
./gradlew :modules:iam:test                                   # 단일 모듈
./gradlew :modules:iam:test --tests "패키지.클래스.메서드"
```

---

## 설계 트레이드오프 및 알려진 제약

> 본 프로젝트는 트레이드오프를 명시적으로 기록하여 회수(언제·왜 바꿀지) 시점을 판단하기 쉽도록 합니다.

### 1. Access Token 무상태 (60분 권한 반영 지연)

- **선택**: Access Token TTL 60분 + Redis 블랙리스트 없음
- **장점**: 검증 레이턴시 최소화(HMAC 1회), Redis 의존성 제거
- **단점**: 권한 박탈/계정 정지가 **최대 60분 지연** 됨
- **회수 시점**: 어드민 권한 변경의 즉시 반영이 필요해지면 `JwtProvider`에 `jti` 블랙리스트 또는 `iat` 비교 로직 추가. 어드민 라우트만 TTL 5~10분으로 분리하는 절충도 가능

### 2. Bulkhead는 Semaphore 카운터 (Hikari pool과 자동 동기화 X)

- **선택**: Resilience4j `Bulkhead`(semaphore) + `maxConcurrentCalls=19` (Hikari pool=20 − 1 여유)
- **장점**: VT 환경에서 connection 점유 폭주를 application 레벨에서 미리 차단
- **단점**: semaphore 카운터와 실제 connection 점유가 **자동 동기화되지 않음.** 한 트랜잭션이 외부 호출 후 다른 connection을 요청하는 케이스에서 가정이 깨질 수 있음
- **보완**: HikariCP `leakDetectionThreshold`(예: 5초), `connectionTimeout`을 함께 튜닝

### 3. 예약 동시성 — 현재는 애플리케이션 + UNIQUE 제약 기반

- **현재 보호**: `existsActiveByMemberAndStop` 사전 체크 + DB UNIQUE 제약
- **커버 범위**: 동일 회원의 이중 신청 차단
- **커버하지 않는 범위**: 정류소 슬롯/시간대 경쟁(여러 회원이 같은 자원을 동시에 노릴 때) — Bulkhead는 동시성을 **제한**하는 것이지 **직렬화**가 아님
- **개선안**: 슬롯·예약 정원이 도입되는 시점에 PostgreSQL `pg_try_advisory_xact_lock(stopId)` 또는 Redisson 분산 락 적용 (Valkey가 이미 구성되어 추가 인프라 부담 없음)

### 4. CI/CD 파이프라인 미구성

- 현재는 로컬 + Docker Compose만 구성. `.gitlab-ci.yml` / `.github/workflows` 없음

### 5. AWS Aurora / Secrets Manager 통합 미적용

- `JWT_SECRET` 등 시크릿이 현재 환경변수 평문 의존

### 6. 부하/내구성 테스트 도구 미포함

- 동시성·Bulkhead·VT 효과 검증용 도구(k6 / Gatling / JMH 등) 미설정
- **권장**: 동시 예약 시나리오 한 가지라도 k6 스크립트로 reproducible하게 확보

---

## 로드맵(우선순위)

1. `reservation` 모듈 동시성 보강 — `pg_try_advisory_xact_lock` 도입 + 동시 호출 통합 테스트
2. `reservation` / `gateway` 단위·통합 테스트 충원 (상태 머신 + 라우팅 필터)
3. **GitHub Actions 1장** — build + test + docker build 검증을 PR 게이트로
4. **관측성 백엔드 compose 추가** — `grafana/otel-lgtm` 단일 컨테이너 연동
5. **어드민 토큰 TTL 단축** 또는 `jti` 블랙리스트 — 권한 회수 지연 완화
6. **Testcontainers 전환** — H2 → PostgreSQL 18 (PostgreSQL 전용 기능 검증)
7. **AWS 시크릿 관리 PoC** — Secrets Manager + Aurora IAM 인증
