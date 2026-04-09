# Kotlin 마이그레이션 소스코드 품질 리뷰 보고서

> **프로젝트:** bus MSA (Spring Boot 4.0.5)
> **마이그레이션:** Java 25 → Kotlin 2.3.20, JPA/QueryDSL → Exposed 1.0.0
> **검토일:** 2026-04-09
> **검토 파일 수:** ~140개 (Kotlin 소스 + build.gradle.kts + 설정 파일)

---

## 요약

| 심각도 | 건수 | 설명 |
|--------|------|------|
| CRITICAL | 3 | 런타임 경쟁 조건, Virtual Thread 핀닝, 명명 혼동 |
| MAJOR | 12 | Java 잔재 패턴, 코드 중복, 예외 분류 오류 |
| MINOR | 11 | Kotlin 관용성 부족, 불필요한 추상화 |
| INFO | 6 | 참고 사항 |

**전체 판정: Java→Kotlin 마이그레이션 품질은 전반적으로 양호.**
핵심 아키텍처(Virtual Thread 안전, 모듈 경계, 트랜잭션 분리)는 우수하며,
발견된 이슈 대부분은 Java 관용구의 잔재(`Optional`, `@JvmStatic`, 생성자 오버로딩, Builder 패턴)이다.

## 영역별 등급

| 영역 | 등급 | 핵심 사항 |
|------|------|-----------|
| 아키텍처 & 모듈 경계 | **A** | Facade 패턴, S2S 분리, 모듈별 스키마 격리, 모듈 간 DTO 격리 우수 |
| Kotlin 관용성 | **B-** | 핵심 기능은 활용하나 sealed class, 확장 함수, nullable 타입 활용 부족 |
| Virtual Thread 안전 | **A** | ScopedValue 체계적 사용, ThreadLocal 금지 규약 준수 |
| 보안 | **B+** | JWT/Cookie/S2S 다계층 보안 우수, `!!` 강제 언래핑 일부 위험 |
| 예외 처리 구조 | **A-** | BusinessException/ServiceException 분리 우수, 일부 예외 분류 오류 |
| 테스트 | **B** | common/auth-contract/stop 양호, iam/reservation 보강 필요 |

---

## 1. CRITICAL — 반드시 수정 필요 (3건)

### [C-01] S2STokenFilter에서 JWT 이중 파싱 — 경쟁 조건

**파일:** `modules/auth-contract/.../filter/S2STokenFilter.kt:52-59`

`isValidS2SToken()` 내부에서 `isInvalidToken(token)` 호출(1차 파싱) 후 `parseClaimsFromToken(token)`(2차 파싱)을 수행한다.
1차에서 Claims를 파싱하지만 결과를 버리고 2차에서 다시 파싱한다.
**두 호출 사이에 토큰이 만료되면** 1차는 성공하지만 2차에서 `JwtException`이 발생하여
정상 요청이 401로 거부될 수 있다.

**수정안:**
```kotlin
private fun isValidS2SToken(token: String): Boolean =
    jwtProvider.tryParseClaims(token)
        ?.let { claims -> S2S_TOKEN_TYPE == claims.get("type", String::class.java) }
        ?: false
```

---

### [C-02] ThreadPoolConfig 함수명과 실제 동작이 반대

**파일:** `modules/common/.../configuration/ThreadPoolConfig.kt:67`

`getCpuBoundExecutor()`라는 함수명이지만 실제로는 `Executors.newVirtualThreadPerTaskExecutor()`로 IO-bound 실행기를 생성한다.
Bean 이름은 `"IoBoundExecutor"`로 올바르지만, 함수명이 혼동을 유발한다.
유지보수 시 CPU-bound 작업에 Virtual Thread 실행기를 사용하는 실수를 유발할 수 있다.

**수정안:** 함수명을 `getIoBoundExecutor()`로 변경.

---

### [C-03] JwtProvider의 synchronized — Virtual Thread 핀닝 발생

**파일:** `modules/auth-contract/.../security/JwtProvider.kt:108`

`generateS2SToken()`에서 double-checked locking으로 `synchronized(this)`를 사용한다.
Virtual Thread가 `synchronized` 블록 내에서 블로킹되면 캐리어 스레드가 핀닝되어 전체 처리량이 저하된다.

**수정안:**
```kotlin
private val lock = ReentrantLock()

fun generateS2SToken(): String {
    // ...
    lock.withLock { /* 토큰 갱신 */ }
}
```

---

## 2. MAJOR — 수정 권장 (12건)

### common 모듈

#### [M-01] FavoriteUtil — Java 스타일 유틸리티

**파일:** `modules/common/.../utils/FavoriteUtil.kt`

- `convertToMap()`이 Java 리플렉션(`field.isAccessible = true`) 사용 → `KClass.memberProperties` 또는 `ObjectMapper.convertValue<Map>()` 권장
- `convertTo()`가 매번 `ObjectMapper()`를 새로 생성 → 스레드 안전하므로 싱글턴 재사용 필요
- `ifEmpty()`, `ifNotNumericToZero()` → Kotlin 표준 라이브러리(`?.ifEmpty {}`, `toIntOrNull()`)로 대체 가능
- 모든 메서드에 `@JvmStatic` → 순수 Kotlin 프로젝트에서 불필요

#### [M-02] 예외 클래스에 과도한 생성자 오버로딩

**파일:** `modules/common/.../exceptions/BaseException.kt` 외 4개

`BaseException`에 9개 생성자, 각 하위 예외에 8개 — Java 텔레스코핑 안티패턴의 잔재.

**수정안:** Kotlin 기본 인수(default parameter)로 단일 주생성자 사용:
```kotlin
open class BaseException(
    val result: Result = CommonResult.FAIL,
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message ?: result.getMessage(), cause)
```

#### [M-03] MdcLoggingFilter에서 `java.util.Optional` 사용

**파일:** `modules/common/.../logging/MdcLoggingFilter.kt:63`

`resolveMemberId()`가 `Optional<String>` 반환 → Kotlin에서는 `String?`이 표준.

#### [M-04] `@JvmStatic` 과도 사용 (36개 이상)

**파일:** `ApiResponse.kt`, `Base64Util.kt`, `FavoriteUtil.kt`, `MessageUtil.kt`, `PathNameBuilder.kt`, `MdcContextUtil.kt`, `UuidV7.kt`, `PageResult.kt`

Java interop이 필요한 경우만 유지하고 나머지는 제거 권장.

### auth-contract 모듈

#### [M-05] JwtProvider.tryParseClaims()가 Optional 반환

**파일:** `modules/auth-contract/.../security/JwtProvider.kt:147`

**수정안:** `fun tryParseClaims(token: String): Claims?`로 변경, 호출부를 `?.let {}` / `?: throw` 패턴으로 전환.

#### [M-06] MemberPrincipal.fromHeaders()에서 `!!` 강제 언래핑

**파일:** `modules/auth-contract/.../security/MemberPrincipal.kt:95`

`role` 파라미터가 nullable이지만 `memberTypeName!!`으로 강제 언래핑 → NPE 위험.

**수정안:** `role`을 non-null `String`으로 선언하거나, null인 경우 명시적 예외를 던진다.

### gateway 모듈

#### [M-07] HealthCheck가 WebFlux 환경에서 MVC 패턴 사용

**파일:** `modules/gateway/.../healthcheck/HealthCheck.kt`

Gateway는 WebFlux 기반인데 `@RestController`로 `ApiResponse` 반환 — Servlet 기반 패턴과 혼재.

**수정안:** `RouterFunction` + `HandlerFunction` 패턴 또는 Actuator health endpoint로 대체.

### iam 모듈

#### [M-08] resolveMemberType() 코드 중복 — DRY 위반

**파일:** `modules/iam/.../auth/service/AuthService.kt:212-222`, `modules/iam/.../member/service/MemberService.kt:62-69`

거의 동일한 로직이 두 서비스에 존재. 유일한 차이는 에러 코드.

**수정안:** 공통 도메인 서비스(`RoleResolutionService`)로 추출.

#### [M-09] AdminRegisterRequestValidator에서 잘못된 예외 타입

**파일:** `modules/iam/.../admin/guard/AdminRegisterRequestValidator.kt:32-39`

중복 검사 실패 시 `NoAuthenticationException` 사용 — 비즈니스 검증 실패이므로 `BusinessException`이 적절.
(`RegisterRequestValidator`는 올바르게 `BusinessException`을 사용하고 있어 일관성 깨짐)

### stop 모듈

#### [M-10] SeoulBusApiClient에서 `lateinit var` + `@PostConstruct` 패턴

**파일:** `modules/stop/.../client/SeoulBusApiClient.kt:27-38`

3개 필드가 `lateinit var` → 생성자 주입이 Kotlin에서 관용적.

**수정안:**
```kotlin
@Component
class SeoulBusApiClient(
    @Value("\${seoul.api.key}") private val apiKey: String,
    @Value("\${seoul.api.base-url:http://openapi.seoul.go.kr:8088}") baseUrl: String,
) {
    private val restClient: RestClient = RestClient.builder().baseUrl(baseUrl).build()
}
```

### reservation 모듈

#### [M-11] ContractController에서 `!!` 강제 언래핑

**파일:** `modules/reservation/.../contract/controller/ContractController.kt:44`

`MemberPrincipal.extractBearerToken(httpRequest)!!` → NullPointerException 위험.

**수정안:**
```kotlin
val token = MemberPrincipal.extractBearerToken(httpRequest)
    ?: throw NoAuthenticationException("Authorization 헤더가 없습니다.")
```

### 프로젝트 전반

#### [M-12] sealed class / sealed interface 활용 부족

프로젝트 전체에서 `sealed interface`는 `StopSearchCriteria` 하나만 사용.
예외 계층이나 Result 타입에 sealed class를 적용하면 `when` 표현식에서 컴파일 타임 완전성 검사를 받을 수 있다.

---

## 3. MINOR — 개선 고려 (11건)

### common 모듈

| ID | 이슈 | 파일 | 설명 |
|----|------|------|------|
| N-01 | PageResult Builder 불필요 | `PageResult.kt:22-46` | data class에서 Java Builder 패턴 유지 → named arguments로 충분 |
| N-02 | Base64Util, PathNameBuilder | `utils/` | Kotlin stdlib로 대체 가능한 단순 래퍼 |
| N-03 | BulkheadProperties mutable | `BulkheadProperties.kt` | `var` → `val` + constructor binding 권장 |
| N-04 | Result에서 UnaryOperator | `result/Result.kt:20` | Java 함수형 인터페이스 → `(String) -> String` |

### auth-contract 모듈

| ID | 이슈 | 파일 | 설명 |
|----|------|------|------|
| N-05 | @JvmField 과도 사용 | `MemberPrincipal.kt:45-62` | `@JvmField val` → `const val`로 변경 가능 |
| N-06 | java.lang.Boolean 명시 사용 | `MemberPrincipal.kt:127` | Kotlin `Boolean?` 타입으로 처리 가능 |

### 기타 모듈

| ID | 이슈 | 파일 | 설명 |
|----|------|------|------|
| N-07 | PUBLIC_PATHS 선형 검색 | `JwtAuthenticationFilter.kt:35-44` | `List` → `Set` 또는 `PathPatternParser` 매칭 권장 |
| N-08 | 조회 API에 POST 사용 | `MemberController.kt:27-47` | RESTful 규칙상 GET이 적절 |
| N-09 | @Value private var | `AuthController.kt:51` | 생성자 주입 `val`로 불변 선언 권장 |
| N-10 | FQCN import | `ContractFacade.kt:25` | import 문으로 해결 가능 |
| N-11 | `List<Any>` 반환 타입 | `ReservationController.kt:41` | 구체 타입 DTO 정의 권장 |

### build.gradle.kts

| ID | 이슈 | 설명 |
|----|------|------|
| N-12 | Mockito + MockK 중복 | MockK 전환 시 `exclude(group = "org.mockito")` 추가 권장 |

---

## 4. Kotlin 관용성 평가

### Java 잔재 패턴 (제거 대상)

| 패턴 | 발견 횟수 | Kotlin 대안 |
|------|-----------|-------------|
| `java.util.Optional` | 3곳 | nullable 타입 (`T?`) |
| `@JvmStatic` | 36+ | top-level 함수 또는 companion object 직접 호출 |
| `@JvmField` | 10+ | `const val` (컴파일 타임 상수) |
| 생성자 오버로딩 | 5개 클래스 | 기본 인수 (default parameter) |
| Builder 패턴 | 2곳 | named arguments + `copy()` |
| `java.lang.Boolean` | 1곳 | Kotlin `Boolean?` |
| `UnaryOperator<T>` | 1곳 | `(T) -> T` |

### 미활용 Kotlin 기능

| 기능 | 현재 상태 | 권장 |
|------|-----------|------|
| sealed class/interface | 1곳만 사용 | 예외 계층, Result 타입에 확대 |
| 확장 함수 | 미사용 | 엔티티→DTO 변환에 도입 (`entity.toResponse()`) |
| 코루틴 | 미사용 | 당장 불필요, 비동기 요구사항 증가 시 검토 |
| `when` 표현식 | 제한적 사용 | sealed class와 함께 활용 확대 |
| scope 함수 | 제한적 사용 | `let`, `apply`, `also` 적극 활용 |

### 잘 활용된 Kotlin 기능

| 기능 | 사용 사례 |
|------|-----------|
| data class | DTO, 요청/응답 객체 |
| object 선언 | Exposed Table 정의 |
| val 불변 선언 | 대부분의 프로퍼티 |
| Kotlin DSL | Exposed 쿼리 |
| `?.` safe call | null 체크 대부분 |
| companion object | 정적 팩토리, 상수 |

---

## 5. 긍정적 관찰 사항

1. **Virtual Thread 안전 설계가 일관적** — ThreadLocal 대신 `ScopedValue`(Java 25), `HttpServletRequest.setAttribute()`, `ObjectProvider<HttpServletRequest>` 등을 체계적으로 사용. 이 수준의 Virtual Thread 인식은 매우 드물다.

2. **모듈 경계가 잘 지켜짐** — reservation 모듈이 stop 모듈의 `BusStopResponse`를 직접 참조하지 않고 자체 `StopInfo` DTO를 정의하여 결합도를 낮추었다.

3. **Exposed ORM 전환이 깔끔함** — JPA/QueryDSL에서 Exposed DAO 패턴으로의 전환이 일관적이며, Table object + Entity class 분리가 정돈되어 있다.

4. **Auto-configuration 구조 우수** — `@ConditionalOnWebApplication`, `@ConditionalOnClass`, `@ConditionalOnMissingBean` 조건을 적절히 활용하여 Gateway(WebFlux)와 하위 서비스(Servlet)가 충돌 없이 동작한다.

5. **Facade 패턴으로 트랜잭션 경계 분리** — S2S 호출은 트랜잭션 외부, DB 저장은 내부로 분리하여 장기 트랜잭션과 분산 트랜잭션 문제를 회피한다.

6. **StopSearchCriteria sealed interface** — 검색 기준을 타입 안전하게 표현하며, `when` 완전성 검사를 활용. 프로젝트 내 모범 사례.

7. **S2S 토큰 설계가 체계적** — `S2SRestClientFactory` + `S2STokenFilter`의 대칭 구조, 내부/외부 API 분리(`/api/v1/internal/`), 신뢰 호스트 검증.

8. **한국어 주석/KDoc이 충실** — 프로젝트 규칙을 잘 준수하며, 설계 의도와 제약사항이 명확히 기록되어 있다.

---

## 6. 우선순위별 개선 로드맵

### Phase 1: 즉시 수정 (CRITICAL)

| 순서 | 이슈 | 영향 | 예상 작업량 |
|------|------|------|-------------|
| 1 | [C-03] JwtProvider `synchronized` → `ReentrantLock` | Virtual Thread 처리량 저하 | 소 |
| 2 | [C-01] S2STokenFilter JWT 이중 파싱 제거 | 정상 요청 401 거부 가능 | 소 |
| 3 | [C-02] ThreadPoolConfig 함수명 수정 | 유지보수 혼동 | 소 |

### Phase 2: 단기 개선 (MAJOR, 1~2주)

| 순서 | 이슈 | 작업 |
|------|------|------|
| 1 | [M-05, M-03] `Optional` → nullable 타입 | 3개 파일 수정 |
| 2 | [M-06, M-11] `!!` 강제 언래핑 제거 | 명시적 예외 처리로 전환 |
| 3 | [M-02] 예외 클래스 생성자 축소 | 기본 인수 활용 |
| 4 | [M-04] `@JvmStatic` / `@JvmField` 정리 | 36+ 곳 제거 |
| 5 | [M-08] resolveMemberType() 중복 제거 | 공통 서비스 추출 |
| 6 | [M-09] 예외 타입 수정 | NoAuthenticationException → BusinessException |

### Phase 3: 중기 개선 (MINOR, 2~4주)

- 확장 함수 도입 (엔티티→DTO 변환)
- sealed class 활용 확대 (예외 계층, Result 타입)
- REST API 메서드 정규화 (POST → GET)
- `lateinit var` → 생성자 주입 전환
- 불필요한 유틸리티 클래스 정리

### Phase 4: 장기 검토

- 코루틴/구조적 동시성 도입 (비동기 요구사항 증가 시)
- Mockito → MockK 완전 전환
- Kotlin DSL 활용 확대 (테스트, 설정)
