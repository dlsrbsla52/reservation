# Exposed 버전 점검 및 업그레이드 판단

> 점검일: 2026-07-14  
> 대상: reservation 멀티 모듈 프로젝트

## 결론

프로젝트는 기존 점검 결과에 따라 Exposed를 **`1.3.1`**로 업그레이드했으며, 모든 artifact를 같은
버전으로 정렬했다. 코드는 계속 Exposed 1.x API와 호환되는 패키지와 Java UUID API를 사용한다.

| 항목 | 확인 결과 |
|---|---|
| 선언 버전 | `1.3.1` |
| Gradle 실제 해석 버전 | `exposed-core:1.3.1` |
| 점검 당시 최신 안정 버전 | `1.3.1` |
| 모듈 버전 정렬 | `core`, `dao`, `jdbc`, `java-time`, `spring-boot4-starter` 모두 `1.3.1` |
| Spring Boot 조합 | `exposed-spring-boot4-starter` 사용 — Spring Boot 4 구성에 적합 |
| API 사용 | `org.jetbrains.exposed.v1.*` 패키지 사용 — 1.x 규약에 적합 |

## 현재 구성

루트 빌드에서 Exposed 버전을 한 곳에서 관리한다.

```kotlin
// build.gradle.kts
extra["exposedVersion"] = "1.3.1"
```

공통 모듈은 같은 버전으로 필요한 Exposed artifact를 선언한다.

```kotlin
api("org.jetbrains.exposed:exposed-spring-boot4-starter:$exposedVersion")
api("org.jetbrains.exposed:exposed-core:$exposedVersion")
api("org.jetbrains.exposed:exposed-dao:$exposedVersion")
api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
api("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
```

`dependencyInsight` 검증에서도 전이 의존성에 의해 다른 버전으로 대체되지 않고 `1.3.1`이 선택됐다.

```text
org.jetbrains.exposed:exposed-core:1.3.1 (selected by rule)
```

## 1.x API 사용 적합성

### 패키지 전환

Exposed 1.0부터 패키지가 `org.jetbrains.exposed.sql.*`에서 `org.jetbrains.exposed.v1.*`로 변경됐다. 프로젝트는 새 패키지를 사용한다.

```kotlin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
```

### Java UUID 사용

프로젝트 모델이 `java.util.UUID`를 사용하므로 다음 1.x 전용 Java UUID API 선택은 적절하다.

```kotlin
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
```

기본 `Table.uuid()`는 Kotlin `Uuid` 기반이다. 현재처럼 Java UUID를 유지하려면 `javaUUID()`, `dao.id.java.UUIDTable`, `dao.java.UUIDEntity`를 계속 사용해야 한다.

## 1.3.1 업그레이드로 반영된 변화

| 버전 | 관련 변화 | 프로젝트 영향 |
|---|---|---|
| 1.1.0 | coroutine 취소 시 `suspendTransaction` 연결 누수 수정, `SELECT FOR UPDATE`의 stale entity 수정 | JDBC coroutine 또는 DAO 동시성 경로가 있으면 유의미 |
| 1.2.0 | 컬럼 comment, Oracle `FOR UPDATE SKIP LOCKED`, check constraint 변경 감지 강화 | Liquibase 중심인 현재 구조에는 직접 영향이 작음 |
| 1.3.0 | Exposed Gradle migration plugin, Kotlin `Uuid`의 V4/V7 생성 버전 선택, VECTOR 타입 | DB-first 코드 생성 기능은 제공하지 않음. Java UUID 기반 현재 모델에는 직접 적용되지 않음 |
| 1.3.1 | `ResultRow` 캐시 성능 개선 및 Spring Native/nullable type 수정 | DSL 조회량이 많다면 성능·안정성 이점 가능 |

> [!important]
> Exposed Gradle plugin은 Exposed `Table` 정의와 DB 스키마의 차이로 SQL migration을 생성한다. 이 프로젝트의 DB 변경 원칙은 Liquibase changelog이므로, 도입하더라도 생성 SQL을 Liquibase changelog로 이관·검토하는 절차가 필요하다.

## 권장 판단

- 현재 적용 버전은 `1.3.1`이며, 루트 `build.gradle.kts`의 `exposedVersion` 한 곳에서 관리한다.
- 다음 업그레이드에서도 모든 Exposed artifact의 버전을 함께 정렬하고 `dependencyInsight`로 실제 해석 버전을 확인한다.
- Java UUID API는 유지한다. Kotlin `Uuid`로 바꾸는 작업은 별도 타입 마이그레이션으로 취급한다.

## 현재 `UuidV7` 구현과 Kotlin 표준 생성기

프로젝트의 `modules/common/.../UuidV7.kt`는 모든 Entity 생성 경로에서 `java.util.UUID` UUIDv7을 미리 할당하는 facade다. 현재 비트 구성은 RFC 9562 UUIDv7 형식에 맞는다.

```kotlin
fun generate(): UUID {
    val timestamp = Instant.now().toEpochMilli()
    val msb = (timestamp shl 16) or (7L shl 12) or (random.nextLong() and 0xFFFL)
    val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
    return UUID(msb, lsb)
}
```

| 구성 | 현재 구현 |
|---|---|
| 상위 48 bit | Unix epoch milliseconds |
| version | UUIDv7 (`0111`) |
| variant | RFC 9562 variant (`10`) |
| 나머지 bit | `SecureRandom` 난수 |
| Exposed 호환 | `javaUUID()`, `UUIDTable`, `UUIDEntity`와 직접 호환 |

### 동일 밀리초의 단조성

현재 구현의 timestamp는 밀리초 단위이고 나머지 74 bit는 난수다.

```text
같은 1ms
UUID #1 = [timestamp][random suffix A]
UUID #2 = [timestamp][random suffix B]
```

따라서 같은 밀리초에 생성한 두 값의 순서는 보장되지 않는다. 시간이 바뀌면 timestamp prefix가 커지므로 장기적으로는 B-tree 오른쪽 시간 영역에 집중되지만, 주석의 `monotonically increasing`은 엄밀히 성립하지 않는다.

Kotlin `2.3.20` 표준 라이브러리의 `Uuid.generateV7()`은 같은 프로세스 생명주기에서 counter를 사용해 **엄격한 단조 증가**를 보장한다. 단일 JVM에서 생성한 PK라면 B-tree 정렬 기준으로 계속 오른쪽 끝에 삽입된다.

다만 여러 Pod/JVM 사이의 전역 단조 증가는 보장하지 않는다. clock skew, 재시작, 과거 데이터 backfill이 있으면 일부 중간 삽입은 발생할 수 있다. 그래도 UUIDv4나 현재 구현보다 index locality가 좋다.

### 권장 교체안

호출부와 DB schema를 바꾸지 않고 `UuidV7` 내부만 표준 구현으로 교체한다.

```kotlin
package com.media.bus.common.entity.common

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object UuidV7 {

    @OptIn(ExperimentalUuidApi::class)
    fun generate(): UUID =
        Uuid.generateV7().toJavaUuid()
}
```

| 항목 | 현재 직접 구현 | Kotlin `Uuid.generateV7()` |
|---|---|---|
| 동일 ms 정렬 | 랜덤 순서 | 같은 프로세스 내 엄격한 단조 증가 |
| 난수 | `SecureRandom` | JVM에서 `SecureRandom` |
| 호출부/DB schema | 유지 | `toJavaUuid()`로 그대로 유지 |
| 상태·시계 처리 | 직접 책임 | 표준 라이브러리에 위임 |

> [!important]
> `Uuid.generateV7()`은 아직 `ExperimentalUuidApi` opt-in이 필요하다. 그러나 이 프로젝트의 Kotlin `2.3.20`에는 API와 `toJavaUuid()` 변환 함수가 포함되어 있어, Java UUID 기반 Exposed DAO 구조를 유지할 수 있다.

## 업그레이드 검증 절차

```bash
./gradlew :modules:common:dependencyInsight \
  --dependency exposed-core \
  --configuration runtimeClasspath \
  --no-configuration-cache

./gradlew :modules:common:test
./gradlew :modules:iam:test
./gradlew :modules:stop:test
./gradlew :modules:reservation:test
```

추가로 PostgreSQL 통합 테스트에서 UUID 매핑, `timestampWithTimeZone`, DAO 관계 조회, Liquibase 적용 후 repository CRUD를 확인한다.

## 참고 자료

- [Exposed 1.0 정식 출시](https://blog.jetbrains.com/kotlin/2026/01/exposed-1-0-is-now-available/)
- [0.61.0 → 1.0.0 마이그레이션 가이드](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)
- [공식 변경 이력](https://github.com/JetBrains/Exposed/blob/main/CHANGELOG.md)
- [Kotlin `Uuid.generateV7()` API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.uuid/-uuid/-companion/generate-v7.html)
- [RFC 9562 UUIDv7](https://datatracker.ietf.org/doc/html/rfc9562#section-5.7)
