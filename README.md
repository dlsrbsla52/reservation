# 예약 시스템 개발 프로젝트
ㄴ ㄲㅏㄹ>`java25`, `Spring Boot 4.0.5`, `Kotlin 2.3.20`을 위한 MSA(Microservices Architecture) 기반 보일러플레이트

## 시스템 아키텍처 (MSA Multi-Module 구조)
본 프로젝트는 **MSA(Microservices Architecture)** 구조를 지향하며, 단일 프로젝트(Monorepo) 내에서 멀티 모듈 기반으로 구성됩니다. 각 서브 모듈은 도메인 특성에 맞게 독립적인 웹 애플리케이션으로 동작합니다.

- **common**: 전체 프로젝트에서 공유하는 공통 도메인/DTO, 유틸리티, 글로벌 예외 처리, Exposed ORM 엔티티, AWS SDK, DB 설정 및 Security 기본 설정이 응집된 핵심 라이브러리 모듈입니다.
- **auth-contract**: JWT 토큰 구조 및 인증 계약을 정의하는 공유 라이브러리 모듈입니다. `iam`과 타 서비스 간의 인증 인터페이스 계약을 담당합니다.
- **gateway**: 클라이언트의 모든 요청을 단일 진입점으로 받아 각 마이크로서비스로 라우팅하는 Spring Cloud Gateway 모듈입니다. (Expected Port: 8080)
- **iam**: JWT 토큰 발급·갱신 프로세스와 사용자의 인증(Authentication)/인가(Authorization) 및 회원의 라이프사이클(가입, 조회, 수정, 제재, 탈퇴 등) 및 회원 관련 비즈니스 도메인을 관리하는 모듈입니다. (Expected Port: 8181)
- **stop**: 정류소의 관리 매니저를 담당하는 서비스 모듈입니다. 각 정류소의 매칭 상태와 가격(유동인구, 판매가격, 결제 시기, 재계약 시기) 등 비즈니스 도메인을 책임지는 메인 워커 모듈입니다. (Expected Port: 8182)
- **reservation**: 시스템의 Core 비즈니스인 실제 예약 생성, 변경, 취소 등의 트랜잭션 로직을 책임지는 메인 워커 모듈입니다. (Expected Port: 8183)

## API 문서 (Swagger UI)

> 각 마이크로서비스는 **springdoc-openapi**를 통해 Swagger UI를 독립적으로 제공합니다.
> 로컬 실행 후 아래 URL로 접근할 수 있습니다.

| 서비스 | Swagger UI | OpenAPI JSON |
|--------|------------|--------------|
| `iam` (인증/회원) | http://localhost:8181/swagger-ui.html | http://localhost:8181/api-docs |
| `stop` (정류소) | http://localhost:8182/swagger-ui.html | http://localhost:8182/api-docs |
| `reservation` (예약) | http://localhost:8183/swagger-ui.html | http://localhost:8183/api-docs |

> **참고**: Gateway(8080)를 통해서는 Swagger UI에 접근할 수 없습니다. 각 서비스 포트로 직접 접근해야 합니다.

---

## DB 및 통합 테스트(MSA) 구동 환경
> 본 프로젝트는 Docker Compose를 이용해 모든 MSA 모듈을 로컬 컨테이너 환경에서 통합 테스트할 수 있도록 최적화되어 있습니다.
> 
> 다음과 같은 명령어로 DB(PostgreSQL) 및 API Gateway를 포함한 서비스를 띄울 수 있습니다.
>
> ```bash
> docker-compose up --build -d
> ```
> 특정 서비스만 재빌드시 사용
> ```bash
> docker-compose up -d --build --no-deps {{service-name}}
> docker-compose up -d --build --no-deps gateway-service
> ```
> 병렬 빌드 실행 (약 30% 속도 향상)
> ```bash
> DOCKER_BUILDKIT=1 docker compose build --parallel
> DOCKER_BUILDKIT=1 docker compose -f docker-compose-local.yml build --parallel
> DOCKER_BUILDKIT=1 docker compose -f docker-compose-local.yml up --build -d
> ```
> 로컬 빌드 실행
> ```bash
> docker-compose -f docker-compose-local.yml up -d --build --no-deps {{service-name}}
> docker-compose -f docker-compose-local.yml up -d --build --no-deps gateway-service
>```
> 위 명령어를 실행하면 Host OS의 아키텍처(AMD64/ARM64)에 맞춰 Java 25 환경이 동적으로 구성되는 빌더 이미지를 통해 각 모듈의 `.jar`가 패키징됩니다. 
> API Gateway(`8080`)를 단일 진입점으로 하여 뒤단의 개별 서비스(`8181`, `8183`) 포트가 묶여 백그라운드에서 구동됩니다. DB는 `15433` 포트로 바인딩됩니다.
>
> 💡 **데이터 영속성(Persistence)**
> 개발환경에선 `docker-compose-local.yml` 내에 `postgres_data`라는 Docker Name Volume이 매핑되어 있습니다.
> 이로 인해 `docker-compose down`이나 컨테이너(rm)를 강제로 삭제하더라도, **매핑된 볼륨(`-v`)을 함께 삭제하지 않는 이상 DB 내의 데이터는 영구적으로 유지**되므로 안전하게 테스트를 껐다 켤 수 있습니다.

### 인프라 구성

| 컴포넌트 | 버전 | 포트 | 용도 |
|----------|------|------|------|
| PostgreSQL | 18.3 | 15433 | 주 데이터베이스 (모듈별 schema 격리) |
| Valkey (Redis 호환) | 8.1.6 | 6379 | 캐시 / 세션 / 분산 락 |


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
companion object {
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