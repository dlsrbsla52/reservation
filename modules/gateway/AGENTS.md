# AGENTS.md — gateway

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

`modules/gateway`는 Spring Cloud Gateway (WebFlux/Netty) 기반 API 게이트웨이 (port **8080**).
JWT 검증 후 X-User-* 헤더 주입, 하위 서비스로 라우팅. DB 없음.
Base package: `com.media.bus.gateway`

## Build

```bash
./gradlew :modules:gateway:bootJar
./gradlew :modules:gateway:bootRun
./gradlew :modules:gateway:test
```

## Dependencies

- `modules/common` — `TokenProvider` 인터페이스
- `modules/auth-contract` — `JwtProvider`, `MemberPrincipal`
- `spring-cloud-starter-gateway` (WebFlux 기반 — Tomcat 제거됨)

## 라우팅 규칙

| 경로 패턴 | 대상 서비스 |
|-----------|-------------|
| `/api/v1/admin/**` (라우트 `iam-admin`, metadata `required-audience: ADMIN`) | `iam-service:8181` |
| `/api/v1/auth/**`, `/api/v1/member/**` | `iam-service:8181` |
| `/api/v1/stop/**` | `stop-service:8182` |
| `/api/v1/reservation/**`, `/api/v1/contract/**` | `reservation-service:8183` |
| `/api/v1/internal/**` (라우트 `block-internal`) | 403 차단 — 서비스 간(S2S) 전용 경로 |

## 주요 클래스

- **`JwtAuthenticationFilter`** — `GlobalFilter` 구현. Authorization 헤더에서 JWT를 `tryParseClaims()` 단일 호출로 검증. 유효하면 X-User-* 헤더 + X-Service-Token(S2S) 주입 후 라우팅
- **`SecurityConfig`** — WebFlux Security 설정 (공개 경로 허용, 나머지 인증 필요)
- **`HealthCheck`** — `/api/v1/gateway/health-check` 엔드포인트

## 관리자 경계

- `JwtAuthenticationFilter`는 매칭된 라우트의 `required-audience` metadata를 보고, `ADMIN`이면 **관리자 카테고리 + `aud=ADMIN` 토큰**만 통과시킨다(아니면 403).
- metadata가 빠져도 경로가 `/api/v1/admin`(중복 슬래시 정규화 후)이면 같은 규칙을 적용한다. 관리자 API를 새 라우트로 옮길 때도 metadata를 함께 둔다.
- 세부 인가(`ADMIN_USER`/`ADMIN_MASTER` 구분)는 iam 컨트롤러의 `@Authorize`가 담당한다. gateway는 1차 차단이다.
- CORS는 `CorsConfig`가 경로별로 처리한다. `/api/v1/admin/**`, `/api/v1/auth/admin/**`는 `CORS_ADMIN_ALLOWED_ORIGIN`만, 그 외는 일반·관리자 origin 모두 허용한다. `CORS_ADMIN_ALLOWED_ORIGIN`이 비면 `CORS_ALLOWED_ORIGIN`을 쓴다.

## 공개 경로

- `JwtAuthenticationFilter.PUBLIC_PATHS`는 토큰 없이 통과한다(로그인·가입·토큰 갱신·이메일 인증·계정 재활성화·비밀번호 초기화·관리자 로그인/갱신/재활성화·health-check).
- 공개 경로에서는 클라이언트가 보낸 `X-User-*` 헤더를 **제거**하고 넘긴다. 하위 서비스는 이 헤더를 검증 없이 신뢰하므로, 공개 엔드포인트에서 사용자 정보가 필요하면 요청 body로 본인을 확인해야 한다.

## 주입되는 헤더

| 헤더 | 설명 |
|------|------|
| `X-User-Id` | 회원 UUID |
| `X-User-Login-Id` | 로그인 아이디 |
| `X-User-Email` | 이메일 |
| `X-User-Role` | `ROLE_` + memberType |
| `X-Email-Verified` | 이메일 인증 여부 |
| `X-User-Permissions` | 쉼표 구분 권한 (예: "READ,WRITE") |
| `X-Service-Token` | S2S 토큰 |

## 주의사항

- WebFlux 기반이므로 `common`의 Servlet 전용 자동 구성(`CommonWebMvcAutoConfiguration` 등)은 적용되지 않음
- 필터는 반드시 `WebFilter` / `GatewayFilter` / `GlobalFilter` 구현 (Spring MVC `OncePerRequestFilter` 사용 금지)
- Virtual Thread 대신 Reactor 논블로킹 모델 — `block()` 호출 금지
