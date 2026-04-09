# Kotlin 전환 마이그레이션 변경 이력

> **브랜치**: `bigbag/toKotlin`
> **수행일**: 2026-04-09
> **최종 스택**: Spring Boot 4.0.5 · JDK 25 · Kotlin 2.3.20 · Exposed 1.0.0

---

## 버전 업그레이드 요약

| 항목 | 변경 전 | 변경 후 | 비고 |
|------|---------|---------|------|
| Kotlin | 2.1.20 | **2.3.20** | K2 컴파일러 안정화, JVM 25 공식 지원 |
| JVM Target | JVM_21 | **JVM_25** | Kotlin 2.3.20에서 JVM 25 타깃 정식 지원 |
| Exposed ORM | 0.61.0 | **1.0.0** | Spring Boot 4 호환 (`exposed-spring-boot4-starter`) |
| Exposed Starter | `exposed-spring-boot-starter` | **`exposed-spring-boot4-starter`** | Spring Boot 4.x 전용 아티팩트 |
| `spring-boot-starter-aop` | 직접 의존 | **`org.aspectj:aspectjweaver`** | Boot 4.0.5 BOM에서 누락, `spring-aop`는 전이적 포함 |

---

## 1. Kotlin 2.3.20 호환성 수정

### 1-1. KDoc 주석 내 `/**` 중첩 파싱

K2 컴파일러가 KDoc 안의 `/api/**` 패턴을 중첩 주석 시작(`/**`)으로 오파싱한다.

```kotlin
// Before — 컴파일 에러: Unclosed comment
/** /api/v1/member/** 경로 설명 */

// After — 패턴 제거 또는 백틱 미사용 서술로 변경
/** /api/v1/member 하위 전체 경로 설명 */
```

**영향 파일**: `SecurityConfig.kt` (iam, stop), `InternalStopController.kt`, `AuthContractMvcConfigurer.kt`

### 1-2. `ApiResponse<Void>` → `ApiResponse<Unit?>`

Kotlin에서 Java의 `Void`는 `Unit?`으로 매핑된다. `ApiResponse.success()`와 `ApiResponse.successWithMessage()`가 `ApiResponse<Unit?>`를 반환하므로 컨트롤러 반환 타입을 일치시켜야 한다.

```kotlin
// Before
fun register(...): ApiResponse<Void>

// After
fun register(...): ApiResponse<Unit?>
```

**영향 파일**: `AuthController.kt`, `StopController.kt`, `ReservationController.kt`, `HealthCheck.kt`

### 1-3. `PasswordEncoder.encode()` Nullable 반환

Java의 `PasswordEncoder.encode(CharSequence)`가 Kotlin에서 `String?`로 추론된다.

```kotlin
// After — non-null assertion 추가
encodedPassword = passwordEncoder.encode(request.password)!!
```

**영향 파일**: `AdminMemberService.kt`, `AuthService.kt`, `ContractController.kt`

### 1-4. Kotlin Enum의 `name` 프로퍼티 final

Kotlin enum은 `name` 프로퍼티가 `final`이므로 `BaseEnum.name`을 override할 수 없다. 생성자에서 `name` 파라미터를 제거하고 enum 상수명 자체가 `name` 역할을 하도록 변경했다.

```kotlin
// Before — 컴파일 에러: 'name' is final
enum class MemberStatus(
    override val name: String,
    override val displayName: String,
) : BaseEnum {
    ACTIVE("ACTIVE", "정상 활성 상태"),

// After — name은 enum 내장 프로퍼티로 충족
enum class MemberStatus(
    override val displayName: String,
) : BaseEnum {
    ACTIVE("정상 활성 상태"),
```

**영향 파일**: `MemberStatus.kt`

### 1-5. `BaseEnum.fromName()` — reified 함수 호출

Kotlin reified 함수는 `Class` 파라미터가 불필요하며 `Optional` 대신 nullable을 반환한다.

```kotlin
// Before
fun fromName(name: String): Optional<MemberStatus> = BaseEnum.fromName(MemberStatus::class.java, name)

// After
fun fromName(name: String): MemberStatus? = BaseEnum.fromName<MemberStatus>(name)
```

**영향 파일**: `MemberStatus.kt`, 호출측 `AuthService.kt`, `MemberService.kt`에서 `.orElseThrow` → `?: throw` 변환

### 1-6. `ScopedValue.call` 타입 파라미터 명시

Kotlin 2.3.20에서 `ScopedValue.Carrier.call()`의 타입 추론이 엄격해졌다.

```kotlin
// Before — Cannot infer type parameter 'X'
ScopedValue.where(HAS_PERMIT, true).call { ... }

// After — 반환 타입과 예외 타입을 명시
ScopedValue.where(HAS_PERMIT, true).call<Any?, Exception> { ... }
```

**영향 파일**: `TransactionalBulkheadAspect.kt`

### 1-7. `FilterRegistrationBean.filter` 재할당 불가

Spring Boot 4의 `FilterRegistrationBean`에서 `filter` 프로퍼티가 `val`(read-only)로 변경되었다.

```kotlin
// Before
FilterRegistrationBean<MdcLoggingFilter>().apply { filter = MdcLoggingFilter() }

// After — 생성자를 통한 주입
FilterRegistrationBean(MdcLoggingFilter()).apply { ... }
```

**영향 파일**: `CommonLoggingAutoConfiguration.kt`

### 1-8. `BaseException.message` final 선언

Kotlin 2.3.20에서 `open` 프로퍼티는 초기화, `final`, 또는 `abstract` 중 하나여야 한다. 생성자에서만 설정되는 `message`를 `final`로 명시했다.

```kotlin
final override val message: String
```

**영향 파일**: `BaseException.kt`

### 1-9. 제네릭 타입 바운드 강화

`Class<T>`에서 `Class<T & Any>`로 엄격해진 타입 체크에 대응한다.

```kotlin
// Before
fun <T> createProxy(baseUrl: String, serviceInterface: Class<T>): T

// After
fun <T : Any> createProxy(baseUrl: String, serviceInterface: Class<T>): T
```

**영향 파일**: `S2SRestClientFactory.kt`

### 1-10. Data class 프로퍼티 접근

Java record 스타일 접근자(`principal.id()`)가 Kotlin data class에서는 프로퍼티 접근(`principal.id`)으로 변경.

**영향 파일**: `JwtAuthenticationFilter.kt`

### 1-11. `doFilterInternal` protected 접근 제한

Kotlin 2.3.20에서 Java `protected` 메서드 접근이 엄격해져 테스트에서 `doFilterInternal` → `doFilter`(public)로 변경.

**영향 파일**: `MemberPrincipalExtractFilterTest.kt`

### 1-12. AssertJ `satisfies` 타입 추론

`satisfies` 람다의 타입 추론 실패 → `Consumer` 명시.

```kotlin
// Before
.satisfies { ex -> ... }

// After
.satisfies(java.util.function.Consumer { ex -> ... })
```

**영향 파일**: `AdminRegisterRequestValidatorTest.kt`

---

## 2. Exposed 1.0.0 마이그레이션

### 2-1. 패키지 리네임 전체 매핑

| 0.61.0 | 1.0.0 | 설명 |
|--------|-------|------|
| `org.jetbrains.exposed.dao.id.UUIDTable` | `org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable` | java.util.UUID 전용 `.java.` 패키지 |
| `org.jetbrains.exposed.dao.UUIDEntity` | `org.jetbrains.exposed.v1.dao.java.UUIDEntity` | java.util.UUID 전용 |
| `org.jetbrains.exposed.dao.UUIDEntityClass` | `org.jetbrains.exposed.v1.dao.java.UUIDEntityClass` | java.util.UUID 전용 |
| `org.jetbrains.exposed.dao.id.EntityID` | `org.jetbrains.exposed.v1.core.dao.id.EntityID` | |
| `org.jetbrains.exposed.sql.Database` | `org.jetbrains.exposed.v1.jdbc.Database` | core → jdbc 이동 |
| `org.jetbrains.exposed.sql.SchemaUtils` | `org.jetbrains.exposed.v1.jdbc.SchemaUtils` | core → jdbc 이동 |
| `org.jetbrains.exposed.sql.transactions.transaction` | `org.jetbrains.exposed.v1.jdbc.transactions.transaction` | core → jdbc 이동 |
| `org.jetbrains.exposed.sql.selectAll` | `org.jetbrains.exposed.v1.jdbc.selectAll` | core → jdbc 이동 |
| `org.jetbrains.exposed.sql.javatime.*` | `org.jetbrains.exposed.v1.javatime.*` | |
| `org.jetbrains.exposed.sql.SqlExpressionBuilder.*` | `org.jetbrains.exposed.v1.core.*` | top-level 함수로 승격 |

### 2-2. `uuid()` → `javaUUID()`

1.0.0에서 `uuid()`는 `kotlin.uuid.Uuid`를 반환한다. `java.util.UUID`를 유지하려면 `javaUUID()`를 사용한다.

```kotlin
import org.jetbrains.exposed.v1.core.java.javaUUID

// Before
val stopId = uuid("stop_id")

// After
val stopId = javaUUID("stop_id")
```

**영향 파일**: `StopTable.kt`, `ContractTable.kt`, `ContractDetailTable.kt`, `ReservationTable.kt`

### 2-3. 연산자 import 변경

`SqlExpressionBuilder`가 deprecated되고 연산자가 top-level 함수로 승격되었다.

```kotlin
// Before
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like

// After
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
```

**영향 파일**: 모든 Repository 클래스

### 2-4. `clientDefault` API 변경

`Column<T>.clientDefault { }` 체인이 동작하지 않아 `Column.defaultValueFun` 직접 설정으로 변경.

```kotlin
// Before
val createdAt = timestampWithTimeZone("created_at")
    .clientDefault { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) }

// After
val createdAt = timestampWithTimeZone("created_at")
    .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
```

**영향 파일**: `BaseTable.kt` (common 모듈)

### 2-5. Spring Boot 4 Starter

`exposed-spring-boot-starter`는 Spring Boot 3.x 구 패키지(`org.springframework.boot.autoconfigure.jdbc`)를 참조하여 Boot 4.x에서 `ClassNotFoundException` 발생. `exposed-spring-boot4-starter`로 교체.

```kotlin
// Before
api("org.jetbrains.exposed:exposed-spring-boot-starter:0.61.0")

// After
api("org.jetbrains.exposed:exposed-spring-boot4-starter:1.0.0")
```

### 2-6. DSL 조인 문법

명시적 조인 조건 인자가 제거되어 infix 조인 표현으로 변경.

```kotlin
// Before
RolePermissionTable
    .innerJoin(RoleTable, { RolePermissionTable.roleId }, { RoleTable.id })

// After — FK reference로 자동 조인
(RolePermissionTable innerJoin RoleTable innerJoin PermissionTable)
```

**영향 파일**: `RolePermissionRepository.kt`

---

## 3. 인프라 변경

### 3-1. Dockerfile

```dockerfile
# Before
COPY gradlew build.gradle settings.gradle ./

# After — Kotlin DSL 파일명
COPY gradlew build.gradle.kts settings.gradle.kts ./
```

### 3-2. `spring-boot-starter-aop` 교체

Spring Boot 4.0.5 BOM에서 `spring-boot-starter-aop` 버전 관리가 누락됨.
`spring-aop`는 `spring-boot-starter-web`이 전이적으로 포함하므로 AspectJ 위빙 런타임만 명시 선언.

```kotlin
// Before
api("org.springframework.boot:spring-boot-starter-aop")

// After
api("org.aspectj:aspectjweaver")
```

### 3-3. 테스트 스키마 생성

H2 인메모리 DB 테스트에서 스키마 격리를 위해 `CREATE SCHEMA` 선행 실행 필요.

```kotlin
transaction {
    exec("CREATE SCHEMA IF NOT EXISTS stop")
    SchemaUtils.create(StopTable, StopUpdateHistoryTable)
}
```

**영향 파일**: `StopEntityTest.kt`, `StopUpdateHistoryEntityTest.kt`

---

## 4. 알려진 제약

| 항목 | 상태 | 설명 |
|------|------|------|
| `TransactionalBulkheadAspectTest.shouldRejectExcessiveCalls` | Flaky | Virtual Thread 기반 동시성 테스트 타이밍 이슈. 마이그레이션과 무관 |
| `clientDefault` 체인 문법 | Workaround | Exposed 1.0.0에서 `Column.clientDefault` 확장이 `UUIDTable` 수신자와 불일치. `defaultValueFun` 직접 설정으로 우회 |

---

## 5. 참고 자료

- [Kotlin 2.3.20 릴리즈 노트](https://kotlinlang.org/docs/whatsnew2320.html)
- [Exposed 1.0 릴리즈 블로그](https://blog.jetbrains.com/kotlin/2026/01/exposed-1-0-is-now-available/)
- [Exposed 0.61.0 → 1.0.0 마이그레이션 가이드](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)
