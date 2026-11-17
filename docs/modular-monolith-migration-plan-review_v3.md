# 모듈러 모놀리스 회귀 계획 리뷰 v3

- 검토일: 2026-11-17
- 대상: [모듈러 모놀리스 회귀 계획](modular-monolith-migration-plan.md), 1·2차 리뷰 반영 후 **513행** 버전
- 코드 기준: `bigbang/modular-monolith`, `HEAD 5a64caf`
- 계획서 SHA-256: `df29ecad0289ceb05a74c32db0a40e8f951165b53b0793e87d55a1e9e62d0a3c`
- 검증 범위: 계획·기존 리뷰·현재 소스·Gradle·설정·배포 파일의 정적 대조, 관련 공식 문서 확인
- 판정: **아키텍처 방향은 일관된다. 다만 과부하 오류 변환에는 확정된 상태 코드 불일치가 있고, OpenAPI 접근 정책·app 빌드 설정·프록시 신뢰 범위는 보완이 필요하다.**

## 1. 종합 판단

소비자 포트와 `app` 어댑터를 사용하고 도메인 모듈 사이의 컴파일 의존을 금지하는 구조는 서로 모순되지 않는다. 제공자의 기존 Facade를 유지하면서 HTTP만 제거하는 방향도 적절하다.

이전 리뷰의 주요 지적은 반영되어 있다. 보안 설정 통합, `PasswordEncoder` 보존, 제공자 검증 이관, 공유 internal 매핑의 마지막 삭제, S2S 참조 정리, 관리 포트 구분, local 풀·퍼밋 조정, MDC 정리 순서, 메모리 제한, 이미지·볼륨 보존을 미반영 사항으로 다시 지적하지 않는다.

이번 검토에서는 **확정된 모순**, **구현 명세 누락**, **코드와 다른 설명**을 구분한다. 아직 존재하지 않는 `app`의 장애를 재현한 것은 아니다. 아래 행 번호는 모두 검토 시점의 계획서 기준이다.

## 2. 수정 우선순위

| ID | 우선순위 | 유형 | 발견 사항 | 반영 시점 |
|---|---|---|---|---|
| V3-01 | 높음 | 코드와 충돌하는 수정안 | 권장한 Bulkhead 예외 변환은 `429` 대신 `400`을 반환함 | 3단계 어댑터·오류 계약 |
| V3-02 | 중간 | 접근 정책과 작업 목적 충돌 | OpenAPI 생성은 HTTP 문서 URL을 요구하지만 허용 목록에는 문서 생성 예외가 없음 | 2단계 보안·OpenAPI 이관 |
| V3-03 | 중간 | 실행 설정 누락 | 새 `app`에도 루트의 `bootJar=false`가 적용되며 직접 사용하는 공통 타입의 컴파일 의존도 필요함 | 2단계 모듈 생성 |
| V3-04 | 중간 | 보안 설정 책임 혼동·범위 누락 | Caddy와 Tomcat의 신뢰 프록시는 별개이며 감사 로그에도 같은 IP 추출 로직이 남음 | 4단계 S8·배포 구성 |

V3-02~04는 누락된 선택을 구현 시 보완하면 해결할 수 있다. 현재 운영 장애나 인증 우회를 확인했다는 판정은 아니다.

## 3. 주요 발견 사항

### V3-01. `BusinessException(CommonResult.BULKHEAD_FULL)`만으로는 429가 보존되지 않는다

**계획 근거:** 354행은 직접 전파되는 `BulkheadFullException`을 `BusinessException(CommonResult.BULKHEAD_FULL)`로 변환하도록 권장한다. 363행은 기존 HTTP 상태와 예약 항목별 코드의 유지를 완료 조건으로 둔다.

**현재 코드의 확정된 동작:**

| 구성 요소 | 코드상 동작 |
|---|---|
| `CommonResult` | 기본 HTTP 상태는 `BAD_REQUEST`. `BULKHEAD_FULL("00270", ...)`은 별도 상태를 지정하지 않아 **400** |
| `BusinessException` | `httpStatus` 기본값은 `result.httpStatus()` |
| `ExceptionAdvisor.businessExceptionHandler` | `error.httpStatus`를 그대로 응답 |
| `ExceptionAdvisor.bulkheadFullHandler` | 별도로 `TOO_MANY_REQUESTS`를 지정하므로 **429** |
| 기존 `S2SRestClientFactory` | 하위 응답의 상태를 `BusinessException(..., httpStatus = httpStatus)`로 명시하여 **429** 보존 |

근거: [CommonResult.kt](../modules/common/src/main/kotlin/com/media/bus/common/result/type/CommonResult.kt) 17·65행, [BusinessException.kt](../modules/common/src/main/kotlin/com/media/bus/common/exceptions/BusinessException.kt) 18–23행, [ExceptionAdvisor.kt](../modules/common/src/main/kotlin/com/media/bus/common/web/advisor/ExceptionAdvisor.kt) 39–47·129–135행, [S2SRestClientFactory.kt](../modules/common/src/main/kotlin/com/media/bus/common/client/S2SRestClientFactory.kt) 94–98행.

따라서 계획대로 어댑터에서 변환하면 예약 생성의 항목 `errorCode=00270`은 복구되지만, **IAM 관리자 API처럼 예외를 바깥 핸들러까지 전달하는 경로는 HTTP 400이 된다.** 항목 코드가 같다는 것만으로 외부 오류 계약이 같아지는 것은 아니다.

**수정안:** 포트 경계에서 변환할 때 상태를 명시한다.

```kotlin
throw BusinessException(
    result = CommonResult.BULKHEAD_FULL,
    cause = error,
    httpStatus = HttpStatus.TOO_MANY_REQUESTS,
)
```

또는 `CommonResult.BULKHEAD_FULL` 자체에 429를 지정하되, 공통 enum 변경의 영향을 함께 확인한다. 두 방법 중 하나를 계획에 확정하면 된다.

`Retry-After`는 별도로 판단해야 한다. 현재 직접 처리되는 `BulkheadFullException` 응답에는 `Retry-After: 1`이 있지만, S2S 변환기는 그 헤더를 전달하지 않는다. 따라서 모든 경로의 기존 헤더가 같았다고 가정하지 말고, 직접 호출 경로와 기존 S2S 경로를 나누어 유지 또는 통일 정책을 정한다.

**완료 조건:**

- 예약 생성 중 정류소 제공자 과부하: 항목의 `errorCode=00270` 유지.
- IAM 관리자 위임 API 중 제공자 과부하: HTTP 429와 본문 `code=00270` 유지.
- 직접 공개 API의 과부하: 기존 상태·코드와 `Retry-After` 정책 확인.

### V3-02. OpenAPI 생성 URL과 공개 포트 차단 정책을 함께 설계해야 한다

**계획 근거:** 252행은 OpenAPI 플러그인과 집계 작업을 2단계에서 `app`으로 옮긴다. 반면 294·315·426행은 허용 API prefix 밖의 경로와 `/api-docs`·Swagger를 8080에서 차단한다. 268·429행의 문서 기능 비활성화는 운영 프로필에 한정되어 있지만, JWT 필터의 경로 허용 정책에는 `openapi` 프로필 예외가 없다.

현재 세 모듈의 Gradle 설정은 `http://localhost:8181|8182|8183/api-docs.yaml`을 다운로드하고, 앱을 `--spring.profiles.active=openapi`로 실행한다. 이 작업은 소스만 분석하는 문서 생성기가 아니다. 사용 중인 플러그인 1.9.0은 앱을 실행한 뒤 문서 URL에 HTTP 요청을 보낸다. [springdoc 플러그인 1.9.0 공식 설명](https://github.com/springdoc/springdoc-openapi-gradle-plugin/blob/1.9.0/README.md).

근거: [IAM build.gradle.kts](../modules/iam/build.gradle.kts) 20–31행, [stop build.gradle.kts](../modules/stop/build.gradle.kts), [reservation build.gradle.kts](../modules/reservation/build.gradle.kts), [루트 build.gradle.kts](../build.gradle.kts) 118–126행.

플러그인 이동 시 URL만 8080으로 바꾸면 문서 경로가 허용 prefix 밖이어서 차단된다. `springdoc.api-docs.enabled=true`만 설정해도 앞단 필터를 통과하지 못한다. 관리 포트로 옮기는 선택지도 계획에 있지만, 생성 작업의 URL·문서 노출 설정·관리 포트 접근 정책을 연결하는 구체적인 선택은 없다.

**수정안:** 다음 중 하나를 2단계에 확정한다.

- **권장:** 문서 생성 전용 `openapi` 프로필에서만 루프백에 바인딩하고 `/api-docs.yaml` 및 필요한 그룹 경로를 허용한다. 일반 실행·운영의 8080 차단 정책은 유지한다.
- 관리 포트에서 문서를 생성한다면 실제 노출 URL, springdoc 관리 포트 설정, 허용할 actuator 경로, Gradle 다운로드 URL을 함께 명시한다.

또한 현재 작업트리에는 세 모듈의 `application-openapi.yml`이 없다. Gradle의 프로필 인수와 `bootJar` 제외 규칙은 존재하지만, 복사해 올 문서 전용 설정 파일이 이미 준비되어 있다고 가정하면 안 된다. 문서 생성에 사용할 DB·Redis·더미 설정 또는 격리 방식을 별도로 정해야 한다.

**완료 조건:** `./gradlew generateAllOpenApiDocs`를 실제 실행해 의도한 API 명세가 생성되는지 확인한다. 일반 `clean build`와 `bootJar` 성공만으로 대체하지 않는다. 최종 명세에 internal API가 없고, 일반 실행·운영 프로필의 문서 차단 조건도 별도로 통과해야 한다.

### V3-03. `app`의 실행 JAR·컴파일 의존을 명시해야 한다

**계획 근거:** 250–253행은 `app` 추가와 기존 세 모듈의 라이브러리화를 설명하고, 310·365행은 `app:bootJar`를 검증한다. 그러나 새 실행 모듈의 Gradle 재정의는 적혀 있지 않다.

**현재 코드에서 확인한 제약:**

1. 루트 `subprojects`는 모든 하위 모듈에 `bootJar=false`, `jar=true`를 적용한다. 새 `app`도 예외가 아니다. 기존 실행 모듈들은 각자 이를 반대로 재정의하고 있다.
2. 기존 `iam`·`stop`·`reservation`의 `common`·`auth-contract` 의존은 `implementation`이다. `app`이 도메인 세 모듈에만 의존하면 그 구현 의존성이 `app`의 컴파일 클래스패스에 자동 노출되지는 않는다. `app`은 보안 설정에서 `JwtProvider`·`MemberPrincipal`, 어댑터에서 공통 응답 타입 등을 직접 사용하므로 해당 의존도 필요하다.
3. Dockerfile은 `build/libs/*.jar`를 `app.jar`로 복사한다. 실행 JAR과 plain JAR을 둘 다 만들 경우를 피하거나 복사 대상을 정확하게 지정해야 한다.

근거: [루트 build.gradle.kts](../build.gradle.kts) 55–57행, [IAM build.gradle.kts](../modules/iam/build.gradle.kts) 5–17행, [auth-contract build.gradle.kts](../modules/auth-contract/build.gradle.kts), [Dockerfile](../Dockerfile) 70행. 프레임워크 근거: [Spring Boot 실행·plain JAR 설정](https://docs.spring.io/spring-boot/gradle-plugin/packaging.html), [Gradle API와 implementation 의존 구분](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation).

**수정안:** 2-1에 다음 사항을 추가한다.

```kotlin
// modules/app/build.gradle.kts의 핵심 설정 예시
dependencies {
    implementation(project(":modules:iam"))
    implementation(project(":modules:stop"))
    implementation(project(":modules:reservation"))
    implementation(project(":modules:auth-contract"))
    implementation(project(":modules:common"))
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    archiveFileName.set("app.jar")
}
tasks.named("jar") { enabled = false }
```

`common`은 `auth-contract`의 `api` 의존을 통해서도 보일 수 있지만, 위 예시는 `app`이 직접 사용하는 모듈을 명시한다. 이는 도메인 모듈끼리 의존하지 않는다는 원칙과 충돌하지 않는다. Dockerfile도 고정된 실행 산출물 `modules/app/build/libs/app.jar`를 복사하게 맞춘다.

**완료 조건:** `:modules:app:bootJar`가 `SKIPPED`가 아니라 실행되어 실제 산출물을 만들고, 해당 JAR을 사용하는 최종 이미지가 기동한다. `app`의 컴파일 클래스패스에는 직접 사용하는 타입이 있으며, 도메인 세 모듈 사이에는 새 의존이 없어야 한다.

### V3-04. Caddy의 `trusted_proxies`와 Tomcat의 프록시 신뢰는 별개다

**계획 근거:** S8(431행)은 `server.forward-headers-strategy: native`와 Caddy의 `trusted_proxies` 설정 후 `request.remoteAddr`를 사용하라고 한다. 373행의 배포 구성은 Caddy를 선택 사항으로 둔다.

이 설명은 서로 다른 두 구간의 설정을 충분히 구분하지 않는다.

| 구간 | 신뢰할 대상과 책임 |
|---|---|
| 클라이언트 또는 상위 프록시 → Caddy | Caddy의 `trusted_proxies`는 **Caddy 앞에 있는** 프록시의 전달 헤더를 신뢰하는 설정 |
| Caddy → app/Tomcat | Tomcat의 `RemoteIpValve`와 `server.tomcat.remoteip.*`가 app에서 신뢰할 프록시를 결정 |

Caddy가 인터넷의 첫 진입점이라면 상위 프록시를 임의로 신뢰하도록 추가할 필요가 없다. 기본적으로 Caddy는 위조 방지를 위해 외부에서 온 `X-Forwarded-*` 값을 그대로 신뢰하지 않는다. [Caddy 전달 헤더 공식 설명](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#defaults).

`NATIVE`는 웹 서버의 전달 헤더 처리를 사용한다는 뜻이며, Caddy의 설정을 Tomcat에 전달하거나 앱의 신뢰 범위를 자동으로 Caddy 한 곳으로 제한한다는 뜻은 아니다. [Spring Boot 프록시·Tomcat 설정](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server).

**추가 누락:** `X-Forwarded-For` 첫 값을 직접 읽는 곳은 `AuthCookieSupport`뿐만 아니라 `AuditLogService.extractClientIp`에도 있다. 세션 기기 정보만 바꾸면 감사 로그는 다른 추출 정책을 계속 사용한다.

근거: [AuthCookieSupport.kt](../modules/iam/src/main/kotlin/com/media/bus/iam/auth/support/AuthCookieSupport.kt) 70–79행, [AuditLogService.kt](../modules/iam/src/main/kotlin/com/media/bus/iam/audit/service/AuditLogService.kt) 112–118행.

**수정안:**

- 운영 배포에서 Caddy 또는 다른 TLS 프록시가 실제로 어디에 배치되는지 확정한다. Caddy가 선택 사항이면 대체 프록시 구성도 같은 책임을 충족해야 한다.
- 프록시 뒤의 app 8080은 외부에서 프록시를 우회하여 접근할 수 없도록 바인딩·포트 공개 범위를 정한다. Caddy가 컨테이너인지 호스트 프로세스인지에 맞춰 설정한다.
- Tomcat의 신뢰 대상은 실제 프록시 경로에 맞추고, 세션 정보와 감사 로그가 동일하게 검증된 `remoteAddr`를 사용하게 한다.

**완료 조건:** 임의의 `X-Forwarded-For`를 포함한 요청에서도 세션·감사 로그에 위조 IP가 기록되지 않으며, 프록시를 거친 정상 요청의 실제 클라이언트 IP가 보존된다. app 직접 접근 가능 여부도 배포 구성으로 확인한다.

## 4. 코드와 다른 설명 및 검증 범위 정정

아래는 추가 아키텍처 결함으로 집계하지 않는다. 계획서가 “코드에서 확인했다”고 적은 설명이나 검증 근거를 정확하게 만드는 수정이다.

| 계획 위치 | 현재 코드와의 차이 | 수정 방향 |
|---|---|---|
| 39·43·227행: `AdminStopPriceController`가 S2S 클라이언트를 직접 사용 | 현재 생성자는 `AdminStopPriceFacade` 하나만 주입하며 모든 업무 위임이 Facade를 거친다. 클라이언트 이름은 주석에만 남아 있다 | 포트 교체 대상은 Facade다. 컨트롤러에는 4곳의 `X-User-Id` 교체 작업이 남는다고 구분한다. [컨트롤러](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/controller/AdminStopPriceController.kt) 35–37행 |
| 32행: `InternalStopController`가 `StopType.parseFilter` 실행 | 현재 컨트롤러는 `stopsType: String?`를 전달하고, `StopService.getBusStopPage`가 82행에서 변환한다 | 이 검증은 이미 Service에 있음을 표시한다. 컨트롤러에서 수행하는 `StopSearchCriteria.of`, `StopBoundsCriteria.of`, `@Valid`와 구분한다. [StopService](../modules/stop/src/main/kotlin/com/media/bus/stop/service/StopService.kt) |
| 185행: 어댑터 예시의 `StopQueryService` | 현재 해당 클래스가 없고 조회 진입점은 `StopService` | 기존 진입점을 유지한다면 예시를 `StopService`로 바꾼다. 분리할 의도라면 신규 리팩터링임을 명시한다 |
| 356행: DTO 변환 누락을 “컴파일러가 잡는다” | 기본값이 있는 필드는 생략해도 컴파일된다. 예를 들어 IAM `StopInfo.unitPrice`는 `BigDecimal? = null` | 필수 인수·타입 오류만 컴파일로 확인된다. 단가가 있는 정류소의 `unitPrice`가 어댑터를 거친 뒤에도 유지되는 계약 테스트를 둔다. [StopInfo](../modules/iam/src/main/kotlin/com/media/bus/iam/client/stop/dto/StopInfo.kt) |
| 244행: 직접 생성한 객체는 검증이 실행되지 않음 | 이는 `@Validated`의 프록시 기반 메서드 검증에 해당한다. 240행에서 권장한 명시적 검증·예외 발생은 직접 호출해도 실행된다 | 검증 방식을 구분한다. 수동 Validator는 단위 테스트, 프록시 기반 검증은 실제 프록시를 통과하는 테스트로 확인한다 |
| 329·340행: 컨트롤러 삭제·테스트의 `TokenProvider` mock 정리 | `InternalContractControllerTest`는 삭제할 컨트롤러를 직접 생성한다. `IamApiContractTest`는 mock뿐 아니라 삭제할 `IamApi`·`IamServiceClient`·`S2SRestClientFactory` 전체에 의존한다 | 전자는 검색 범위·페이지 보정 검증을 새 진입점 테스트로 옮긴다. 후자는 전송 계약 검증을 폐기하고 회원 조회 결과·실패 계약을 어댑터 테스트로 이관한다. 테스트 전체의 유지·폐기 방침을 적는다. [컨트롤러 테스트](../modules/reservation/src/test/kotlin/com/media/bus/reservation/contract/controller/InternalContractControllerTest.kt), [IAM HTTP 계약 테스트](../modules/reservation/src/test/kotlin/com/media/bus/reservation/contract/client/IamApiContractTest.kt) |

특히 47행의 “모두 사실이다”와 1차 리뷰 반영 표는 현재 코드 기준의 정정 사항을 함께 표시하는 편이 좋다. 이전 리뷰의 문장을 재인용하는 것과 현재 소스로 재검증한 사실은 구분되어야 한다.

## 5. 미결정 사항과 모순의 구분

다음 항목은 이미 계획이 조건을 명시했으므로 새로운 결함으로 판단하지 않는다.

- **2단계 S2S 기능 미동작:** 75·270·461행이 루프백을 생략하고 3단계를 바로 진행한다고 명시한다. 2단계의 완료는 기동·인증·설정 검증이며 업무 API 전체의 전환 완료가 아니라는 해석이면 일관된다.
- **트랜잭션 합류:** 기존 호출 Facade에는 `@Transactional`이 없고, 352행도 합류 조건을 구분했다. 직접 호출만으로 모든 작업이 하나의 트랜잭션이 된다고 다시 지적할 근거는 없다.
- **Liquibase 이력 위치:** 운영 조회 전에는 여러 이력 테이블이 존재한다고 단정할 수 없다. 0단계에서 다중 이력이 확인되면 그때 단일 master·이력 통합 또는 이력별 실행 구성을 확정해야 한다. `logicalFilePath` 보존만으로 이력 테이블 자체가 합쳐지지는 않는다.
- **외부 internal 호출자:** 저장소 안의 호출자 부재와 외부 호출자 부재는 다르다. 기존 계획의 확인 조건을 그대로 유지한다.
- **롤백:** 20행은 요약이 단정적이지만 404–407행에서 같은 이미지·볼륨·환경이라는 전제를 명시했다. 요약에 해당 절 링크를 붙이면 충분하며, 롤백 성공이 입증된 상태로 읽어서는 안 된다.

## 6. 단계별 반영과 검증

| 단계 | 이번 리뷰에서 추가할 내용 | 확인할 결과 |
|---|---|---|
| 1 | 포트 대상·검증 소유 위치의 잘못된 설명 정정 | 이미 Service에 있는 검증과 새로 옮길 검증이 구분됨 |
| 2 | `app`의 명시적 실행 JAR·의존 설정, 문서 생성 전용 접근 정책 | 실제 실행 JAR 생성·기동, OpenAPI 생성 작업 성공, 운영 문서 차단 유지 |
| 3 | Bulkhead 변환에 429 지정, 기본값 있는 DTO 필드 검증, 기존 테스트의 역할 이관 | 관리자 HTTP 429·예약 항목 코드 유지, 정류소 단가 보존, 의미 있는 검증 유지 |
| 4 | 프록시별 신뢰 범위·app 포트 공개 정책, 세션·감사 로그 IP 추출 통일 | 위조 전달 헤더 거절, 정상 IP 보존, 프록시 우회 접근 차단 |
| 5 | 기존 전환·롤백 리허설에 위 검증 결과 포함 | 동일 데이터·세션·외부 응답 계약을 사용하는 신·구 버전 전환 확인 |

## 7. 이번 작업의 검증 결과와 한계

- 계획서 513행과 이전 리뷰 2개를 읽고, 현재 소스·예외 처리·필터·서비스 호출·Gradle·리소스·Docker 구성을 대조했다.
- HTTP 클라이언트 메서드 `3 + 1 + 12 + 10 = 26`개와 `X-User-Id` 직접 주입 19곳을 정적 집계로 재확인했다.
- OpenAPI 플러그인은 현재 저장소가 사용하는 **1.9.0 문서**를 확인했다. 프록시·패키징 동작도 공식 문서로 확인했다.
- 이번 변경은 이 리뷰 파일 하나다. 계획 원문·이전 리뷰·애플리케이션 코드는 변경하지 않았다.
- Gradle 빌드, 앱·컨테이너 실행, 운영 DB·Valkey 조회, 성능 측정, 전환·롤백 리허설은 수행하지 않았다. 본문의 완료 조건은 향후 구현 시 검증할 항목이다.
