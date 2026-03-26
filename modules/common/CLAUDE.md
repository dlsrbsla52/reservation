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
| `CommonLoggingAutoConfiguration` | Servlet 환경 | `MdcLoggingFilter` (requestId + userId → MDC) |
| `RestClientConfig` | Servlet 환경 | `internalRestClient` 빈 |
| `QueryDslConfig` | 항상 | `JPAQueryFactory` 빈 |
| `ThreadPoolConfig` | 항상 | 공유 Executor 설정 |

`JwtProvider` 빈은 `auth-contract`의 `AuthContractAutoConfiguration`이 제공한다 (common의 `TokenProvider` 구현체).

## 소비 모듈 필수 설정

```yaml
hig:
  bulkhead:
    database-name: <name>   # resilience4j.bulkhead.instances 키와 일치해야 함

rest:
  service:
    apply-patterns:
      - /api/**             # ResponseAdvisor가 응답을 래핑할 경로 패턴
```

## 응답 래핑 (ResponseBodyWrapper)

`ResponseAdvisor`가 `@Order` 순으로 `ResponseBodyWrapper` 빈을 순회하여 응답 래핑.

| 순서 | 래퍼 | 조건 → 결과 |
|------|------|-------------|
| 1 | `PassthroughBodyWrapper` | `AbstractView` or `String` → 그대로 통과 |
| 2 | `NullBodyWrapper` | `null` → `NoDataView` |
| 3 | `PageResultBodyWrapper` | `PageResult` → `PageView` |
| 4 | `DefaultObjectBodyWrapper` | 나머지 → `DataView` |

새 래퍼 추가: `ResponseBodyWrapper` 구현 후 `@Bean` 등록.

## 예외 처리 (ExceptionAdvisor)

| 예외 | HTTP 상태 |
|------|-----------|
| `NoAuthenticationException` | 401 |
| `NoAuthorizationException` / `AccessDeniedException` | 403 |
| `StorageException` | 404 |
| `BaseException` | 500 |
| `Exception` (catch-all) | 500 |

새 예외 추가: `BaseException(Result result, ...)` 상속. `Result` 인터페이스는 `CommonResult` enum이 구현 — 모듈별 enum도 구현 가능.

**예약된 Result 코드**: `00000`–`00299` (common 전용). 다른 모듈에서 사용 금지.

## Enum 규칙

```java
@Getter @AllArgsConstructor @SuppressWarnings("unused")
public enum MyType implements BaseEnum {
    FOO("FOO", "전시명");
    private final String name;
    private final String displayName;
    public static Optional<MyType> fromName(String name) {
        return BaseEnum.fromName(MyType.class, name);
    }
}
```

## 동시성 패턴

### TransactionalBulkheadAspect
모든 `@Transactional` 메서드와 `@Repository` 빈을 Resilience4j 세마포어 Bulkhead로 감싼다.
재진입 추적: `ScopedValue<Boolean>` (Java 25) — `ThreadLocal` 대체 금지.

### @BoundedConcurrency
`@Async` + `CompletableFuture<T>` 반환 메서드에 적용. `Semaphore` 빈 이름을 지정.
```java
@BoundedConcurrency("myTaskSemaphore")
@Async("myTaskExecutor")
public CompletableFuture<String> doAsync() { ... }
```
self-invocation (`this.method()`)은 AOP 우회 → 비동기 메서드는 별도 빈으로 분리.

## 서비스 간 HTTP 통신

`internalRestClient` 빈: `Authorization` 헤더를 신뢰 내부 호스트에만 자동 전파.
신뢰 호스트 패턴: `*.local`, `*.internal`, `*service*`, `localhost`, `10.*`

비동기/스케줄러 등 요청 컨텍스트 없는 호출은 `TokenProvider.generateS2SToken()`으로 S2S 토큰을 자동 주입.

## JPA 기본 엔티티

- `BaseEntity` — UUID PK (`@UuidV7`), Hibernate-proxy-safe `equals`/`hashCode`
- `DateBaseEntity` — `BaseEntity` + `createdAt`/`updatedAt` 감사 필드

UUID v7은 시간 정렬 가능한 monotonically increasing UUID. B-tree 인덱스 친화적, Virtual Thread 안전.

```java
@Id @UuidV7
@Column(name = "id", updatable = false, nullable = false)
private UUID id;
```

## 로깅

- **Log4j2** + **LMAX Disruptor 4.0** — `AsyncRoot` 락프리 비동기 로깅 (Virtual Thread 핀닝 방지)
- **Micrometer Tracing + OTel** — `traceId`/`spanId` MDC 자동 주입
- **`MdcLoggingFilter`** — `requestId`, `userId` MDC 주입

MDC는 ThreadLocal 기반이므로 새 스레드에서 자동 전파 안 됨.
`@Async` 메서드는 `AsyncMdcAspect`가 자동 처리. 수동 스레드 생성 시 `MdcContextUtil.wrap()` 사용.

로그 포맷: `local` 프로파일 → 컬러 패턴 / 그 외 → JSON 구조화 (CloudWatch Logs Insights 최적화)