# CLAUDE.md — iam

`modules/iam`는 JWT 발급·갱신·회원 관리를 담당하는 Spring Boot 서비스 (port **8181**).
Base package: `com.media.bus.iam`

## Build

```bash
./gradlew :modules:iam:bootJar
./gradlew :modules:iam:bootRun
./gradlew :modules:iam:test
```

## Dependencies

- `modules/common` — 공통 인프라
- `modules/auth-contract` — `JwtProvider`, `MemberPrincipal`, `MemberType`

## 패키지 구조

```
modules/iam/src/main/kotlin/com/media/bus/iam/
  admin/                 # 어드민 회원 관리
    controller/AdminController
    service/AdminMemberService
    guard/AdminRegisterRequestValidator
    dto/CreateAdminMemberRequest
  auth/                  # 인증 (로그인, 가입, 토큰)
    controller/AuthController
    service/AuthService
    service/RoleResolutionService   # 회원 역할 조회 공통 서비스
    entity/              # RoleEntity, PermissionEntity, MemberRoleEntity, RolePermissionEntity (Exposed DAO)
    repository/          # RoleRepository, MemberRoleRepository, RolePermissionRepository
    guard/               # RegisterRequestValidator
    dto/                 # LoginRequest, RegisterRequest, TokenResponse
    result/AuthResult    # IAM 전용 Result 코드 (A 접두사)
  member/                # 회원 관리
    controller/MemberController   # S2S 전용 (내부 서비스 호출)
    service/MemberService
    entity/MemberEntity           # Exposed DAO, DateBaseTable 상속
    entity/enumerated/MemberStatus
    repository/MemberRepository
    dto/                 # MemberResponse, FindMemberByJwtRequest
```

## 주요 API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/register` | 회원 가입 (이메일 인증 토큰 발급) |
| POST | `/api/v1/auth/login` | 로그인 → Access Token(body) + Refresh Token(HttpOnly Cookie) |
| GET  | `/api/v1/auth/verify-email` | 이메일 인증 (Redis 1회성 토큰) |
| POST | `/api/v1/auth/token/refresh` | Refresh Token으로 Access Token 재발급 (Token Rotation) |
| POST | `/api/v1/auth/logout` | 로그아웃 (Redis Refresh Token 삭제) |
| POST | `/api/v1/member/jwt` | JWT 기반 회원 조회 (S2S 전용) |
| POST | `/api/v1/member/id` | memberId 기반 회원 조회 (S2S 전용) |

## Database

- Schema: `auth` (Role, Permission, MemberRole, RolePermission)
- Schema: `member` (Member 엔티티)
- ORM: Exposed DAO 패턴
- Migration: Liquibase (`src/main/resources/db/changelog/`)

## 토큰 정책

토큰 상세(TTL, 검증 방식, 알려진 제약) → `modules/auth-contract/CLAUDE.md` 참조.

## 주요 서비스

- **`RoleResolutionService`** — 회원 역할 조회 공통 서비스. AuthService, MemberService에서 공유
- **`AuthService`** — 가입, 로그인, 이메일 인증, 토큰 갱신, 로그아웃
- **`MemberService`** — JWT/ID/loginId/email 기반 회원 조회 (S2S 전용)
