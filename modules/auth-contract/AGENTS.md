# AGENTS.md — auth-contract

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

`modules/auth-contract`는 JWT/인증 계약을 정의하는 공유 라이브러리 모듈이다.
다른 모듈(app, iam, stop, reservation)이 이 모듈에 의존하여 토큰 검증과 인증 컨텍스트를 공유한다.

## 주요 클래스

- **`JwtProvider`** — JWT 발급, 파싱, 검증 구현체
- **`MemberPrincipal`** — 인증된 사용자 정보를 담는 data class (`id`, `loginId`, `email`, `memberType`, `emailVerified`, `permissions`)
- **`MemberType`** — 회원 유형 enum (`MEMBER`, `BUSINESS`, `ADMIN_USER`, `ADMIN_MASTER`, `ADMIN_DEVELOPER`)
- **`MemberPrincipal.TRUSTED_HEADERS`** — 예전 gateway 신뢰 헤더 6개. 지금은 `app` 인증 필터가 모든 요청에서 **제거하는 목록**으로만 쓴다. `MemberPrincipal`은 토큰 클레임(`fromClaims`)으로만 만든다
- **`AuthorizeHandlerInterceptor`** — `@Authorize` 어노테이션 기반 카테고리/권한 검증
- **`CurrentMemberArgumentResolver`** — `@CurrentMember` 어노테이션으로 컨트롤러 파라미터에 `MemberPrincipal` 주입

## 토큰 설계

| 토큰 종류 | TTL | 저장소 | 비고 |
|-----------|-----|--------|------|
| Access (일반 사이트, `aud=USER`) | 60분 | 없음(Stateless) | 서명 검증만 수행 |
| Access (관리자 사이트, `aud=ADMIN`) | 15분 | 없음(Stateless) | `app` 인증 필터가 `/api/v1/admin/**`에 이 토큰만 통과시킨다 |
| Refresh | 7일 | Redis | 취소 가능, Token Rotation 지원. `aud`를 기록하고 갱신 시 이어받는다 |

## 클레임 암호화(`ClaimCipher`)

- JWS 서명은 그대로 두고 **클레임 값만** AES-256-GCM으로 암호화한다. 키는 `jwt.claim-encryption-key`(`JWT_CLAIM_ENCRYPTION_KEY`, Base64 32바이트)이고 서명 키 `JWT_SECRET`과 따로 둔다.
- 암호화 대상: `sub`, `loginId`, `email`, `memberType`, `emailVerified`, `permissions`. 평문 유지: `aud`·`iat`·`exp`(파서·필터가 사용), refresh의 `sid`·`type`.
- 복호화는 `JwtProvider.parseClaimsFromToken()` 안에서 끝낸다. 호출부는 평문 `Claims`만 받으므로 **토큰을 직접 `Jwts.parser()`로 파싱하지 않는다**(값이 암호문으로 보인다).
- 복호화 실패(키 불일치·변조·암호화 전 토큰)는 `MalformedJwtException` → `tryParseClaims()`가 `null` → 401.
- 키를 바꾸면 발급된 모든 토큰이 무효가 된다(전원 재로그인).

## 발급처(`TokenAudience`)

- 액세스·리프레시 토큰의 JWT 표준 `aud` 클레임에 `USER` 또는 `ADMIN`을 기록한다. TTL도 발급처별로 `TokenAudience.accessTokenTtl`이 정한다.
- `TokenAudience.fromClaims()`는 `aud`가 없거나 모르는 값이면 **`USER`로 해석**한다. 넓은 쪽(`ADMIN`)으로 해석하면 구버전 토큰이 관리자 경계를 우회한다.
- 발급처 규칙(관리자 계정 ⇔ 관리자 사이트)은 iam `AuthService`가, 경로 진입 규칙은 `app`의 `JwtAuthenticationFilter`가 강제한다. 액세스 토큰에는 `type` 클레임이 없고, `type`이 있는 토큰(refresh)은 필터가 401로 거절한다.

## 핵심 API

- `tryParseClaims(token): Claims?` — JWT 파싱 단일 진입점. 실패 시 null 반환 (예외 미전파)
- `isInvalidToken(token): Boolean` — `tryParseClaims()` 재사용

## ⚠️ 알려진 제약사항 — 액세스 토큰 권한 갱신 지연

**현상**: 액세스 토큰의 권한 클레임은 **발급 시점 기준으로 최대 60분간 캐시된다.**
권한이 변경되어도 기존 토큰이 만료될 때까지는 변경이 반영되지 않는다.

**원인**: 액세스 토큰은 무상태(Stateless) 설계로, Redis 블랙리스트가 구현되어 있지 않다.

**현재 의사결정**: 의도적으로 무상태 설계를 유지한다.
- 장점: Redis 의존성 없음, 낮은 레이턴시, 단순한 검증 로직
- 단점: 권한 변경의 즉시 반영 불가

**즉시 반영이 필요한 경우**: Redis 블랙리스트 도입 필요.
구현 시 `JwtProvider`의 토큰 검증 로직에 블랙리스트 조회를 추가하고,
권한 변경 시 해당 회원의 모든 액세스 토큰을 블랙리스트에 등록한다.

## 검증

```bash
./gradlew :modules:auth-contract:test
./gradlew :modules:auth-contract:build
```
