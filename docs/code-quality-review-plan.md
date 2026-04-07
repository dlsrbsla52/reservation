# 코드 품질 개선 구현 플랜

**기반 문서:** `docs/code-quality-review.md`  
**작성 일자:** 2026-04-07  
**수정 대상:** 총 20건 (CRITICAL 2, HIGH 5, MEDIUM 8, LOW 5)

---

## 수정 순서 개요

모듈 의존성 방향(`common` → `auth-contract` → `gateway`/`iam`/`stop`/`reservation`)에 따라 공유 라이브러리부터 수정한다.

| Step | 모듈 | 이슈 | 수정 파일 수 |
|------|------|------|-------------|
| 1 | `common` | L-01, L-02, C-02, H-05 | 4 |
| 2 | `auth-contract` | C-01, M-08 | 1 |
| 3 | `gateway` | C-01 | 1 |
| 4 | `iam` | M-02, M-03 | 3 |
| 5 | `stop` | H-01, H-04, M-04 | 4+1(신규) |
| 6 | `reservation` | H-02, H-03, M-06 | 3 |

---

## Step 1 — `common` 모듈

### [L-01] `BaseEnum.java:27` — null 입력 시 NPE

**파일:** `modules/common/src/main/java/com/media/bus/common/entity/common/BaseEnum.java`

```java
// 변경 전
if (name.isBlank()) {

// 변경 후
if (name == null || name.isBlank()) {
```

**이유:** `name`이 null이면 `isBlank()` 호출 시 NPE 발생.

---

### [L-02] `CommonResult.java:46` — messageId 오타

**파일:** `modules/common/src/main/java/com/media/bus/common/result/type/CommonResult.java`

```java
// 변경 전
BAD_CREDENTIAL_FAIL("00221", "authentication,bad-credential.fail.msg", ...)

// 변경 후
BAD_CREDENTIAL_FAIL("00221", "authentication.bad-credential.fail.msg", ...)
```

**이유:** 쉼표(`,`)가 점(`.`)이어야 메시지 번들 키로 올바르게 조회된다.

---

### [H-05] `MdcLoggingFilter.java:61` — SecurityContextHolder (ThreadLocal) 사용

**파일:** `modules/common/src/main/java/com/media/bus/common/logging/MdcLoggingFilter.java`

```java
// 변경 전
private Optional<String> resolvememberId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
        return Optional.ofNullable(auth.getName());
    }
    return Optional.empty();
}

// 변경 후 (camelCase 오타도 함께 수정)
private Optional<String> resolveMemberId(HttpServletRequest request) {
    MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.ATTRIBUTE_KEY);
    if (principal != null) {
        return Optional.of(principal.id().toString());
    }
    return Optional.empty();
}
```

**이유:** `SecurityContextHolder`는 ThreadLocal 기반. Virtual Thread 환경에서 carrier thread 전환 시 컨텍스트 소실 가능. `MemberPrincipalExtractFilter`가 이미 request attribute에 `MemberPrincipal`을 주입하므로 그것을 읽는 방식으로 교체.

---

### [C-02] `RestClientConfig.java:87` — RequestContextHolder (ThreadLocal) 사용

**파일:** `modules/common/src/main/java/com/media/bus/common/configuration/RestClientConfig.java`

```java
// 변경 전
private String extractBearerToken() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
        return tokenProvider.generateS2SToken(); // S2S 폴백
    }
    ...
}

// 변경 후
// RequestScope 빈 도입: TokenHolder
@Bean
@RequestScope
public RequestTokenHolder requestTokenHolder(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    return new RequestTokenHolder(header);
}

// extractBearerToken() 에서 RequestTokenHolder 주입받아 사용
private String extractBearerToken(RequestTokenHolder tokenHolder) {
    String header = tokenHolder.getAuthorizationHeader();
    if (header == null || !header.startsWith("Bearer ")) {
        return tokenProvider.generateS2SToken(); // 비동기/스케줄러 컨텍스트 폴백
    }
    return header.substring(7);
}
```

**이유:** `RequestContextHolder`는 ThreadLocal 기반. `@RequestScope` 빈은 Spring이 관리하는 프록시로 Virtual Thread 환경에서도 안전하며, 비동기/스케줄러 컨텍스트(RequestScope 없음) → S2S 토큰 폴백 유지.

---

## Step 2 — `auth-contract` 모듈

### [C-01] `JwtProvider.java` — `tryParseClaims()` 신규 추가

**파일:** `modules/auth-contract/src/main/java/com/media/bus/contract/security/JwtProvider.java`

```java
// 신규 메서드 추가
/**
 * JWT 토큰을 파싱하여 Claims를 반환한다.
 * 서명 오류 또는 만료 시 빈 Optional 반환 (예외 미전파).
 *
 * @param token JWT 토큰 문자열
 * @return 파싱된 Claims, 실패 시 Optional.empty()
 */
public Optional<Claims> tryParseClaims(String token) {
    try {
        return Optional.of(parseClaimsFromToken(token));
    } catch (JwtException | IllegalArgumentException e) {
        return Optional.empty();
    }
}

// 기존 isInvalidToken() — tryParseClaims() 재사용으로 이중 구현 제거
public boolean isInvalidToken(String token) {
    return tryParseClaims(token).isEmpty();
}
```

**이유:** 기존 `isInvalidToken()`은 파싱 후 결과를 버린다. 호출부에서 항상 다시 파싱하여 이중 HMAC 연산이 발생. `tryParseClaims()`로 일원화.

---

### [M-08] `JwtProvider.java` — S2S 토큰 캐싱

**파일:** `modules/auth-contract/src/main/java/com/media/bus/contract/security/JwtProvider.java`

```java
// 캐시 필드 추가
private volatile String cachedS2SToken = null;
private volatile long s2sTokenExpireAt = 0L;
private static final long S2S_CACHE_BUFFER_MS = 5 * 60 * 1000L; // 만료 5분 전 갱신

// generateS2SToken() 수정
@Override
public String generateS2SToken() {
    long now = System.currentTimeMillis();
    if (cachedS2SToken == null || now >= s2sTokenExpireAt - S2S_CACHE_BUFFER_MS) {
        synchronized (this) {
            if (cachedS2SToken == null || now >= s2sTokenExpireAt - S2S_CACHE_BUFFER_MS) {
                cachedS2SToken = buildNewS2SToken(now);
                s2sTokenExpireAt = now + S2S_TOKEN_EXPIRE_MS;
            }
        }
    }
    return cachedS2SToken;
}

private String buildNewS2SToken(long now) {
    return Jwts.builder()
        .subject("SYSTEM")
        .claim("type", "s2s")
        .issuedAt(new Date(now))
        .expiration(new Date(now + S2S_TOKEN_EXPIRE_MS))
        .signWith(secretKey)
        .compact();
}
```

**이유:** S2S 토큰 TTL이 1시간이므로 매 요청마다 재생성할 필요가 없다. double-checked locking으로 Virtual Thread 환경에서도 안전한 캐싱.

---

## Step 3 — `gateway` 모듈

### [C-01] `JwtAuthenticationFilter.java:74-81` — 이중 파싱 제거

**파일:** `modules/gateway/src/main/java/com/media/bus/gateway/filter/JwtAuthenticationFilter.java`

```java
// 변경 전
if (jwtProvider.isInvalidToken(token)) {
    log.warn("[JwtAuthenticationFilter] 유효하지 않은 토큰. path={}", path);
    return unauthorized(exchange);
}
try {
    Claims claims = jwtProvider.parseClaimsFromToken(token);
    ...
}

// 변경 후
Claims claims = jwtProvider.tryParseClaims(token).orElse(null);
if (claims == null) {
    log.warn("[JwtAuthenticationFilter] 유효하지 않은 토큰. path={}", path);
    return unauthorized(exchange);
}
// try-catch 불필요 — tryParseClaims가 예외를 이미 흡수
MemberPrincipal principal = jwtProvider.getPrincipalFromClaims(claims);
```

**이유:** `isInvalidToken()` + `parseClaimsFromToken()` 이중 파싱을 `tryParseClaims()` 단일 호출로 통합. HMAC 검증 연산이 요청당 1회로 감소.

---

## Step 4 — `iam` 모듈

### [M-02] `RegisterRequestValidator.java:34,40,45,50` — NoAuthenticationException 오용

**파일:** `modules/iam/src/main/java/com/media/bus/iam/auth/guard/RegisterRequestValidator.java`

```java
// 변경 전 (4곳 동일 패턴)
throw new NoAuthenticationException(CommonResult.DUPLICATE_USERNAME_FAIL);

// 변경 후
throw new BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL);
```

수정 대상 라인: 34, 40, 45, 50 (4곳 모두)

**이유:** 아이디 중복, 이메일 중복, 사업자번호 누락은 비즈니스 검증 실패(400/409). `NoAuthenticationException`은 401을 반환하므로 의미론적으로 잘못됨.

---

### [M-03] `AuthService.java` — 역할 조회 로직 중복 제거

**파일:** `modules/iam/src/main/java/com/media/bus/iam/auth/service/AuthService.java`

```java
// private 메서드 추출 (AuthService 내부)
private MemberType resolveMemberType(UUID memberId) {
    List<MemberRole> memberRoles = memberRoleRepository.findWithRoleByMemberId(memberId);
    if (memberRoles.isEmpty()) {
        throw new BaseException(AuthResult.ROLE_NOT_FOUND);
    }
    if (memberRoles.size() > 1) {
        log.warn("[AuthService] 회원 [{}]에게 복수 역할이 존재합니다. 첫 번째 역할을 사용합니다.", memberId);
    }
    return MemberType.fromName(memberRoles.get(0).getRole().getName())
            .orElseThrow(() -> new BaseException(AuthResult.ROLE_NOT_FOUND));
}
```

- `login()` (L119-129) → `resolveMemberType(member.getId())` 호출로 교체
- `refreshAccessToken()` (L188-197) → 동일하게 교체

**파일:** `modules/iam/src/main/java/com/media/bus/iam/member/service/MemberService.java`

```java
// MemberService 내에도 동일한 패턴 추출
private MemberType resolveMemberType(UUID memberId) {
    List<MemberRole> memberRoles = memberRoleRepository.findWithRoleByMemberId(memberId);
    if (memberRoles.isEmpty()) {
        throw new BaseException(CommonResult.USERNAME_NOT_FOUND_FAIL);
    }
    return MemberType.fromName(memberRoles.getFirst().getRole().getName())
            .orElseThrow(() -> new BaseException(CommonResult.INTERNAL_ERROR));
}
```

**이유:** 동일한 7줄 패턴이 3곳에서 반복. 각 서비스 클래스 내 private 메서드로 추출.

---

## Step 5 — `stop` 모듈

### [H-01] `StopCommandGuard.java:21-24` — 로직 반전

**파일:** `modules/stop/src/main/java/com/media/bus/stop/guard/StopCommandGuard.java`

```java
// 변경 전
public void isStopRegistered(String stopId) {
    stopRepository.findByStopId(stopId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "등록된 정류장을 찾을 수 없습니다."));
}

// 변경 후
public void validateNotDuplicate(String stopId) {
    stopRepository.findByStopId(stopId).ifPresent(s -> {
        throw new BusinessException(HttpStatus.CONFLICT, "이미 등록된 정류장입니다.");
    });
}
```

**파일:** `modules/stop/src/main/java/com/media/bus/stop/service/StopService.java`

```java
// 변경 전
stopCommandGuard.isStopRegistered(request.stopId());

// 변경 후
stopCommandGuard.validateNotDuplicate(request.stopId());
```

**이유:** `createOneStop()`에서 Guard를 호출하는 의도는 중복 등록 방지. 현재 "없으면 예외" 로직은 신규 등록을 항상 실패시킴.

---

### [H-04] `Stop.java:31` — @Setter 제거

**파일:** `modules/stop/src/main/java/com/media/bus/stop/entity/Stop.java`

```java
// 변경 전
@Getter
@Setter
public class Stop extends DateBaseEntity {

// 변경 후
@Getter
public class Stop extends DateBaseEntity {
```

**이유:** `applyUpdate()` 도메인 메서드가 이미 필드 변경을 캡슐화하고 있음. `@Setter` 전역 적용은 외부에서 임의 변경을 허용.

---

### [M-04] `StopRegisterService.java` — 트랜잭션 분리

**신규 파일:** `modules/stop/src/main/java/com/media/bus/stop/service/StopBulkPersistService.java`

```java
@Service
@RequiredArgsConstructor
public class StopBulkPersistService {

    private final StopRepository stopRepository;
    private final StopUpdateHistoryRepository stopUpdateHistoryRepository;

    /// 페이지 단위로 정류소 저장 및 이력 기록.
    /// REQUIRES_NEW로 페이지마다 독립 트랜잭션 커밋 — 중간 실패 시 해당 페이지만 롤백.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistPage(List<Stop> toSave, List<StopUpdateHistory> histories) {
        stopRepository.saveAll(toSave);
        stopUpdateHistoryRepository.saveAll(histories);
    }
}
```

**파일:** `modules/stop/src/main/java/com/media/bus/stop/service/StopRegisterService.java`

```java
// 변경 전
@Transactional
public StopBulkRegisterResult registerAllFromPublicApi() { ... }

// 변경 후 (@Transactional 제거, StopBulkPersistService 주입)
public StopBulkRegisterResult registerAllFromPublicApi() {
    ...
    for (int start = 1; start <= totalCount; start += pageSize) {
        ...
        stopBulkPersistService.persistPage(toSave, histories); // 페이지 단위 트랜잭션
        ...
    }
}
```

**이유:** 전체 루프가 단일 트랜잭션이면 장시간 DB 커넥션 점유 + 중간 실패 시 전체 롤백. `StopBulkPersistService` 별도 빈으로 분리하면 self-invocation AOP 우회 문제를 원천 차단하면서 페이지별 독립 트랜잭션 보장.

---

## Step 6 — `reservation` 모듈

### [H-02] `Contract.java`, `ContractDetail.java`, `Reservation.java` — @Setter 제거

**공통 패턴:**

```java
// 변경 전
@Getter
@Setter
public class Contract extends DateBaseEntity {

// 변경 후
@Getter
public class Contract extends DateBaseEntity {
```

- 상태 변경이 필요한 필드는 도메인 행위 메서드로 노출:
  ```java
  // Contract: 계약 취소
  public void cancel(LocalDateTime cancelledAt) {
      this.status = ContractStatus.CANCELLED;
      this.cancelledAt = cancelledAt;
  }
  ```

---

### [H-03] `Contract.java:59`, `ContractDetail.java:49,53,60` — String → Enum

**파일:** `modules/reservation/src/main/java/com/media/bus/reservation/contract/entity/Contract.java`

```java
// 변경 전
@Size(max = 20)
@NotNull
@Column(name = "status", nullable = false, length = 20)
private String status;

// 변경 후
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 20)
private ContractStatus status;
```

**파일:** `modules/reservation/src/main/java/com/media/bus/reservation/contract/entity/ContractDetail.java`

```java
// 변경 전 / 변경 후
private String paymentCycle;      →  @Enumerated(STRING) private PaymentCycle paymentCycle;
private String paymentMethod;     →  @Enumerated(STRING) private PaymentMethod paymentMethod;
private String paymentStatus;     →  @Enumerated(STRING) private PaymentStatus paymentStatus;
```

팩토리 메서드 내 `.getName()` 호출도 제거:
```java
// 변경 전
.status(ContractStatus.PENDING.getName())

// 변경 후
.status(ContractStatus.PENDING)
```

**이유:** Enum 클래스가 이미 정의되어 있음에도 String으로 저장하면 잘못된 값이 DB에 저장될 수 있고 컴파일 타임 타입 안전성을 포기.

---

### [M-06] `Reservation.java` — @SuperBuilder + 정적 팩토리

**파일:** `modules/reservation/src/main/java/com/media/bus/reservation/reservation/entity/Reservation.java`

```java
// 변경 전
@Getter
@Setter
public class Reservation extends DateBaseEntity { ... }

// 변경 후
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends DateBaseEntity {

    ...

    /// 예약 생성 팩토리 메서드.
    public static Reservation create(UUID memberId, String stopId, ...) {
        return Reservation.builder()
                .memberId(memberId)
                .stopId(stopId)
                ...
                .build();
    }
}
```

**이유:** CLAUDE.md "정적 팩토리 메서드로 생성 강제" 규약 미적용 상태.

---

## 검증 방법

```bash
# 1. 전체 빌드 확인
./gradlew clean build

# 2. 모듈별 테스트
./gradlew :modules:common:test
./gradlew :modules:auth-contract:test
./gradlew :modules:stop:test
./gradlew :modules:iam:test

# 3. Docker 로컬 환경 기동
docker-compose -f docker-compose-local.yml up --build -d

# 4. 인증 흐름 수동 검증
# POST /api/v1/auth/login → Access Token 발급 확인
# GET  /api/v1/stop (토큰 포함) → 200 정상 응답
# GET  /api/v1/stop (만료 토큰) → 401 응답
# GET  /api/v1/stop/health-check (인증 없이) → 200

# 5. H-01 정류소 중복 등록 검증
# POST /api/v1/stop (신규 stopId) → 201 Created
# POST /api/v1/stop (동일 stopId) → 409 Conflict

# 6. M-04 배치 등록 검증
# POST /api/v1/stop/bulk-register → 오류 없이 완료, 페이지별 로그 출력 확인
```

---

## 수정 파일 목록

| Step | 모듈 | 파일 | 이슈 |
|------|------|------|------|
| 1 | common | `BaseEnum.java` | L-01 |
| 1 | common | `CommonResult.java` | L-02 |
| 1 | common | `MdcLoggingFilter.java` | H-05 |
| 1 | common | `RestClientConfig.java` | C-02 |
| 2 | auth-contract | `JwtProvider.java` | C-01, M-08 |
| 3 | gateway | `JwtAuthenticationFilter.java` | C-01 |
| 4 | iam | `RegisterRequestValidator.java` | M-02 |
| 4 | iam | `AuthService.java` | M-03 |
| 4 | iam | `MemberService.java` | M-03 |
| 5 | stop | `StopCommandGuard.java` | H-01 |
| 5 | stop | `StopService.java` | H-01 |
| 5 | stop | `Stop.java` | H-04 |
| 5 | stop | `StopRegisterService.java` | M-04 |
| 5 | stop | `StopBulkPersistService.java` *(신규)* | M-04 |
| 6 | reservation | `Contract.java` | H-02, H-03 |
| 6 | reservation | `ContractDetail.java` | H-02, H-03 |
| 6 | reservation | `Reservation.java` | H-02, M-06 |
