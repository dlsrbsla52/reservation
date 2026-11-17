# AGENTS.md — iam

<!-- 이 모듈 지침의 원본. CLAUDE.md는 @AGENTS.md로 가져오고 GEMINI.md는 심볼릭 링크다. -->

`modules/iam`는 JWT 발급·갱신·회원 관리, 관리자 API를 담당하는 도메인 라이브러리 모듈이다. 실행은 `modules/app`이 한다.
Base package: `com.media.bus.iam`

## Build

```bash
./gradlew :modules:iam:test
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'   # 실행은 app 모듈
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
    controller/MemberController   # 회원 본인 조회·수정·탈퇴
    service/MemberService
    entity/MemberEntity           # Exposed DAO, DateBaseTable 상속
    entity/enumerated/MemberStatus
    repository/MemberRepository
    dto/                 # MemberResponse 등
  port/                  # StopAdminPort, ReservationAdminPort (구현은 app 어댑터)
    dto/{stop,reservation}/   # 포트 입출력 DTO
```

## 주요 API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/register` | 회원 가입 (이메일 인증 토큰 발급) |
| POST | `/api/v1/auth/login` | 로그인 → Access Token(body) + Refresh Token(HttpOnly Cookie) |
| GET  | `/api/v1/auth/verify-email` | 이메일 인증 (Redis 1회성 토큰) |
| POST | `/api/v1/auth/token/refresh` | Refresh Token으로 Access Token 재발급 (Token Rotation) |
| POST | `/api/v1/auth/logout` | 로그아웃 (Redis Refresh Token 삭제) |
| POST | `/api/v1/auth/admin/login` | 관리자 사이트 로그인. 관리자 계정만 허용, `aud=ADMIN` 토큰 + `admin_refresh_token` 쿠키 |
| POST | `/api/v1/auth/admin/token/refresh` | 관리자 토큰 재발급 (`admin_refresh_token` 쿠키, Path `/api/v1/auth/admin`) |
| POST | `/api/v1/auth/admin/logout` | 관리자 세션 로그아웃 |
| POST | `/api/v1/auth/admin/reactivate` | INACTIVE 관리자 계정 재활성화 (일반 재활성화는 관리자 계정을 받지 않음) |

**모듈 간 호출**
- 다른 모듈에 제공: reservation의 `MemberLookupPort`를 `app`의 `ReservationMemberLookupAdapter`가 `MemberService.findByMemberId`로 구현한다. HTTP internal API는 두지 않는다.
- 다른 모듈을 사용: 관리자 기능은 `iam.port.StopAdminPort`·`ReservationAdminPort`를 쓴다. 구현은 `app`의 어댑터다. 포트 DTO는 `iam.port.dto.{stop,reservation}`에 두고, stop·reservation 클래스를 참조하지 않는다(enum은 이름 문자열). 기본값을 두지 않는다 — 어댑터가 필드를 빠뜨리면 컴파일이 실패해야 한다.
- 컨트롤러는 회원 정보를 `@CurrentMember MemberPrincipal`로만 받는다. `X-User-*` 헤더를 읽지 않는다.

## Database

- Schema: `auth` (Role, Permission, MemberRole, RolePermission)
- Schema: `member` (Member 엔티티)
- ORM: Exposed DAO 패턴
- Migration: Liquibase (`src/main/resources/db/changelog/`)

## 토큰 정책

- 관리자 계정은 일반 로그인(`/api/v1/auth/login`)과 재활성화를 쓸 수 없다(403 `A0019`). 관리자 로그인은 관리자 계정만 받는다(403 `A0020`).
- 갱신 API는 refresh 토큰의 `aud`가 자기 사이트와 다르거나, 세션 중 역할이 바뀌어 사이트 규칙에 어긋나면 401 `A0021`로 재로그인시킨다.
- 로그아웃은 호출한 사이트의 refresh 쿠키로 세션 id를 찾아 그 세션만 지운다. 쿠키가 없으면 **세션을 지우지 않는다**(전체 삭제는 `revokeOtherSessions`나 관리자 강제 로그아웃 경로).
- `/api/v1/admin` 하위 컨트롤러는 `@Authorize`가 필수다. `AdminControllerAuthorizeCoverageTest`가 누락을 막는다.

토큰 상세(TTL, 검증 방식, 알려진 제약) → `modules/auth-contract/AGENTS.md` 참조.

## 주요 서비스

- **`RoleResolutionService`** — 회원 역할 조회 공통 서비스. AuthService, MemberService에서 공유
- **`AuthService`** — 가입, 로그인, 이메일 인증, 토큰 갱신, 로그아웃
- **`MemberService`** — 회원 조회·수정. `findByMemberId`는 reservation 회원 재검증 포트 어댑터도 쓴다
