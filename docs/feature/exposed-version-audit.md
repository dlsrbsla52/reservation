# Exposed 버전 점검 및 업그레이드 판단

> 점검일: 2026-07-14  
> 대상: reservation 멀티 모듈 프로젝트

## 결론

프로젝트는 Exposed **`1.0.0` 정식 버전**을 일관되게 사용하고 있다. `1.0.0`은 시험 버전이 아니며, 코드도 Exposed 1.x API와 호환되는 방식으로 작성됐다.

다만 현재 최신 안정 버전은 **`1.3.1`**이므로, 버전 자체는 최신이 아니다. 신규 기능 도입이나 JDBC coroutine 트랜잭션 안정성 개선이 필요하다면 `1.3.1` 업그레이드를 검토한다.

| 항목 | 확인 결과 |
|---|---|
| 선언 버전 | `1.0.0` |
| Gradle 실제 해석 버전 | `exposed-core:1.0.0` |
| 현재 최신 안정 버전 | `1.3.1` |
| 모듈 버전 정렬 | `core`, `dao`, `jdbc`, `java-time`, `spring-boot4-starter` 모두 `1.0.0` |
| Spring Boot 조합 | `exposed-spring-boot4-starter` 사용 — Spring Boot 4 구성에 적합 |
| API 사용 | `org.jetbrains.exposed.v1.*` 패키지 사용 — 1.x 규약에 적합 |

## 현재 구성

루트 빌드에서 Exposed 버전을 한 곳에서 관리한다.

```kotlin
// build.gradle.kts
extra["exposedVersion"] = "1.0.0"
```

공통 모듈은 같은 버전으로 필요한 Exposed artifact를 선언한다.

```kotlin
api("org.jetbrains.exposed:exposed-spring-boot4-starter:$exposedVersion")
api("org.jetbrains.exposed:exposed-core:$exposedVersion")
api("org.jetbrains.exposed:exposed-dao:$exposedVersion")
api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
api("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
```

`dependencyInsight` 검증에서도 전이 의존성에 의해 다른 버전으로 대체되지 않고 `1.0.0`이 선택됐다.

```text
org.jetbrains.exposed:exposed-core:1.0.0 (selected by rule)
```

## 1.0 API 사용 적합성

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

## 1.3.1 업그레이드로 얻는 변화

| 버전 | 관련 변화 | 프로젝트 영향 |
|---|---|---|
| 1.1.0 | coroutine 취소 시 `suspendTransaction` 연결 누수 수정, `SELECT FOR UPDATE`의 stale entity 수정 | JDBC coroutine 또는 DAO 동시성 경로가 있으면 유의미 |
| 1.2.0 | 컬럼 comment, Oracle `FOR UPDATE SKIP LOCKED`, check constraint 변경 감지 강화 | Liquibase 중심인 현재 구조에는 직접 영향이 작음 |
| 1.3.0 | Exposed Gradle migration plugin, UUID v4/v7 자동 생성, VECTOR 타입 | DB-first 코드 생성 기능은 제공하지 않음. DB 변경은 기존 원칙대로 Liquibase 유지 |
| 1.3.1 | `ResultRow` 캐시 성능 개선 및 Spring Native/nullable type 수정 | DSL 조회량이 많다면 성능·안정성 이점 가능 |

> [!important]
> Exposed Gradle plugin은 Exposed `Table` 정의와 DB 스키마의 차이로 SQL migration을 생성한다. 이 프로젝트의 DB 변경 원칙은 Liquibase changelog이므로, 도입하더라도 생성 SQL을 Liquibase changelog로 이관·검토하는 절차가 필요하다.

## 권장 판단

- 현재 기능이 안정적으로 동작하고 `suspendTransaction`·R2DBC를 사용하지 않는다면, `1.0.0` 유지가 즉시 장애 요인은 아니다.
- 새 기능 사용, 성능 개선, 1.x bug fix 반영을 원하면 `1.3.1`로 올리는 것을 권장한다.
- 업그레이드는 루트 `build.gradle.kts`의 `exposedVersion` 한 곳을 변경하는 방식으로 시작할 수 있다.
- Java UUID API는 유지한다. Kotlin `Uuid`로 바꾸는 작업은 별도 타입 마이그레이션으로 취급한다.

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
