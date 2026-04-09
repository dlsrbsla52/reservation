# Kotlin 전환 마이그레이션 가이드

> **대상 브랜치**: `bigbag/toKotlin`  
> **작성일**: 2026-04-09  
> **기준 스택**: Spring Boot 4.0.5 · JDK 25 · Kotlin 2.3.20 · Exposed 1.0.0
> **변경 이력**: [kotlin-migration-changelog.md](kotlin-migration-changelog.md) 참조

---

## 목차

1. [개요 및 목표](#1-개요-및-목표)
2. [Kotlin 버전 선택](#2-kotlin-버전-선택)
3. [Exposed ORM 소개 및 JPA 대응표](#3-exposed-orm-소개-및-jpa-대응표)
4. [빌드 시스템 변경](#4-빌드-시스템-변경)
5. [모듈별 마이그레이션 전략](#5-모듈별-마이그레이션-전략)
6. [코드 패턴 대응 예시](#6-코드-패턴-대응-예시)
7. [의존성 변경 목록](#7-의존성-변경-목록)
8. [Virtual Threads / ScopedValue 호환성](#8-virtual-threads--scopedvalue-호환성)
9. [테스트 전략](#9-테스트-전략)
10. [마이그레이션 실행 체크리스트](#10-마이그레이션-실행-체크리스트)
11. [주의 사항 및 알려진 제약](#11-주의-사항-및-알려진-제약)

---

## 1. 개요 및 목표

### 전환 동기

| 항목 | 현재 (Java) | 전환 후 (Kotlin) |
|------|------------|----------------|
| 보일러플레이트 | Lombok `@Getter`, `@Builder`, `@SuperBuilder` | Kotlin `data class`, 기본 생성자 |
| Null 안전성 | `Optional<T>`, `@NonNull` 어노테이션 | 언어 수준 nullable/non-null 타입 |
| ORM 쿼리 | JPA + QueryDSL 별도 Q타입 생성 | Exposed DSL — 단일 언어로 타입 안전 쿼리 |
| AP 코드량 | QueryDSL Q타입 코드 생성 + APT 설정 | 제거됨 |
| Enum 계약 | `BaseEnum` 인터페이스 + Lombok | `BaseEnum` 인터페이스 + Kotlin enum |

### 유지 항목 (변경 없음)

- **Spring Boot 4.0.5** — 동일 버전
- **JDK 25** — 동일 툴체인, Virtual Threads 유지
- **Liquibase** — 마이그레이션 XML 파일 변경 없음, DDL 무변경
- **Log4j2 + LMAX Disruptor** — 비동기 로깅 유지
- **Resilience4j Bulkhead** — AOP 구조 유지
- **Spring Cloud Gateway (WebFlux)** — gateway 모듈 구조 유지
- **PostgreSQL 18.3** — DB 스키마 그대로 (schema isolation 유지)
- **Valkey/Redis** — Refresh Token 저장소 유지

---

## 2. Kotlin 버전 선택

### 선택: **Kotlin 2.1.20**

```
kotlinVersion = "2.1.20"
```

### 후보 비교

| 버전 | 상태 | JDK 25 지원 | 비고 |
|------|------|------------|------|
| Kotlin 2.0.x | GA | preview 수준 | K2 컴파일러 첫 정식 출시 |
| **Kotlin 2.1.20** ✅ | **GA (2025-03)** | **JVM toolchain 25 공식 지원** | K2 안정화, Spring Boot 4 kotlin-spring 플러그인 검증 완료 |
| Kotlin 2.2.x | EAP | 미지원 | 프로덕션 부적합 |

### JDK 25 + Kotlin 2.1.20 핵심 호환성

- `jvmTarget = "25"` — Kotlin 컴파일러에서 정식 지원
- Virtual Thread(`Thread.ofVirtual()`) — JVM 수준 기능, Kotlin 영향 없음
- `ScopedValue` — Java API 직접 호출, Kotlin에서 동일하게 사용
- K2 컴파일러 — 빌드 속도 향상, 더 정확한 타입 추론

---

## 3. Exposed ORM 소개 및 JPA 대응표

### 3.1 핵심 개념 대응

| JPA / Hibernate | Exposed |
|----------------|---------|
| `@Entity` 클래스 | `object XxxTable : Table(...)` |
| `@MappedSuperclass` | 공통 컬럼을 별도 `object`에 정의 후 재사용 |
| `@Id @GeneratedValue` | `UUIDTable` 상속 (UUID PK 내장) |
| `JpaRepository<T, ID>` | Exposed DSL 함수 (`select`, `insert`, `update`, `deleteWhere`) |
| `@Transactional` | Spring `@Transactional` 그대로 유지 (Exposed가 Spring TX에 위임) |
| `@OneToMany`, `@ManyToOne` | 명시적 `join` — 암묵적 lazy 로딩 없음 (N+1 원천 차단) |
| `@Version` (낙관적 잠금) | 컬럼 직접 관리 + `optimisticUpdate` 패턴 |
| `@Enumerated(EnumType.STRING)` | `.customEnumerationByName(...)` 또는 varchar 저장 후 변환 |
| `@Column(nullable = false)` | `.not()` 수정자 생략 시 기본 NOT NULL |
| QueryDSL Q타입 | 제거 — Exposed DSL이 동일 역할 (타입 안전 쿼리) |

### 3.2 Exposed 두 가지 API 스타일

```kotlin
// --- DSL API (쿼리 중심, 유연한 JOIN) ---
val stops = StopTable
    .select(StopTable.stopId, StopTable.stopName)
    .where { StopTable.stopName like "강남%" }
    .map { Stop.fromRow(it) }

// --- DAO API (엔티티 중심, 간단한 CRUD) ---
val stop = StopEntity.findById(id) ?: throw NotFoundException()
```

> **권장**: 복잡한 조인/집계는 **DSL**, 단순 CRUD는 **DAO**. 두 스타일 혼용 가능.

### 3.3 트랜잭션 설정

Exposed는 Spring `@Transactional`과 완전히 통합된다. `exposed-spring-boot-starter`가 자동으로 `SpringTransactionManager`를 등록한다.

```kotlin
// Service 계층 — Spring @Transactional 그대로 사용
@Service
@Transactional
class StopService(private val stopRepository: StopRepository) {
    fun createStop(request: SimpleStopCreateRequest): Unit {
        stopRepository.save(request)
    }
}
```

`transaction { }` 블록은 **Spring 관리 트랜잭션 외부**에서만 사용한다. 혼용 시 트랜잭션 경계가 이중으로 생성되므로 주의.

### 3.4 Liquibase 연동

Exposed는 DDL을 생성하지 않는다. Liquibase 마이그레이션 XML은 **변경 없이 그대로 사용**한다.

```yaml
# application.yml — 변경 없음
spring:
  liquibase:
    enabled: true
    changeLog: /db/changelog/db.changelog-master.xml
  jpa:  # 제거
```

---

## 4. 빌드 시스템 변경

### 4.1 루트 `build.gradle` → `build.gradle.kts` 전환

```kotlin
// build.gradle.kts (루트)

buildscript {
    extra["springbootVersion"] = "4.0.5"
    extra["kotlinVersion"] = "2.1.20"
    extra["exposedVersion"] = "0.61.0"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:${extra["springbootVersion"]}")
        classpath("io.spring.gradle:dependency-management-plugin:1.1.7")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlinVersion"]}")
        classpath("org.jetbrains.kotlin:kotlin-allopen:${extra["kotlinVersion"]}")
        // 제거: querydsl-plugin
    }
}

plugins {
    id("io.sentry.jvm.gradle") version "4.11.0" apply false
    id("com.bmuschko.docker-spring-boot-application") version "10.0.0" apply false
}

allprojects {
    group = "com.media.bus"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "kotlin-spring")          // open class 자동화 (@Component, @Service 등)
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    // 제거: apply plugin: 'java'
    // 제거: querydsl-plugin

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${extra["springbootVersion"]}")
        }
    }

    kotlin {
        jvmToolchain(25)                     // JDK 25 툴체인
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")   // null 안전성 강화
        }
    }

    configurations.configureEach {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
        // 제거: Lombok exclude 불필요
    }

    dependencies {
        // 제거: lombok, annotationProcessor
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")  // Kotlin 직렬화
        implementation("org.jetbrains.kotlin:kotlin-reflect")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("io.mockk:mockk:1.13.10")   // Kotlin 친화적 Mock
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25) }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        jvmArgs("-Dnet.bytebuddy.experimental=true")   // Java 25 ByteBuddy 호환성 유지
    }
}
```

### 4.2 common 모듈 `build.gradle.kts`

```kotlin
// modules/common/build.gradle.kts
plugins { `java-library` }

bootJar { enabled = false }
jar { enabled = true }

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-aop")
    api("org.springframework.boot:spring-boot-starter-log4j2")
    api("com.lmax:disruptor:4.0.0")

    // Exposed — common이 Exposed 기반 BaseTable 제공
    api("org.jetbrains.exposed:exposed-spring-boot-starter:${rootProject.extra["exposedVersion"]}")
    api("org.jetbrains.exposed:exposed-core:${rootProject.extra["exposedVersion"]}")
    api("org.jetbrains.exposed:exposed-dao:${rootProject.extra["exposedVersion"]}")
    api("org.jetbrains.exposed:exposed-jdbc:${rootProject.extra["exposedVersion"]}")
    api("org.jetbrains.exposed:exposed-java-time:${rootProject.extra["exposedVersion"]}")

    // 제거: spring-boot-starter-data-jpa
    // 제거: querydsl-jpa, querydsl-apt

    runtimeOnly("org.postgresql:postgresql")
    api("org.liquibase:liquibase-core")

    api(platform("software.amazon.awssdk:bom:2.39.2"))
    api("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")
    api("io.micrometer:micrometer-tracing-bridge-otel")
}
```

### 4.3 각 서비스 모듈 `build.gradle.kts` (공통 패턴)

```kotlin
// modules/stop/build.gradle.kts (iam, reservation 동일 구조)
bootJar { enabled = true }

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-contract"))
}
```

### 4.4 소스 디렉토리 전환

Java → Kotlin 전환 시 소스 디렉토리를 변경한다.

```
# 전환 전
src/main/java/com/media/bus/...

# 전환 후
src/main/kotlin/com/media/bus/...
src/test/kotlin/com/media/bus/...
```

---

## 5. 모듈별 마이그레이션 전략

### 5.1 common 모듈

#### BaseEntity → Exposed UUIDTable

```kotlin
// modules/common/src/main/kotlin/com/media/bus/common/entity/BaseTable.kt

import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

/**
 * ## 모든 테이블의 공통 기반
 *
 * UUID v7 (시간 정렬 가능) PK를 사용한다.
 * `UUIDTable`이 `id` 컬럼을 자동으로 제공한다.
 */
abstract class BaseTable(name: String, schema: String) :
    UUIDTable(name = "$schema.$name")

/**
 * ## createdAt / updatedAt 공통 컬럼
 *
 * `DateBaseTable`을 상속하면 감사 컬럼이 자동으로 포함된다.
 */
abstract class DateBaseTable(name: String, schema: String) : BaseTable(name, schema) {
    val createdAt = timestampWithTimeZone("created_at").clientDefault {
        java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
    }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault {
        java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
    }
}
```

#### UUID v7 생성 함수

```kotlin
// modules/common/src/main/kotlin/com/media/bus/common/entity/UuidV7.kt

import java.util.UUID
import java.time.Instant

/**
 * ## UUID v7 생성기
 *
 * 타임스탬프 기반으로 단조 증가하는 UUID를 생성한다.
 * B-tree 인덱스 친화적이며 Virtual Thread 안전하다.
 */
object UuidV7 {
    fun generate(): UUID {
        val now = Instant.now()
        val timestamp = now.toEpochMilli()
        val random = java.security.SecureRandom()

        val msb = (timestamp shl 16) or (7L shl 12) or (random.nextLong() and 0xFFFL)
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE

        return UUID(msb, lsb)
    }
}
```

#### BaseEnum → Kotlin interface

```kotlin
// modules/common/src/main/kotlin/com/media/bus/common/entity/BaseEnum.kt

/**
 * ## 모든 코드성 Enum의 공통 계약
 *
 * `name`과 `displayName`을 반드시 구현해야 한다.
 */
interface BaseEnum {
    val name: String
    val displayName: String

    companion object {
        inline fun <reified T> fromName(name: String): T? where T : Enum<T>, T : BaseEnum =
            enumValues<T>().find { it.name == name }
    }
}
```

#### Exception 계층

```kotlin
// modules/common/src/main/kotlin/com/media/bus/common/exceptions/BaseException.kt

open class BaseException(
    val result: Result,
    message: String = result.message,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// BusinessException — 비즈니스 규칙 위반 (4xx)
class BusinessException(result: Result) : BaseException(result)

// ServiceException — 기술적 오류 (5xx)
class ServiceException(result: Result, cause: Throwable? = null) : BaseException(result, cause = cause)

// NoAuthenticationException — 401
class NoAuthenticationException(result: Result) : BaseException(result)

// NoAuthorizationException — 403
class NoAuthorizationException(result: Result) : BaseException(result)
```

#### ApiResponse → Kotlin data class

```kotlin
// modules/common/src/main/kotlin/com/media/bus/common/web/ApiResponse.kt

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
) {
    companion object {
        fun <T> success(data: T) = ApiResponse(success = true, data = data)
        fun success() = ApiResponse<Unit>(success = true)
        fun successWithMessage(message: String) = ApiResponse<Unit>(success = true, message = message)
        fun <E> page(items: List<E>, totalCnt: Long, pageNum: Int, pageRows: Int) =
            ApiResponse(success = true, data = PageResult(items, totalCnt, pageRows, pageNum))
    }
}
```

#### QueryDslConfig 제거

`exposed-spring-boot-starter`가 `SpringTransactionManager`와 데이터소스 연결을 자동으로 구성한다. `QueryDslConfig.java`는 삭제한다.

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에서 `QueryDslConfig` 항목 제거.

---

### 5.2 auth-contract 모듈

#### MemberPrincipal → Kotlin data class

```kotlin
// modules/auth-contract/src/main/kotlin/com/media/bus/contract/security/MemberPrincipal.kt

data class MemberPrincipal(
    val id: UUID,
    val loginId: String,
    val email: String,
    val memberType: MemberType,
    val emailVerified: Boolean,
    val permissions: Set<Permission>
) {
    companion object {
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_LOGIN_ID = "X-User-Login-Id"
        const val HEADER_USER_EMAIL = "X-User-Email"
        const val HEADER_USER_ROLE = "X-User-Role"
        const val HEADER_EMAIL_VERIFIED = "X-Email-Verified"
        const val HEADER_USER_PERMISSIONS = "X-User-Permissions"

        fun fromHeaders(request: HttpServletRequest): MemberPrincipal { ... }
        fun fromClaims(claims: Claims): MemberPrincipal { ... }
    }

    val isAdmin: Boolean get() = memberType.isAdmin
    fun hasPermission(permission: Permission) = permission in permissions
}
```

#### MemberType → Kotlin enum

```kotlin
enum class MemberType(
    override val name: String,
    override val displayName: String,
    val category: MemberCategory
) : BaseEnum {
    MEMBER("MEMBER", "일반 회원", MemberCategory.USER),
    BUSINESS("BUSINESS", "비즈니스 회원", MemberCategory.BUSINESS),
    ADMIN_USER("ADMIN_USER", "관리회원 일반", MemberCategory.ADMIN),
    ADMIN_MASTER("ADMIN_MASTER", "관리회원 마스터", MemberCategory.ADMIN),
    ADMIN_DEVELOPER("ADMIN_DEVELOPER", "관리회원 개발자", MemberCategory.ADMIN);

    val isAdmin: Boolean get() = category == MemberCategory.ADMIN
    val isUser: Boolean get() = category == MemberCategory.USER
    val isBusiness: Boolean get() = category == MemberCategory.BUSINESS
}
```

---

### 5.3 iam 모듈

#### Member Entity → Exposed Table + DAO

```kotlin
// modules/iam/src/main/kotlin/com/media/bus/iam/member/entity/MemberTable.kt

object MemberTable : DateBaseTable("member", "auth") {
    val loginId     = varchar("login_id", 100).uniqueIndex("uq_member_login_id")
    val password    = varchar("password", 255)
    val email       = varchar("email", 255).uniqueIndex("uq_member_email")
    val phoneNumber = varchar("phone_number", 20)
    val emailVerified = bool("email_verified").default(false)
    val status      = enumerationByName<MemberStatus>("status", 20)
    val businessNumber = varchar("business_number", 20).nullable()
}

class MemberEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MemberEntity>(MemberTable)

    var loginId      by MemberTable.loginId
    var password     by MemberTable.password
    var email        by MemberTable.email
    var phoneNumber  by MemberTable.phoneNumber
    var emailVerified by MemberTable.emailVerified
    var status       by MemberTable.status
    var businessNumber by MemberTable.businessNumber

    // 도메인 행위
    fun verifyEmail() { emailVerified = true }
    fun withdraw()    { status = MemberStatus.WITHDRAWN }
    fun suspend()     { status = MemberStatus.SUSPENDED }
}
```

#### Repository → Exposed DSL 함수

```kotlin
// modules/iam/src/main/kotlin/com/media/bus/iam/member/repository/MemberRepository.kt

@Repository
class MemberRepository {

    fun findByLoginId(loginId: String): MemberEntity? =
        MemberEntity.find { MemberTable.loginId eq loginId }.singleOrNull()

    fun findByEmail(email: String): MemberEntity? =
        MemberEntity.find { MemberTable.email eq email }.singleOrNull()

    fun save(entity: MemberEntity): MemberEntity = entity  // DAO 변경은 자동 flush

    fun existsByLoginId(loginId: String): Boolean =
        MemberTable.selectAll().where { MemberTable.loginId eq loginId }.count() > 0
}
```

---

### 5.4 stop 모듈

#### Stop Entity → Exposed Table + DAO

```kotlin
// modules/stop/src/main/kotlin/com/media/bus/stop/entity/StopTable.kt

object StopTable : DateBaseTable("stop", "stop") {
    val stopId       = varchar("stop_id", 50).uniqueIndex()
    val stopName     = varchar("stop_name", 200).index("idx_stop_name")
    val xCrd         = varchar("x_crd", 50)
    val yCrd         = varchar("y_crd", 50)
    val nodeId       = varchar("node_id", 50)
    val stopsType    = enumerationByName<StopType>("stops_type", 30)
    val version      = long("version").default(0L)  // 낙관적 잠금 수동 관리
    val registeredById     = uuid("registered_by_id").nullable()
    val registeredBySource = enumerationByName<ChangeSource>("registered_by_source", 20)
}

class StopEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<StopEntity>(StopTable) {

        /** 정적 팩토리 — Java의 `Stop.requestOf(...)` 대응 */
        fun create(request: SimpleStopCreateRequest, registeredById: UUID): StopEntity =
            new(UuidV7.generate()) {
                stopId = request.stopId
                stopName = request.stopName
                xCrd = request.xCrd
                yCrd = request.yCrd
                nodeId = request.nodeId
                stopsType = request.stopsType
                version = 0L
                this.registeredById = registeredById
                registeredBySource = ChangeSource.USER
            }
    }

    var stopId       by StopTable.stopId
    var stopName     by StopTable.stopName
    var xCrd         by StopTable.xCrd
    var yCrd         by StopTable.yCrd
    var nodeId       by StopTable.nodeId
    var stopsType    by StopTable.stopsType
    var version      by StopTable.version
    var registeredById     by StopTable.registeredById
    var registeredBySource by StopTable.registeredBySource
}
```

#### StopRepository → Exposed DSL

```kotlin
@Repository
class StopRepository {

    fun findByStopId(stopId: String): StopEntity? =
        StopEntity.find { StopTable.stopId eq stopId }.singleOrNull()

    fun findByStopIdIn(stopIds: Collection<String>): List<StopEntity> =
        StopEntity.find { StopTable.stopId inList stopIds }.toList()

    fun findByStopNameStartingWith(prefix: String): List<StopEntity> =
        StopEntity.find { StopTable.stopName like "$prefix%" }.toList()

    fun existsByStopId(stopId: String): Boolean =
        StopTable.selectAll().where { StopTable.stopId eq stopId }.count() > 0

    fun save(entity: StopEntity): StopEntity = entity
}
```

---

### 5.5 reservation 모듈

#### Contract Entity → Exposed Table + DAO

```kotlin
// modules/reservation/src/main/kotlin/com/media/bus/reservation/contract/entity/ContractTable.kt

object ContractTable : DateBaseTable("contract", "reservation") {
    val stopId          = uuid("stop_id").index("idx_contract_stop_id")
    val previousContractId = uuid("previous_contract_id").nullable()
    val memberId        = uuid("member_id").index("idx_contract_member_id")
    val managerId       = uuid("manager_id").nullable()
    val contractName    = varchar("contract_name", 255)
    val status          = enumerationByName<ContractStatus>("status", 20)
        .also { StopTable /* idx_contract_stop_id covers (stop_id, status) */ }
    val autoRenewal     = bool("auto_renewal").default(false)
    val contractStartDate = timestampWithTimeZone("contract_start_date")
    val contractEndDate   = timestampWithTimeZone("contract_end_date")
    val renewalNotifiedAt = timestampWithTimeZone("renewal_notified_at").nullable()
    val cancelledAt     = timestampWithTimeZone("cancelled_at").nullable()
    val cancelReason    = varchar("cancel_reason", 500).nullable()
}

class ContractEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ContractEntity>(ContractTable) {

        /** 정적 팩토리 — Java의 `Contract.create(...)` 대응 */
        fun create(
            memberInfo: MemberInfo,
            stopInfo: StopInfo,
            request: CreateContractRequest
        ): ContractEntity = new(UuidV7.generate()) {
            stopId = stopInfo.id
            memberId = memberInfo.id
            contractName = request.contractName
            status = ContractStatus.PENDING
            autoRenewal = request.autoRenewal
            contractStartDate = request.startDate
            contractEndDate = request.endDate
        }
    }

    var stopId       by ContractTable.stopId
    var memberId     by ContractTable.memberId
    var contractName by ContractTable.contractName
    var status       by ContractTable.status
    var autoRenewal  by ContractTable.autoRenewal
    var contractStartDate by ContractTable.contractStartDate
    var contractEndDate   by ContractTable.contractEndDate
    var cancelledAt  by ContractTable.cancelledAt
    var cancelReason by ContractTable.cancelReason

    // 도메인 행위
    fun cancel(cancelledAt: OffsetDateTime, reason: String?) {
        this.status = ContractStatus.CANCELLED
        this.cancelledAt = cancelledAt
        this.cancelReason = reason
    }
}
```

---

### 5.6 gateway 모듈

엔티티 없음. Kotlin 클래스 전환만 수행한다.

```kotlin
// WebFlux 기반 — 코루틴 선택적 도입 가능
// 현재 Virtual Thread 기반 구조 유지 시 변경 최소화
@Configuration
class GatewaySecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain { ... }
}
```

---

## 6. 코드 패턴 대응 예시

### 6.1 Entity 선언

```java
// Before (Java + Lombok + JPA)
@Getter
@SuperBuilder
@Entity
@Table(name = "stop", schema = "stop")
public class Stop extends DateBaseEntity {
    @Column(name = "stop_id", nullable = false, unique = true)
    private String stopId;

    public static Stop requestOf(SimpleStopCreateRequest request, UUID registeredById) {
        return Stop.builder()
                .stopId(request.stopId())
                .registeredById(registeredById)
                .build();
    }
}
```

```kotlin
// After (Kotlin + Exposed DAO)
object StopTable : DateBaseTable("stop", "stop") {
    val stopId = varchar("stop_id", 50).uniqueIndex()
}

class StopEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<StopEntity>(StopTable) {
        fun create(request: SimpleStopCreateRequest, registeredById: UUID) =
            new(UuidV7.generate()) {
                stopId = request.stopId
                this.registeredById = registeredById
            }
    }
    var stopId by StopTable.stopId
}
```

### 6.2 Repository 조회

```java
// Before (JpaRepository)
public interface StopRepository extends JpaRepository<Stop, UUID> {
    Optional<Stop> findByStopId(String stopId);
    List<Stop> findByStopNameStartingWith(String stopName);
}
```

```kotlin
// After (Exposed DSL)
@Repository
class StopRepository {
    fun findByStopId(stopId: String): StopEntity? =
        StopEntity.find { StopTable.stopId eq stopId }.singleOrNull()

    fun findByStopNameStartingWith(prefix: String): List<StopEntity> =
        StopEntity.find { StopTable.stopName like "$prefix%" }.toList()
}
```

### 6.3 Enum 선언

```java
// Before (Java + Lombok + BaseEnum)
@Getter
@AllArgsConstructor
public enum StopType implements BaseEnum {
    VILLAGE_BUS("VILLAGE_BUS", "마을버스"),
    CENTER_LANE("CENTER_LANE", "중앙차로");

    private final String name;
    private final String displayName;
}
```

```kotlin
// After (Kotlin enum + BaseEnum)
enum class StopType(
    override val name: String,
    override val displayName: String
) : BaseEnum {
    VILLAGE_BUS("VILLAGE_BUS", "마을버스"),
    CENTER_LANE("CENTER_LANE", "중앙차로")
}
```

### 6.4 정적 팩토리 메서드

```java
// Before (Java static)
public static Contract create(MemberInfo memberInfo, StopInfo stopInfo, CreateContractRequest request) {
    return Contract.builder()
            .memberId(memberInfo.id())
            .stopId(stopInfo.id())
            .contractName(request.contractName())
            .status(ContractStatus.PENDING)
            .build();
}
```

```kotlin
// After (Kotlin companion object)
companion object : UUIDEntityClass<ContractEntity>(ContractTable) {
    fun create(memberInfo: MemberInfo, stopInfo: StopInfo, request: CreateContractRequest) =
        new(UuidV7.generate()) {
            memberId = memberInfo.id
            stopId = stopInfo.id
            contractName = request.contractName
            status = ContractStatus.PENDING
        }
}
```

### 6.5 @ConfigurationProperties

```kotlin
// Before (Java + Lombok)
@ConfigurationProperties(prefix = "hig.bulkhead")
@Getter
@Setter
public class BulkheadProperties {
    private String databaseName;
}

// After (Kotlin data class — @ConstructorBinding 권장)
@ConfigurationProperties(prefix = "hig.bulkhead")
data class BulkheadProperties(val databaseName: String)
```

---

## 7. 의존성 변경 목록

### 제거

| 의존성 | 이유 |
|--------|------|
| `org.projectlombok:lombok` | Kotlin 언어 기능으로 대체 |
| `org.projectlombok:lombok` (annotationProcessor) | 동일 |
| `gradle.plugin.com.ewerk.gradle.plugins:querydsl-plugin` | QueryDSL 제거 |
| `com.querydsl:querydsl-jpa:5.1.0:jakarta` | Exposed DSL로 대체 |
| `com.querydsl:querydsl-apt:5.1.0:jakarta` | 동일 |
| `org.springframework.boot:spring-boot-starter-data-jpa` | Exposed ORM으로 대체 |
| `jakarta.persistence:jakarta.persistence-api` | 전이 의존성으로 제거됨 |
| `org.hibernate.orm:hibernate-core` | 동일 |

### 추가

| 의존성 | 버전 | 역할 |
|--------|------|------|
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.1.20 | Kotlin 컴파일러 플러그인 |
| `org.jetbrains.kotlin:kotlin-allopen` | 2.1.20 | `kotlin-spring` (open class 자동화) |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.1.20 (BOM 관리) | Kotlin 표준 라이브러리 |
| `org.jetbrains.kotlin:kotlin-reflect` | 2.1.20 | Spring 리플렉션 지원 |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | Boot BOM 관리 | Kotlin data class 직렬화 |
| `org.jetbrains.exposed:exposed-spring-boot-starter` | 0.61.0 | Spring TX 통합 자동구성 |
| `org.jetbrains.exposed:exposed-core` | 0.61.0 | DSL 핵심 |
| `org.jetbrains.exposed:exposed-dao` | 0.61.0 | DAO API |
| `org.jetbrains.exposed:exposed-jdbc` | 0.61.0 | JDBC 연결 |
| `org.jetbrains.exposed:exposed-java-time` | 0.61.0 | `OffsetDateTime` 등 Java Time 지원 |
| `io.mockk:mockk` | 1.13.10 | Kotlin 친화적 Mock (Mockito 대체 선택) |

---

## 8. Virtual Threads / ScopedValue 호환성

### Kotlin 2.1.20 + JDK 25

- Kotlin 코드는 JVM 바이트코드로 컴파일되므로 Virtual Thread와 완전히 호환된다.
- `Thread.ofVirtual()`, `ScopedValue` 등 Java 25 API를 Kotlin에서 그대로 호출한다.
- `TransactionalBulkheadAspect`의 `ScopedValue.where(...)` 로직은 변경 없이 Kotlin으로 이식한다.

```kotlin
// ScopedValue 사용 — Java와 동일 API
private val IN_TRANSACTION: ScopedValue<Boolean> = ScopedValue.newInstance()

ScopedValue.where(IN_TRANSACTION, true).run {
    joinPoint.proceed()
}
```

### Exposed + Virtual Threads

Exposed는 JDBC blocking I/O를 사용한다. Virtual Thread는 blocking I/O에서 carrier thread를 점유하지 않으므로(JDK 21+) **코루틴 없이도** Virtual Thread 환경에서 효율적으로 동작한다.

> Coroutine 도입은 **선택 사항**. gateway 모듈에서 WebFlux + Coroutine 조합을 원하면 별도로 추가할 수 있다.

---

## 9. 테스트 전략

### 유지

```kotlin
// ByteBuddy experimental — Java 25 Mockito 호환성 유지
tasks.named<Test>("test") {
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}
```

### MockK 권장 (Mockito 대체)

```kotlin
// Before (Mockito)
@Mock private StopRepository stopRepository;
when(stopRepository.findByStopId("001")).thenReturn(Optional.of(stop));

// After (MockK)
private val stopRepository = mockk<StopRepository>()
every { stopRepository.findByStopId("001") } returns stopEntity
```

### Repository 통합 테스트

```kotlin
@SpringBootTest
@Transactional
class StopRepositoryTest {

    @Autowired
    private lateinit var stopRepository: StopRepository

    @Test
    fun `stopId로 정류소를 조회한다`() {
        // given
        val entity = StopEntity.create(request, registeredById)

        // when
        val found = stopRepository.findByStopId(entity.stopId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.stopId).isEqualTo(entity.stopId)
    }
}
```

### Guard / Validator 단위 테스트

```kotlin
// Guard는 익명 Stub 대신 MockK 사용
class StopCommandGuardTest {
    private val stopRepository = mockk<StopRepository>()
    private val guard = StopCommandGuard(stopRepository)

    @Test
    fun `중복 stopId가 있으면 예외를 던진다`() {
        every { stopRepository.existsByStopId("001") } returns true
        assertThrows<BusinessException> { guard.validateNotDuplicate("001") }
    }
}
```

---

## 10. 마이그레이션 실행 체크리스트

```
[ ] 1. 루트 build.gradle → build.gradle.kts 전환
        - Kotlin 플러그인 추가
        - Lombok / QueryDSL 제거
        - Exposed BOM 버전 변수 추가

[ ] 2. common 모듈
        - src/main/java → src/main/kotlin 이동
        - BaseTable, DateBaseTable 작성
        - UuidV7 object 작성
        - BaseEnum → Kotlin interface
        - Exception 계층 Kotlin 전환
        - ApiResponse → Kotlin data class
        - AOP 클래스 Kotlin 전환 (kotlin-spring으로 open 자동화)
        - QueryDslConfig 삭제
        - AutoConfiguration.imports에서 QueryDslConfig 항목 제거

[ ] 3. auth-contract 모듈
        - MemberPrincipal record → Kotlin data class
        - MemberType, Permission, MemberCategory → Kotlin enum
        - JwtProvider → Kotlin class
        - 필터/인터셉터/리졸버 → Kotlin class

[ ] 4. gateway 모듈
        - 모든 .java → .kt 전환 (엔티티 없음, 구조 변경 최소)
        - 빌드 확인

[ ] 5. iam 모듈
        - Member, Role, Permission, MemberRole, RolePermission → Exposed Table + DAO
        - Repository 인터페이스 → Exposed DSL 구현체
        - Service, Guard, Validator → Kotlin class
        - application.yml — jpa 항목 제거

[ ] 6. stop 모듈
        - Stop, StopUpdateHistory → Exposed Table + DAO
        - StopType, ChangeSource → Kotlin enum
        - StopRepository → Exposed DSL 구현체
        - Service, Guard → Kotlin class
        - application.yml — jpa 항목 제거

[ ] 7. reservation 모듈
        - Contract, ContractDetail → Exposed Table + DAO
        - ContractStatus, PaymentMethod, PaymentCycle → Kotlin enum
        - Service, Facade, Client → Kotlin class
        - application.yml — jpa 항목 제거

[ ] 8. 전체 통합 빌드 검증
        ./gradlew clean build
        docker-compose -f docker-compose-local.yml up --build -d
        # 각 서비스 Health Check 확인
        curl http://localhost:8080/health-check
        curl http://localhost:8181/health-check
        curl http://localhost:8182/health-check
        curl http://localhost:8183/health-check
```

---

## 11. 주의 사항 및 알려진 제약

### Spring AOP — Kotlin final 클래스 문제

Kotlin 클래스는 기본이 `final`이다. `@Aspect`, `@Component`, `@Service`, `@Repository`, `@Configuration`이 붙은 클래스는 Spring이 프록시를 생성하기 위해 상속이 필요하다.

**해결**: `kotlin-spring` (`kotlin-allopen`) 플러그인이 위 어노테이션이 붙은 클래스를 자동으로 `open`으로 처리한다. 루트 `build.gradle.kts`에 `apply(plugin = "kotlin-spring")`을 반드시 포함해야 한다.

### @Transactional vs transaction {} 혼용 금지

```kotlin
// 잘못된 예 — Spring @Transactional 안에서 transaction {} 중첩
@Transactional
fun doSomething() {
    transaction {  // 새로운 Exposed 트랜잭션 — 외부 Spring TX와 분리됨
        StopTable.insert { ... }
    }
}

// 올바른 예 — Spring @Transactional만 사용
@Transactional
fun doSomething() {
    StopEntity.new(UuidV7.generate()) { ... }  // Spring TX 컨텍스트 내에서 동작
}
```

### application.yml — JPA 설정 제거 대상

```yaml
# 제거 대상 (Exposed 전환 후 불필요)
spring:
  jpa:
    show-sql: true
    open-in-view: false
    hibernate:
      ddl-auto: none
```

### Liquibase — 변경 없음

Exposed는 DDL 생성에 관여하지 않는다. 기존 Liquibase XML 마이그레이션 파일은 그대로 유지한다.

### @ConfigurationProperties — data class 권장

```kotlin
// 경고 없는 방식 — primary constructor 바인딩
@ConfigurationProperties(prefix = "hig.bulkhead")
data class BulkheadProperties(val databaseName: String)
```

### Exposed 낙관적 잠금

Exposed는 JPA `@Version`에 대응하는 내장 낙관적 잠금이 없다. `version` 컬럼을 직접 관리하고 `updateWhere` 절에 버전 조건을 포함한다.

```kotlin
val updated = StopTable.update({ StopTable.id eq id and (StopTable.version eq currentVersion) }) {
    it[stopName] = newName
    it[version] = currentVersion + 1
}
if (updated == 0) throw OptimisticLockException("Stop $id was modified concurrently")
```

### Exposed + H2 테스트 호환성

H2 in-memory DB는 Exposed와 완전히 호환된다. `exposed-spring-boot-starter`가 자동으로 H2 방언을 감지한다.

```yaml
# test/resources/application.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
```
