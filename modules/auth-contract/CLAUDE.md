# CLAUDE.md — auth-contract

`modules/auth-contract`는 JWT/인증 계약을 정의하는 공유 라이브러리 모듈이다.
다른 모듈(gateway, auth, stop, reservation)이 이 모듈에 의존하여 토큰 검증과 인증 컨텍스트를 공유한다.

## 주요 클래스

- **`JwtProvider`** — JWT 발급, 파싱, 검증 구현체 (`common`의 `TokenProvider` 구현)
- **`MemberPrincipal`** — 인증된 사용자 정보를 담는 record (`id`, `loginId`, `email`, `memberType`, `emailVerified`)
- **`MemberType`** — 회원 유형 enum (`MEMBER`, `BUSINESS`, `ADMIN_USER`, `ADMIN_MASTER`, `ADMIN_DEVELOPER`)
- **`S2STokenFilter`** — 서비스 간 통신(S2S) 토큰 검증 필터

## 토큰 설계

| 토큰 종류 | TTL | 저장소 | 비고 |
|-----------|-----|--------|------|
| Access | 60분 | 없음(Stateless) | 서명 검증만 수행 |
| Refresh | 7일 | Redis | 취소 가능 |
| S2S | 1시간 | 없음(Stateless) | `type: "s2s"` 클레임으로 구분 |

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
