# 인증/회원 (IAM) 기능 목록

현재 `iam` 모듈에서 추가가 필요한 기능 목록.

---

## 현재 구현 상태

- [x] 회원가입 (이메일 인증 토큰 생성, Redis 저장)
- [x] 로그인 (JWT 발급, Refresh Token Rotation)
- [x] 이메일 인증 (Redis 토큰 검증)
- [x] Access Token 재발급
- [x] 로그아웃 (Redis Refresh Token 삭제)
- [x] 회원 조회 (JWT/memberId/loginId/email)
- [x] 역할-권한 체계 (MEMBER / BUSINESS / ADMIN_*)
- [ ] 이메일 실제 발송 (`AuthService.kt:91` — AWS SES 미연결)
- [ ] 이메일 인증 조건 (`AuthorizeHandlerInterceptor.kt:99` — 서버 구축 후 활성화 예정)

---

## 추가 기능 목록

### 1. 이메일 발송 연동 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| AWS SES 인증 메일 발송 | 회원가입 시 이메일 인증 링크 발송. `AuthService`의 TODO 구현 |
| 이메일 재발송 | `POST /api/v1/auth/resend-verification` — 인증 메일 재발송 (Rate Limit 적용) |
| 이메일 인증 조건 활성화 | `AuthorizeHandlerInterceptor`에서 `email_verified=false` 회원 API 접근 차단 |
| 이메일 템플릿 관리 | HTML 이메일 템플릿 (인증, 계약 알림, 갱신 안내 등) |

### 2. 회원 프로필 관리 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 비밀번호 변경 | `PUT /api/v1/member/password` — 현재 비밀번호 검증 후 변경 |
| 비밀번호 초기화 | `POST /api/v1/auth/reset-password` — 이메일로 초기화 링크 발송 |
| 회원 정보 수정 | `PUT /api/v1/member/me` — 전화번호, 사업자번호 등 수정 |
| 회원 탈퇴 | `DELETE /api/v1/member/me` — `WITHDRAWN` 상태 전환, 개인정보 처리 방침에 따른 데이터 보존/삭제 |
| 회원 상태 변경 | 관리자: `ACTIVE` ↔ `SUSPENDED` 전환 (`PUT /api/v1/admin/member/{id}/status`) |

### 3. 역할/권한 관리 API (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 회원 역할 부여/박탈 | `POST /api/v1/admin/member/{id}/role` — 관리자가 역할 부여 |
| 역할 목록 조회 | `GET /api/v1/admin/role` — 전체 역할 및 권한 목록 |
| 역할 생성/수정 | 동적 역할 관리 API (현재 Liquibase 데이터로만 관리) |

### 4. 보안 강화 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 로그인 실패 횟수 제한 | Redis로 실패 횟수 추적, N회 초과 시 계정 일시 잠금 |
| 세션 목록 조회 | `GET /api/v1/member/sessions` — 현재 발급된 Refresh Token 목록 |
| 특정 세션 강제 만료 | `DELETE /api/v1/member/sessions/{sessionId}` — 특정 기기 로그아웃 |
| 전체 세션 만료 | `DELETE /api/v1/member/sessions` — 모든 기기 로그아웃 |

### 5. 소셜 로그인 (우선순위: 낮음)

| 기능 | 설명 |
|------|------|
| OAuth2 연동 | 카카오/네이버 소셜 로그인 (`spring-security-oauth2-client`) |
| 소셜 계정 연결/해제 | 기존 계정에 소셜 계정 연결 |
