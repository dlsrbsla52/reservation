# CLAUDE.md — gateway

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
| `/api/v1/auth/**`, `/api/v1/admin/**` | `iam-service:8181` |
| `/api/v1/stop/**` | `stop-service:8182` |
| `/api/v1/reservation/**` | `reservation-service:8183` |

## 주요 클래스

- **`JwtAuthenticationFilter`** — `GlobalFilter` 구현. Authorization 헤더에서 JWT를 `tryParseClaims()` 단일 호출로 검증. 유효하면 X-User-* 헤더 + X-Service-Token(S2S) 주입 후 라우팅
- **`SecurityConfig`** — WebFlux Security 설정 (공개 경로 허용, 나머지 인증 필요)
- **`HealthCheck`** — `/api/v1/gateway/health-check` 엔드포인트

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
