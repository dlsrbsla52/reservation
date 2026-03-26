# CLAUDE.md — auth

`modules/auth`는 JWT 발급·갱신·회원 관리를 담당하는 Spring Boot 서비스 (port **8181**).
Base package: `com.media.bus.auth`

## Build

```bash
./gradlew :modules:auth:bootJar
./gradlew :modules:auth:bootRun
./gradlew :modules:auth:test
```

## Dependencies

- `modules/common` — 공통 인프라
- `modules/auth-contract` — `JwtProvider`, `MemberPrincipal`, `MemberType`

## 패키지 구조

```
modules/auth/
  modules/auth/          # 인증 (로그인, 가입, 토큰)
    controller/AuthController
    service/AuthService
    entity/              # Role, Permission, MemberRole, RolePermission
    repository/          # RoleRepository, PermissionRepository, MemberRoleRepository
    guard/               # RegisterRequestValidator (interface)
      impl/              # RegisterRequestValidatorImpl
    dto/                 # LoginRequest, RegisterRequest, TokenResponse
  modules/member/        # 회원 관리
    controller/MemberController   # S2S 전용 (내부 서비스 호출)
    service/MemberService
    entity/Member                 # DateBaseEntity 상속
    entity/enumerated/MemberStatus
    repository/MemberRepository
    dto/                 # MemberResponse, FindMemberByJwtRequest
```

## 주요 API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/login` | 로그인 → Access + Refresh 토큰 반환 |
| POST | `/api/v1/auth/register` | 회원 가입 |
| POST | `/api/v1/auth/refresh` | Refresh 토큰으로 Access 토큰 재발급 |
| GET  | `/api/v1/member/{id}` | 회원 조회 (S2S 전용) |

## Database

- Schema: `auth` (Member, Role, Permission, MemberRole, RolePermission)
- Schema: `member` (Member 엔티티 실제 저장 위치)
- Migration: Liquibase (`src/main/resources/db/changelog/`)

## 토큰 정책

토큰 상세(TTL, 검증 방식, 알려진 제약) → `modules/auth-contract/CLAUDE.md` 참조.