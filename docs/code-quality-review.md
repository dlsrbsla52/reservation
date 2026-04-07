# 코드 품질 검토 보고서

**프로젝트:** reservation (bus MSA blueprint)  
**검토 일자:** 2026-04-07  
**검토 범위:** 6개 모듈 전체 (common, auth-contract, gateway, iam, stop, reservation)  
**검토 파일 수:** 42개 소스 파일

---

## 심각도 요약

| 심각도 | 건수 |
|--------|------|
| CRITICAL | 2 |
| HIGH | 5 |
| MEDIUM | 8 |
| LOW | 5 |
| **합계** | **20** |

---

## 영역별 등급

| 영역           | 등급     | 핵심 사항                                                                                |
| ------------ | ------ | ------------------------------------------------------------------------------------ |
| 아키텍처 & 모듈 경계 | **A-** | Facade 패턴, S2S 분리, 모듈별 스키마 격리 우수. 모듈 간 Repository 직접 참조 없음                           |
| 코드 컨벤션 준수    | **B**  | BaseEnum, DateBaseEntity 패턴 대체로 준수. reservation 모듈 엔티티에서 @Setter/String 타입 사용이 규약 위반 |
| 보안           | **B+** | JWT/Cookie/S2S 다계층 보안 우수. RequestContextHolder ThreadLocal 사용이 VT 환경에서 위험            |
| 성능           | **B**  | Bulkhead 적극 활용. 이중 JWT 파싱, 매 요청 S2S 토큰 생성, 대량 단일 트랜잭션 개선 필요                          |
| 예외 처리 구조     | **A**  | BusinessException/ServiceException 분리, Result 기반 HTTP 상태 자동 결정 우수                    |
| 테스트 커버리지     | **C**  | common/auth-contract/stop은 양호. iam/reservation은 심각하게 부족                              |
| 잠재적 버그       | **B-** | StopCommandGuard 로직 반전 확인 필요. TOCTOU 이중 파싱, BaseEnum NPE                             |

---

## CRITICAL — 반드시 수정 필요

### [C-01] Gateway JWT 이중 파싱 — TOCTOU 경합 조건

**파일:** `modules/gateway/.../filter/JwtAuthenticationFilter.java:74-81`

`isInvalidToken(token)` 호출 후 `parseClaimsFromToken(token)`을 다시 호출하는 구조. 두 파싱 사이에 토큰이 만료되면 첫 번째 검증은 통과하지만 두 번째 파싱이 실패하여 사용자에게 잘못된 401 응답이 발생한다.

**수정 방향:**
```java
// JwtProvider에 추가
public Optional<Claims> tryParseClaims(String token) {
    try {
        return Optional.of(parseClaimsFromToken(token));
    } catch (JwtException | IllegalArgumentException e) {
        return Optional.empty();
    }
}
```
`isInvalidToken` + `parseClaimsFromToken` 두 단계를 `tryParseClaims()` 단일 호출로 일원화.

---

### [C-02] `RestClientConfig.extractBearerToken()`에서 `RequestContextHolder` (ThreadLocal) 사용

**파일:** `modules/common/.../configuration/RestClientConfig.java:87`

`RequestContextHolder`는 내부적으로 `ThreadLocal`을 사용한다. Virtual Thread 환경에서 carrier thread 전환 시 컨텍스트가 소실될 수 있으며, 토큰 소실 시 S2S 토큰으로 폴백되어 **권한 상승**이 발생할 수 있다. CLAUDE.md 핵심 규약인 "ThreadLocal 금지" 위반.

**수정 방향:**
- Gateway가 이미 주입하는 `X-User-*` 헤더를 `HttpServletRequest`에서 직접 읽거나,
- `ScopedValue` 기반 토큰 전파 메커니즘으로 교체.

---

## HIGH — 수정 권장

### [H-01] `StopCommandGuard.isStopRegistered()` 비즈니스 로직 반전

**파일:** `modules/stop/.../guard/StopCommandGuard.java:21-24`

메서드 이름은 "등록 여부 확인"이지만, `stopRepository.findByStopId(stopId).orElseThrow()`로 "존재하지 않으면 예외"를 던진다. 신규 등록 시 Guard를 호출하는데, 항상 존재하지 않는 상태이므로 **신규 정류소 등록이 항상 실패**할 수 있다.

**수정 방향:**
```java
// 중복 방지 의도라면
public void validateNotDuplicate(String stopId) {
    stopRepository.findByStopId(stopId).ifPresent(s -> {
        throw new BusinessException(HttpStatus.CONFLICT, "이미 등록된 정류장입니다.");
    });
}
```

---

### [H-02] `Contract`, `ContractDetail`, `Reservation` 엔티티에 `@Setter` 전역 적용

**파일:**
- `modules/reservation/.../contract/entity/Contract.java:23`
- `modules/reservation/.../contract/entity/ContractDetail.java:23`
- `modules/reservation/.../reservation/entity/Reservation.java:19`

CLAUDE.md "정적 팩토리 메서드로 생성 강제" 규약 위반. `@Setter` 전역 적용으로 도메인 무결성 파괴.

**수정 방향:**
```java
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends DateBaseEntity {
    // @Setter 제거
    // 상태 변경은 도메인 행위 메서드로만 노출
    public void cancel(LocalDateTime cancelledAt) {
        this.status = ContractStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }
}
```

---

### [H-03] `Contract.status`, `ContractDetail.paymentStatus` 등 Enum 대신 `String` 타입 사용

**파일:**
- `modules/reservation/.../contract/entity/Contract.java:59`
- `modules/reservation/.../contract/entity/ContractDetail.java:49-60`

`ContractStatus`, `PaymentStatus`, `PaymentCycle`, `PaymentMethod` Enum이 별도 파일로 존재하지만 엔티티 필드에서는 `String`으로 선언. 잘못된 문자열이 DB에 저장될 수 있고 BaseEnum 패턴이 사실상 무효화된다.

**수정 방향:**
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private ContractStatus status;
```

---

### [H-04] `Stop` 엔티티에 `@Setter` 전역 적용

**파일:** `modules/stop/.../entity/Stop.java:31`

`applyUpdate()` 도메인 메서드가 이미 존재하면서도 `@Setter`가 전역으로 열려 있어 외부 임의 변경 가능. H-02와 동일한 문제.

**수정 방향:** `@Setter` 제거 후 `applyUpdate()` 도메인 행위 메서드로만 상태 변경 허용.

---

### [H-05] `MdcLoggingFilter`에서 `SecurityContextHolder` (ThreadLocal) 사용

**파일:** `modules/common/.../logging/MdcLoggingFilter.java:61`

C-02와 동일한 ThreadLocal 규약 위반. `SecurityContextHolder.getContext().getAuthentication()`은 기본적으로 ThreadLocal 기반.

**수정 방향:** `MemberPrincipalExtractFilter`가 주입한 request attribute에서 `MemberPrincipal.id()`를 읽도록 변경.

---

## MEDIUM — 개선 권장

### [M-01] `JwtProvider`에서 이중 파싱 패턴 반복

**파일:** `modules/auth-contract/.../security/JwtProvider.java:117-123`

`isInvalidToken()`은 내부에서 `parseClaimsFromToken()`을 호출하고 Claims를 버린다. 호출부에서 항상 두 메서드를 연속 호출하여 이중 파싱 발생.

**수정 방향:** `Optional<Claims> tryParseClaims(String token)` 메서드를 추가하고 기존 호출부 일원화.

---

### [M-02] `RegisterRequestValidator`에서 비즈니스 검증 실패에 `NoAuthenticationException` 오용

**파일:** `modules/iam/.../guard/RegisterRequestValidator.java:33-51`

아이디 중복, 이메일 중복, 사업자번호 누락은 **비즈니스 검증 실패**(400/409)이지 **인증 실패**(401)가 아니다.

**수정 방향:**
```java
// 변경 전
throw new NoAuthenticationException(CommonResult.DUPLICATE_USERNAME_FAIL);

// 변경 후
throw new BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL);
```

---

### [M-03] `AuthService.login()`에서 역할 조회 로직 중복

**파일:**
- `modules/iam/.../auth/service/AuthService.java:120-129`
- `modules/iam/.../auth/service/AuthService.java:189-197`
- `modules/iam/.../member/service/MemberService.java:93-98`

"memberRoles 조회 → isEmpty 체크 → size > 1 경고 → MemberType.fromName" 패턴이 3곳에서 거의 동일하게 반복. DRY 원칙 위반.

**수정 방향:** `private MemberType resolveMemberType(UUID memberId)` 메서드로 추출.

---

### [M-04] `StopRegisterService.registerAllFromPublicApi()`에서 전체 데이터를 단일 트랜잭션 처리

**파일:** `modules/stop/.../service/StopRegisterService.java:36`

수천~수만 건 데이터가 하나의 트랜잭션으로 처리. 중간 실패 시 전체 롤백, 장시간 커넥션 점유.

**수정 방향:** 페이지 단위로 `@Transactional(propagation = REQUIRES_NEW)` 메서드를 분리하거나 `TransactionTemplate` 사용.

---

### [M-05] `JwtAuthenticationFilter.isPublicPath()`에서 경로 매칭 잠재적 오버매칭

**파일:** `modules/gateway/.../filter/JwtAuthenticationFilter.java:118-121`

`key::startsWith` 패턴은 `"POST:/api/v1/auth/register-admin"` 같은 경로를 화이트리스트에 의도치 않게 포함시킬 수 있다.

**수정 방향:** path와 query string을 분리한 후 정확한 경로 비교.

---

### [M-06] `Reservation` 엔티티에 `@SuperBuilder`와 정적 팩토리 메서드 없음

**파일:** `modules/reservation/.../reservation/entity/Reservation.java`

`DateBaseEntity`는 `@SuperBuilder`를 사용하지만 `Reservation`은 `@Setter` + `@Getter`만 사용. CLAUDE.md "정적 팩토리 메서드로 생성 강제" 미적용.

---

### [M-07] `S2STokenFilter`에서 이중 파싱 패턴

**파일:** `modules/auth-contract/.../filter/S2STokenFilter.java:59-63`

M-01과 동일한 이중 파싱 패턴. try-catch 안이므로 `parseClaimsFromToken`만 호출하면 충분.

---

### [M-08] 매 요청마다 S2S 토큰 새로 생성

**파일:** `modules/gateway/.../filter/JwtAuthenticationFilter.java:100`

```java
.header("X-Service-Token", jwtProvider.generateS2SToken())  // 매 요청마다 JWT 서명
```

JWT 서명(HMAC 연산)이 매 요청마다 발생하여 고트래픽 환경에서 불필요한 CPU 부하.

**수정 방향:** S2S 토큰을 캐싱하여 만료 5분 전까지 재사용하는 메커니즘 도입.

---

## LOW — 선택적 개선

### [L-01] `BaseEnum.fromName()`에서 null 입력 시 NPE

**파일:** `modules/common/.../entity/common/BaseEnum.java:27`

`name.isBlank()` 호출 전 null 체크 없음.

**수정:** `if (name == null || name.isBlank())`로 변경.

---

### [L-02] `CommonResult.BAD_CREDENTIAL_FAIL`의 messageId에 오타

**파일:** `modules/common/.../result/type/CommonResult.java:46`

`"authentication,bad-credential.fail.msg"` → 쉼표(`,`)가 아닌 점(`.`)이어야 함. 메시지 번들 조회 실패로 폴백 메시지만 사용됨.

**수정:** `"authentication.bad-credential.fail.msg"`로 변경.

---

### [L-03] `reservation`, `iam` 모듈 테스트 부족

`reservation` 모듈 테스트 0개. `iam` 모듈 2개(guard validator + repository)만 존재. `AuthService.login()`, `ContractFacade.createContract()` 등 핵심 비즈니스 로직 단위 테스트 없음.

**최소 추가 대상:**
- `AuthService.login()`
- `AuthService.register()`
- `ContractFacade.createContract()`

---

### [L-04] 테스트 코드에서 `System.out.println` 사용

**파일:** `modules/stop/.../client/SeoulBusApiClientTest.java:29,38,39`

`assertThat()` assertion으로 교체하거나 `@Disabled` + `log.debug()` 처리.

---

### [L-05] `CreateAdminMemberRequest` DTO에서 `@Builder` 단독 사용

**파일:** `modules/iam/.../admin/dto/CreateAdminMemberRequest.java:16`

record DTO에서의 Lombok `@Builder`는 지원이 불안정할 수 있음. Compact Constructor 또는 정적 팩토리 메서드가 더 자연스러움.

---

## 긍정적 관찰 사항

1. **예외 처리 계층이 잘 설계됨** — `BusinessException`/`ServiceException` 분리, `Result` 인터페이스 기반 HTTP 상태 자동 결정, `log.warn`(비즈니스) vs `log.error`(기술적) 구분
2. **`MemberPrincipal` record를 통한 Gateway-서비스 간 헤더 계약 중앙화** — 헤더 이름 상수, 파싱/복원 로직이 하나의 record에 집중
3. **Facade 패턴으로 트랜잭션 내 외부 I/O 방지** — `ContractFacade`, `ReservationFacade`가 외부 서비스 호출을 트랜잭션 바깥에서 처리
4. **`TransactionalBulkheadAspect` + `ScopedValue` 조합의 정교한 구현** — 재진입 방지, `BulkheadCallException` 마커 예외를 통한 Throwable 언래핑
5. **다계층 보안 설계** — Refresh Token HttpOnly Cookie, S2STokenFilter 내부 API 보호, `isTrustedInternalService()` 토큰 유출 방지
6. **`@HttpExchange` 프록시를 활용한 선언적 S2S 클라이언트 구조** — `StopApi` 인터페이스와 `StopServiceClient` 위임 구조로 HTTP 호출과 비즈니스 로직 명확히 분리
7. **CLAUDE.md 계층 문서화 우수** — 루트 및 각 모듈별 CLAUDE.md에 빌드 명령, 아키텍처 결정, 알려진 제약사항 기술

---

## 최우선 수정 권고

1. **[C-02]** `RestClientConfig`의 `RequestContextHolder` → `ScopedValue` 또는 request attribute 기반으로 교체
2. **[H-01]** `StopCommandGuard.isStopRegistered()` 로직 반전 여부 확인 및 수정
3. **[C-01]** `JwtProvider.tryParseClaims()` 도입으로 이중 파싱 일원화
