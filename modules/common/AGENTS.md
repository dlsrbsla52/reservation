# AGENTS.md — common

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

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
| `CommonLoggingAutoConfiguration` | Servlet 환경 | `MdcLoggingFilter` (requestId + memberId → MDC) |
| `ThreadPoolConfig` | 항상 | 공유 Executor 설정 (`getForkJoinPoolExecutor`, `getIoBoundExecutor`) |

`JwtProvider` 빈은 `auth-contract`의 `AuthContractAutoConfiguration`이 제공한다.

보안 필터 체인은 등록하지 않는다. 체인은 `modules/app`의 `SecurityConfig` 하나뿐이다. 예전에 두던 "체인이 없으면 permitAll" 기본 체인은 `app` 체인이 빠졌을 때 인증 없이 열리는 구조라 지웠다.

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
| `MethodArgumentNotValidException` · `HttpMessageNotReadableException` · `MethodArgumentTypeMismatchException` · `MissingServletRequestParameterException` | 400 (`00260`) |
| `NoResourceFoundException` · `NoHandlerFoundException` (매핑 없는 경로) | 404 (`00280`) |
| `HttpRequestMethodNotSupportedException` | 405 (`00281`, `Allow` 헤더) |
| `HttpMediaTypeNotSupportedException` | 415 (`00282`) |
| `BulkheadFullException` | 429 (`00270`, `Retry-After: 1`) |
| `NoAuthenticationException` | 401 |
| `NoAuthorizationException` / `AccessDeniedException` | 403 |
| `StorageException` | 404 |
| `BaseException` | 500 |
| `Exception` (catch-all) | 500 |

`Exception` catch-all은 Spring MVC가 원래 4xx로 처리하던 예외까지 먼저 잡는다. 클라이언트 오류 예외는 전용 처리기를 둬야 500이 되지 않는다(`ExceptionAdvisorRequestErrorTest`).

새 예외 추가: `BaseException(result, cause)` 상속. 예외 클래스에는 `message` 파라미터가 없고 메시지는 항상 `Result.message`다. `Result` 인터페이스는 `CommonResult` enum이 구현 — 모듈별 enum(`AuthResult` A, `StopResult` S, `ReservationResult` R, `ContractResult` C)도 구현한다.

```kotlin
// 예외 생성 예시 — 메시지는 Result enum에만 둔다
throw BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL)
throw ServiceException(StopResult.SEOUL_API_PARSE_FAIL)
throw BaseException(cause = e)
```

문자열 예외(`IllegalStateException("…")`, `error("…")`, `require(…) { "…" }`)는 main 코드에서 빌드가 막는다(`lint/forbidden-apis-main.txt`, ktlint `bus:no-hardcoded-exception-message`).

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

## 진입점 입력 검증 (`InputValidator`)

모듈 간 호출은 HTTP가 아니라 같은 프로세스 안의 직접 호출이라 제공 모듈 컨트롤러의 `@Valid`가 실행되지 않는다.
다른 모듈이 부르는 Service·Facade 진입점에서 `InputValidator.validate(request)`로 DTO 제약을 실행한다.
실패하면 `BusinessException(CommonResult.VALIDATION_FAIL)`(400/`00260`, 기본 메시지)을 던진다.
`@Validated` 프록시 검증은 `ConstraintViolationException` 처리기가 없어 500이 되므로 쓰지 않는다.

(예전 S2S HTTP 인프라 `S2SRestClientFactory`·`TokenProvider`·`internalRestClient`는 모듈러 모놀리스 전환 때 제거했다.)

## Exposed 기본 엔티티

JPA 대신 Exposed DAO 패턴을 사용한다. Table object와 Entity class를 분리하여 정의한다.

- `BaseEntityClass` / `BaseTable` — UUID PK (UUIDv7), `equals`/`hashCode` 제공
- `DateBaseEntityClass` / `DateBaseTable` — `BaseTable` + `createdAt`/`updatedAt` 감사 필드

UUID v7은 시간 정렬 가능한 monotonically increasing UUID. B-tree 인덱스 친화적, Virtual Thread 안전.

## 로깅

- **Log4j2** + **LMAX Disruptor 4.0** — `AsyncRoot` 락프리 비동기 로깅 (Virtual Thread 핀닝 방지)
- **`MdcLoggingFilter`** — `requestId` MDC 주입, 요청 끝에 `requestId`·`memberId` 제거. `memberId`는 `app` 인증 필터가 토큰 검증 후 `MDC_MEMBER_ID`로 넣는다(헤더를 읽지 않는다)

MDC는 ThreadLocal 기반이므로 새 스레드에서 자동 전파 안 됨.
`@Async` 메서드는 `mdcTaskDecorator`가 자동 처리. 수동 스레드 생성 시 `MdcContextUtil.wrap()` 사용.

로그 포맷: 모든 프로파일에서 Docker logs로 바로 읽을 수 있는 플레인 패턴 출력
