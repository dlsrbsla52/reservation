# CLAUDE.md — gateway

`modules/gateway`는 Spring Cloud Gateway (WebFlux/Netty) 기반 API 게이트웨이 (port **8080**).
JWT 검증 후 헤더 주입, 하위 서비스로 라우팅. DB 없음.
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
| `/api/v1/iam/**`, `/api/v1/member/**` | `iam-service:8181` |
| `/api/v1/stop/**` | `stop-service:8182` |
| `/api/v1/reservation/**` | `reservation-service:8183` |

## 주요 클래스

- `JwtAuthenticationFilter` — Authorization 헤더에서 JWT 검증. 유효하면 `X-Member-*` 헤더 주입 후 라우팅
- `SecurityConfig` — WebFlux Security 설정 (공개 경로 허용, 나머지 인증 필요)
- `HealthCheck` — `/actuator/health` 엔드포인트

## 주의사항

- WebFlux 기반이므로 `common`의 Servlet 전용 자동 구성(`CommonWebMvcAutoConfiguration` 등)은 적용되지 않음
- 필터는 반드시 `WebFilter` / `GatewayFilter` 구현 (Spring MVC `OncePerRequestFilter` 사용 금지)
- Virtual Thread 대신 Reactor 논블로킹 모델 — `block()` 호출 금지