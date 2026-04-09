# CLAUDE.md — common

`modules/common`은 모든 모듈이 소비하는 공유 라이브러리 (`jar`, `bootJar` 없음).
Base package: `com.media.bus.common`

## Build

```bash
./gradlew :modules:common:build
./gradlew :modules:common:test
```

## Auto-Configuration

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 등록. 소비 모듈은 컴포넌트 스캔 없이 모든 빈을 자동 수신한다.

| Auto-config 클래스 | 조건 | 등록 빈 |
|---|---|---|
| `CommonCoreAutoConfiguration` | `@ConditionalOnClass(Aspect.class)` | `TransactionalBulkheadAspect`, `BoundedConcurrencyAspect` |
| `CommonWebMvcAutoConfiguration` | Servlet 환경 | `ResponseBodyWrapper` 빈들, `ResponseAdvisor`, `ExceptionAdvisor` |
| `CommonSecurityAutoConfiguration` | Servlet 환경 | Spring Security 기본 필터 체인 |
| `CommonLoggingAutoConfiguration` | Servlet 환경 | `MdcLoggingFilter` (requestId + memberId → MDC) |
| `RestClientConfig` | Servlet 환경 | `internalRestClient` 빈 |
| `ThreadPoolConfig` | 항상 | 공유 Executor 설정 (`getForkJoinPoolExecutor`, `getIoBoundExecutor`) |

`JwtProvider` 빈은 `auth-contract`의 `AuthContractAutoConfiguration`이 제공한다 (common의 `TokenProvider` 구현체).

## 소비 모듈 필수 설정

```yaml
hig:
  bulkhead:
    database-name: <name>   # resilience4j.bulkhead.instances 키와 일치해야 함
```

## 응답 구조

컨트롤러는 `ApiResponse<T>`를 직접 반환한다. 래핑 인프라(ResponseBodyAdvice) 없음.

| 팩토리 메서드 | 용도 |
|---|---|
| `ApiResponse.success(data)` | 데이터 있는 성공 응답 |
| `ApiResponse.success()` | 데이터 없는 성공 응답 |
| `ApiResponse.successWithMessage(message)` | 커스텀 메시지 성공 응답 |
| `ApiResponse.page(list)` | 목록 응답 (`ListData<E>` 로 감쌈) |

## 예외 처리 (ExceptionAdvisor)

| 예외 | HTTP 상태 |
|------|-----------|
| `BusinessException` | `Result.httpStatus()` (기본 400) |
| `NoAuthenticationException` | 401 |
| `NoAuthorizationException` / `AccessDeniedException` | 403 |
| `StorageException` | 404 |
| `BaseException` | 500 |
| `Exception` (catch-all) | 500 |

새 예외 추가: `BaseException(result, message, cause)` 상속. Kotlin 기본 인수 사용. `Result` 인터페이스는 `CommonResult` enum이 구현 — 모듈별 enum도 구현 가능.

```kotlin
// 예외 생성 예시 — Kotlin 기본 인수 활용
throw BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL)
throw ServiceException(message = "외부 API 오류")
throw BaseException(cause = e)
```

**예약된 Result 코드**: `00000`–`00299` (common 전용). 다른 모듈에서 사용 금지.

## Enum 규칙

```kotlin
enum class MyType(
    override val displayName: String,
) : BaseEnum {
    FOO("전시명"),
    ;

    companion object {
        fun fromName(name: String): MyType? = BaseEnum.fromName<MyType>(name)
    }
}
```

## 동시성 패턴

### TransactionalBulkheadAspect
모든 `@Transactional` 메서드와 `@Repository` 빈을 Resilience4j 세마포어 Bulkhead로 감싼다.
재진입 추적: `ScopedValue<Boolean>` — `ThreadLocal` 대체 금지.

### @BoundedConcurrency
`@Async` + `CompletableFuture<T>` 반환 메서드에 적용. `Semaphore` 빈 이름을 지정.
```kotlin
@BoundedConcurrency("myTaskSemaphore")
@Async("myTaskExecutor")
fun doAsync(): CompletableFuture<String> { ... }
```
self-invocation (`this.method()`)은 AOP 우회 → 비동기 메서드는 별도 빈으로 분리.

### ThreadPoolConfig
- `getForkJoinPoolExecutor` — CPU-bound 작업용 ForkJoinPool (work-stealing)
- `getIoBoundExecutor` — IO-bound 작업용 Virtual Thread 실행기
- 두 실행기 모두 `MdcContextUtil.wrap()`으로 MDC 컨텍스트를 자동 전파

## 서비스 간 HTTP 통신

`internalRestClient` 빈: `Authorization` 헤더를 신뢰 내부 호스트에만 자동 전파.
신뢰 호스트 패턴: `*.local`, `*.internal`, `*service*`, `localhost`, `10.*`

비동기/스케줄러 등 요청 컨텍스트 없는 호출은 `TokenProvider.generateS2SToken()`으로 S2S 토큰을 자동 주입.

## Exposed 기본 엔티티

JPA 대신 Exposed DAO 패턴을 사용한다. Table object와 Entity class를 분리하여 정의한다.

- `BaseEntityClass` / `BaseTable` — UUID PK (UUIDv7), `equals`/`hashCode` 제공
- `DateBaseEntityClass` / `DateBaseTable` — `BaseTable` + `createdAt`/`updatedAt` 감사 필드

UUID v7은 시간 정렬 가능한 monotonically increasing UUID. B-tree 인덱스 친화적, Virtual Thread 안전.

## 로깅

- **Log4j2** + **LMAX Disruptor 4.0** — `AsyncRoot` 락프리 비동기 로깅 (Virtual Thread 핀닝 방지)
- **Micrometer Tracing + OTel** — `traceId`/`spanId` MDC 자동 주입
- **`MdcLoggingFilter`** — `requestId`, `memberId` MDC 주입 (X-User-Id 헤더 기반, Virtual Thread 안전)

MDC는 ThreadLocal 기반이므로 새 스레드에서 자동 전파 안 됨.
`@Async` 메서드는 `mdcTaskDecorator`가 자동 처리. 수동 스레드 생성 시 `MdcContextUtil.wrap()` 사용.

로그 포맷: `local` 프로파일 → 컬러 패턴 / 그 외 → JSON 구조화 (CloudWatch Logs Insights 최적화)
