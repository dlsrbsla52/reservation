# 모듈러 모놀리스 회귀 계획 리뷰

- 검토일: 2026-11-17
- 대상: [모듈러 모놀리스 회귀 계획](modular-monolith-migration-plan.md)
- 검토 기준: `bigbang/modular-monolith` 작업트리의 코드·설정과 Spring Security, Spring Framework, JDK, Docker 공식 자료
- 판정: **전환 방향은 적절하다. 구현 계획은 아래 누락과 모순을 보완한 뒤 확정한다.**
- 검증 범위: 정적 코드·설정 대조. 통합 앱 실행, 운영 DB 조회, 성능 측정, 롤백 리허설은 수행하지 않았다.

## 1. 종합 판단

현재 구조에서 실행 단위를 하나로 줄이면서 Gradle 모듈 경계를 유지하는 접근은 타당하다. 소비 모듈이 포트를 정의하고 `app`이 제공 모듈의 서비스에 연결하면, 직접적인 모듈 간 컴파일 의존을 만들지 않고 S2S HTTP를 제거할 수 있다.

기존 계획에서 유지할 핵심은 다음과 같다.

- `iam`·`stop`·`reservation` 사이의 직접적인 Gradle 의존 금지
- 모듈별 스키마 유지 및 제공 모듈의 Repository 직접 접근 금지
- JWT 직접 검증과 `@CurrentMember` 전환을 통한 헤더 신뢰 제거
- internal HTTP 매핑 0개를 운영 배포 조건으로 설정
- Liquibase 이력의 파일 식별자를 보존하고 운영 DB 사본으로 검증
- 전환 기간 신규 changeset을 제한하여 이전 버전과의 DB 호환성 유지

다만 **보안 체인 통합 시점**과 **HTTP 제거 시 입력 검증 보존**은 구현 전에 반드시 해결해야 한다. 메모리 수치는 현재 측정값이 아닌 목표치이며, 계획에 적힌 설정만으로 보장되지 않는다.

## 2. 수정 우선순위

| ID | 우선순위 | 발견 사항 | 반영 시점 |
|---|---|---|---|
| R1 | 높음 | 전체 요청용 보안 체인 3개가 함께 등록되며, 설정 삭제 시 `PasswordEncoder`도 사라짐 | `app` 생성과 동시에 |
| R2 | 높음 | internal 컨트롤러 삭제 시 Bean Validation·입력 변환·검색 조건 검증이 유실될 수 있음 | 포트 추출 및 직접 호출 전환 전 |
| R3 | 중간 | 라이브러리에 남는 `application*.yml` 처리 계획이 없음 | 라이브러리 전환 시 |
| R4 | 중간 | 로그의 회원 ID가 여전히 외부 `X-User-Id` 헤더를 신뢰함 | JWT 필터 이관 시 |
| R5 | 중간 | 컨테이너 메모리 제한 없이 `MaxRAMPercentage=60`만 제시함 | 배포 구성 전환 시 |
| R6 | 중간 | healthcheck 실패를 `restart: unless-stopped`가 복구한다고 잘못 설명함 | 운영 복구 정책 확정 시 |

## 3. 주요 발견 사항

### R1. 보안 체인 통합을 앱 생성 단계로 당겨야 한다

**대상:** 원안 1단계 빈 이름 정리, 2단계 `app` 생성, 3단계 보안 설정 통합

세 모듈의 `SecurityFilterChain`에는 `securityMatcher`가 없고, 모두 전체 요청에 적용된다. 클래스와 빈 이름을 바꿔 이름 충돌을 제거하더라도 이 체인들을 한 앱에 함께 등록할 수는 없다. Spring Security는 전체 요청을 처리하는 체인 뒤에 도달할 수 없는 체인이 있으면 `UnreachableFilterChainException`을 발생시킨다.

- 코드: [IAM SecurityConfig](../modules/iam/src/main/kotlin/com/media/bus/iam/configuration/SecurityConfig.kt), [Stop SecurityConfig](../modules/stop/src/main/kotlin/com/media/bus/stop/configuration/SecurityConfig.kt), [Reservation SecurityConfig](../modules/reservation/src/main/kotlin/com/media/bus/reservation/config/SecurityConfig.kt)
- 공식 근거: [Spring Security 7.1.2 체인 검증 구현](https://raw.githubusercontent.com/spring-projects/spring-security/7.1.2/config/src/main/java/org/springframework/security/config/annotation/web/builders/WebSecurityFilterChainValidator.java)

또한 IAM의 `SecurityConfig`에는 `PasswordEncoder` 빈이 있다. 설정 클래스를 삭제하면서 이를 함께 없애면 인증 서비스의 의존성 주입이 실패한다.

**수정안:** `app` 생성과 단일 보안 체인 구성을 같은 단계로 묶는다. 기존 세 보안 설정의 동시 등록을 제거하고, `PasswordEncoder`는 별도 설정으로 보존한다. gateway 없이 공개 API를 검증하려면 JWT 인증 이관까지 해당 단계에 포함해야 한다.

**완료 조건:** 실제 앱 컨텍스트 기동 성공, 기존 모듈의 전체 요청용 체인 중복 없음, 로그인·토큰 갱신·관리자 인증 회귀 통과.

### R2. 컨트롤러가 수행하던 검증을 제공 모듈의 진입점으로 옮겨야 한다

**대상:** 원안 4단계 서비스 직접 호출 및 `Internal*Controller` 삭제

HTTP 제거는 직렬화만 없애는 작업이 아니다. MVC가 수행하던 `@Valid`, 문자열의 enum·날짜 변환, 컨트롤러 내부의 입력 보정과 검증도 사라진다.

구체적으로 예약 완료·계약 생성 요청은 다음과 같다.

| 위치 | 현재 역할 |
|---|---|
| IAM의 `CompleteToContractRequest` | 필드만 정의되어 있고 Bean Validation 제약은 없음 |
| reservation의 `CompleteToContractRequest` | `@NotBlank`, `@Positive`, `@Size` 등 정의 |
| `InternalReservationController.completeReservationToContract` | `@RequestBody @Valid`로 제공자 DTO 검증 실행 |

어댑터가 reservation DTO를 생성해서 서비스에 직접 넘기는 것만으로는 이 검증이 실행되지 않는다. 예를 들어 빈 계약명이나 음수 금액이 기존과 달리 서비스까지 전달될 수 있다. 또한 enum 변환을 단순 `valueOf`로 대체하면 잘못된 입력이 기존 4xx 대신 500으로 처리될 수 있다.

- 코드: [IAM 요청 DTO](../modules/iam/src/main/kotlin/com/media/bus/iam/client/reservation/dto/CompleteToContractRequest.kt), [reservation 요청 DTO](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/dto/request/CompleteToContractRequest.kt), [InternalReservationController](../modules/reservation/src/main/kotlin/com/media/bus/reservation/reservation/controller/InternalReservationController.kt)
- 추가 이관 대상: [InternalContractController](../modules/reservation/src/main/kotlin/com/media/bus/reservation/contract/controller/InternalContractController.kt)의 검색 조건 검증과 페이지 제한, [InternalStopController](../modules/stop/src/main/kotlin/com/media/bus/stop/controller/InternalStopController.kt)의 요청 검증과 검색 조건 생성
- 공식 근거: [Spring Framework Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)

**수정안:** 삭제할 컨트롤러별로 검증·변환·보정 목록을 만든다. 도메인 입력 검증은 제공 모듈의 진입 서비스나 Validator로 옮긴다. 문자열 변환 실패도 기존 공개 API의 오류 계약을 유지하도록 처리한다. 기존 공개 컨트롤러에서도 같은 검증을 수행하는지 확인하여 중복과 누락을 구분한다.

**완료 조건:** 빈 계약명, 0·음수 금액, 길이 초과, 잘못된 enum·날짜, 페이지 범위 초과에 대해 기존 응답 상태·오류 코드·보정 규칙이 유지된다.

### R3. 실행 설정을 `app`에 단일화해야 한다

**대상:** 원안 2단계 라이브러리 전환 및 통합 `application.yml` 생성

현재 `iam`·`stop`·`reservation`에는 `application.yml`과 `application-local.yml`이 각각 있다. local 설정은 기존 서비스 포트, Liquibase 경로, `allow-bean-definition-overriding: true` 등을 포함한다.

라이브러리 JAR에 이 파일들을 남기면 클래스패스에서 동명 설정이 경쟁하고, local 프로필 설정이 통합 앱의 기본 설정을 덮어쓸 수 있다. `app/application.yml` 하나를 추가하는 것만으로 설정 정리가 끝나지 않는다.

- 코드: [IAM local 설정](../modules/iam/src/main/resources/application-local.yml), [Stop local 설정](../modules/stop/src/main/resources/application-local.yml), [Reservation local 설정](../modules/reservation/src/main/resources/application-local.yml)
- 공식 근거: [Spring Boot 외부 설정 및 프로필 우선순위](https://docs.spring.io/spring-boot/reference/features/external-config.html)

**수정안:** 실행 프로필 설정을 모두 `app`으로 옮기고 라이브러리 산출물에서 기존 `application*.yml`을 제거한다. 일반 JAR 활성화와 `bootJar` 비활성화를 함께 확인한다. Liquibase 경로 이동도 각 프로필에 반영한다.

**완료 조건:** 기본·local 프로필 모두 올바른 포트, DB, Liquibase 경로, 빈 덮어쓰기 금지 설정으로 기동한다. 라이브러리 JAR에 기존 실행 설정이 남지 않는다.

### R4. 회원 식별 로그도 JWT 검증 결과를 사용해야 한다

**대상:** 원안 3단계 `X-User-*` 헤더 신뢰 제거

`MdcLoggingFilter`는 `X-User-Id`를 읽어 MDC의 `memberId`에 저장한다. 등록 순서는 `Ordered.HIGHEST_PRECEDENCE + 200`으로 Spring Security 필터보다 앞이다.

따라서 Security 내부의 JWT 필터가 헤더를 제거하더라도 로깅 필터는 이미 외부에서 보낸 회원 ID를 읽은 상태다. 컨트롤러 인증 우회를 막아도 로그의 회원 식별 정보는 위조될 수 있다.

- 코드: [MdcLoggingFilter](../modules/common/src/main/kotlin/com/media/bus/common/logging/MdcLoggingFilter.kt), [CommonLoggingAutoConfiguration](../modules/common/src/main/kotlin/com/media/bus/common/autoconfigure/CommonLoggingAutoConfiguration.kt)

**수정안:** 요청 ID 생성과 인증된 회원 ID 주입의 시점을 구분한다. 회원 ID는 JWT 인증 후 검증된 `MemberPrincipal`에서 설정하고 요청 종료 시 정리한다. 액세스 로그와 비동기 MDC 전파에도 올바른 값이 전달되는지 확인한다.

**완료 조건:** 본인 토큰에 다른 회원의 `X-User-Id`를 붙여도 로그에는 토큰의 회원 ID만 남는다. 인증 실패 요청에는 위조 회원 ID가 남지 않는다.

### R5. 메모리 목표와 실제 제한 설정을 맞춰야 한다

**대상:** 원안 목표 스펙 및 5단계 JVM 옵션

원안은 힙 512~768MB를 목표로 제시하면서 `-XX:MaxRAMPercentage=60`을 사용한다. 그러나 현재 compose에는 컨테이너 메모리 제한이 없다. 다른 제한이 없다면 4GB 호스트에서 힙 상한이 대략 2.4GB로 계산될 수 있으므로 목표와 맞지 않는다.

`MaxRAMPercentage`는 JVM이 인식하는 사용 가능 메모리 기준의 힙 비율이다. 힙 외에도 메타스페이스, 코드 캐시, 네이티브 버퍼 등 프로세스 메모리가 필요하다.

- 코드: [docker-compose.yml](../docker-compose.yml)
- 공식 근거: [JDK 25 `MaxRAM`·`MaxRAMPercentage`](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html), [Docker 자원 제한](https://docs.docker.com/engine/containers/resource_constraints/)

**수정안:** 컨테이너 메모리 제한과 힙 상한을 함께 정한다. `-Xmx`를 명시하거나 제한된 컨테이너 메모리를 기준으로 비율을 계산한다. Serial GC 적용 여부도 측정으로 결정한다.

**완료 조건:** 실제 JVM 힙 상한과 컨테이너 제한 확인, 유휴·일반 요청·정류소 일괄 등록 중 RSS와 지연 측정, 같은 호스트의 DB·Valkey 운영 여유 확보.

### R6. healthcheck와 자동 복구를 구분해야 한다

**대상:** 원안 위험과 대응의 “actuator health로 컨테이너 재시작(`restart: unless-stopped`)”

Docker의 재시작 정책은 컨테이너 프로세스 종료에 반응한다. healthcheck 결과가 `unhealthy`가 되더라도 프로세스가 살아 있으면 `unless-stopped`만으로 재시작되지 않는다. 따라서 앱이 멈췄지만 종료되지 않는 장애에 대한 복구 수단이 현재 설명에는 없다.

- 공식 근거: [Docker 컨테이너 재시작 정책](https://docs.docker.com/reference/cli/docker/container/run/)

**수정안:** 프로세스 종료 후 재시작과 healthcheck 기반 감시를 별도로 정의한다. 후자까지 자동 복구하려면 외부 감시·재시작 수단을 정하고, 수동 대응이라면 알림과 복구 절차를 명시한다.

**완료 조건:** 프로세스 종료와 살아 있는 상태의 응답 불능을 각각 시험하여 알림·복구 동작을 확인한다.

## 4. 문서 내부 모순 및 설명 정정

행 번호는 검토 시점의 원안 338행 기준이다.

| 항목 | 원안 | 정정 방향 |
|---|---|---|
| 공개 경로 판정 | 183행은 `startsWith` 유지, 262행은 정확한 일치 요구 | HTTP 메서드와 경로의 정확한 일치로 통일 |
| 임시 S2S 주소 | 172행은 `localhost:8080`, 297행은 `localhost:8090` | 전용 커넥터 방식을 유지한다면 8090으로 통일하고 공개 경로 허용 목록·JWT 필터와의 처리 순서를 명시 |
| 트랜잭션 합류 | 216행은 같은 트랜잭션 매니저 사용 시 호출자 트랜잭션에 합류한다고 설명 | 호출자 트랜잭션이 존재하고 전파 설정이 합류를 허용할 때로 조건 명시 |

현재 [AdminReservationFacade](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/facade/AdminReservationFacade.kt)와 [AdminStopPriceFacade](../modules/iam/src/main/kotlin/com/media/bus/iam/admin/facade/AdminStopPriceFacade.kt)에는 `@Transactional`이 없다. 따라서 예시로 든 쓰기 위임이 직접 호출로 바뀐다는 이유만으로 하나의 큰 트랜잭션이 되는 것은 아니다.

각 호출 경로의 실제 트랜잭션 경계를 확인한 뒤 필요한 경우에만 `REQUIRES_NEW`를 선택한다. 어댑터에 일괄적으로 트랜잭션을 추가하지 않는다.

## 5. 권장 실행 순서

| 순서 | 작업 | 완료 조건 |
|---|---|---|
| 0 | 운영 이력·백업·현재 API 동작·메모리 기준선 확보 | Liquibase 이력 위치와 파일 식별자 기록, DB 사본과 회귀 시나리오 준비 |
| 1 | 소비자 포트 추출 및 제공자 입력 검증 이관 | 기존 HTTP 구현으로도 동작 유지, 삭제할 컨트롤러의 검증 목록 확보 |
| 2 | `app` 생성과 보안·프로필 설정·로깅 통합 | R1·R3·R4 해결, 기본·local 기동 및 인증 회귀 통과 |
| 3 | 어댑터 연결, S2S HTTP 및 internal 매핑 제거 | R2 검증 보존, internal 매핑 0개, 모듈 경계 유지 |
| 4 | 배포 구성·자원 제한·복구 정책 확정 | R5·R6 해결, 실제 산출물로 컨테이너 검증 |
| 5 | 운영 DB 사본에서 전환·롤백 리허설 후 운영 전환 | 기존 changeset 재실행 없음, 이전 버전 복귀 성공, 회귀 및 자원 측정 통과 |

중간 단계에서 S2S 루프백을 유지한다면 임시 주소·보안 규칙을 일관되게 설정하고 운영에는 배포하지 않는다. 단계 2와 3을 하나의 전환 단위로 묶으면 임시 커넥터 구성을 생략하는 방안도 가능하다.

## 6. 전환 완료 체크리스트

아래 항목은 아직 수행되지 않은 전환 검증이다.

- [ ] 모듈별 테스트와 전체 빌드가 통과하고 `app` 실행 JAR이 생성된다.
- [ ] 기본·local 프로필에서 실제 앱이 기동하며 기존 실행 설정이 라이브러리에 남지 않는다.
- [ ] 기존 보안 체인 중복이 없고 `PasswordEncoder`가 보존된다.
- [ ] 로그인·토큰 갱신·관리자 인증·CORS·공개 경로 정책이 회귀 검증을 통과한다.
- [ ] 위조 `X-User-*`가 업무 처리와 로그의 회원 식별에 영향을 주지 않는다.
- [ ] HTTP 제거 후에도 입력 검증·변환 실패·페이지 제한·오류 응답 계약이 유지된다.
- [ ] internal HTTP 매핑이 0개이며 actuator·Swagger의 공개 포트 노출이 차단된다.
- [ ] 호출별 트랜잭션 경계를 확인하고 필요한 쓰기 경로의 롤백 동작을 검증한다.
- [ ] 운영 DB 사본에서 Liquibase 이력·체크섬을 확인하고 기존 changeset이 재실행되지 않는다.
- [ ] 이전 이미지와 compose로 되돌렸을 때 DB·Valkey 데이터와 로그인 세션을 정상 사용한다.
- [ ] 컨테이너 메모리 제한·힙 상한·실제 RSS를 확인하고 주요 작업 중 지연을 측정한다.
- [ ] 프로세스 종료와 응답 불능 각각의 감시·복구 절차를 검증한다.

이 리뷰는 계획 보완을 위한 근거이며 운영 전환 승인이나 실행 검증 결과를 대신하지 않는다.
