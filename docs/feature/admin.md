# 관리자 (Admin) 기능 목록

현재 관리자 전용 UI/API가 부재하며, 권한 체계(`ADMIN_USER`, `ADMIN_MASTER`, `ADMIN_DEVELOPER`)는 구축되어 있으나 관리자 워크플로우 API가 미구현 상태입니다.

---

## 현재 구현 상태

- [x] 관리자 역할/권한 체계 (`ADMIN_USER`, `ADMIN_MASTER`, `ADMIN_DEVELOPER`)
- [x] `@Authorize` 어노테이션으로 ADMIN 접근 제어
- [x] 정류소 수기 등록 (ADMIN 전용)
- [x] 계약 생성 (ADMIN 전용)
- [ ] 관리자 전용 조회/통계 API 없음
- [ ] 대시보드 없음

---

## 추가 기능 목록

### 1. 관리자 회원 관리 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 회원 목록 조회 | `GET /api/v1/admin/member` — 상태·역할·가입일 필터 + 페이지네이션 |
| 회원 상세 조회 | `GET /api/v1/admin/member/{id}` — 계약·예약 이력 포함 |
| 회원 상태 변경 | `PUT /api/v1/admin/member/{id}/status` — `ACTIVE` ↔ `SUSPENDED` |
| 역할 부여/박탈 | `POST /api/v1/admin/member/{id}/role` |
| 회원 강제 탈퇴 | `DELETE /api/v1/admin/member/{id}` — `WITHDRAWN` 처리 |

### 2. 관리자 예약 관리 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 전체 예약 목록 | `GET /api/v1/admin/reservation` — 상태·정류소·기간 필터 |
| 예약 담당자 배정 | `PUT /api/v1/admin/reservation/{id}/assign` — 매니저 배정 |
| 상담 결과 등록 | `POST /api/v1/admin/reservation/{id}/consultation` — 상담 메모 작성 |
| 예약 강제 취소 | `DELETE /api/v1/admin/reservation/{id}` |

### 3. 관리자 계약 관리 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 전체 계약 목록 | `GET /api/v1/admin/contract` — 상태·정류소·회원·기간 필터 |
| 계약 수동 갱신 | `POST /api/v1/admin/contract/{id}/renew` |
| 계약 강제 취소 | `DELETE /api/v1/admin/contract/{id}` — 취소 사유 필수 |
| 결제 수기 등록 | `POST /api/v1/admin/contract/{id}/payment` — 납부 금액·일자 기록 |
| 연체 처리 | `PUT /api/v1/admin/contract/{id}/overdue` |

### 4. 대시보드 통계 API (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 계약 현황 요약 | `ACTIVE`/`EXPIRED`/`CANCELLED` 건수, 이번달 신규 계약 수 |
| 매출 집계 | 월별/분기별 `paid_amount` 합산, 미수금 현황 |
| 예약 현황 요약 | 상태별 예약 건수, 오늘 상담 예정 건수 |
| 정류소 가용률 | 전체 정류소 대비 계약 체결 정류소 비율 |
| 갱신율 통계 | 만료 계약 대비 갱신 계약 비율 |

### 5. 감사 로그 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 관리자 행위 기록 | 계약 생성/취소, 회원 상태 변경 등 관리자 액션 로그 |
| 감사 로그 조회 | `GET /api/v1/admin/audit` — 행위자·대상·기간 필터 |
| 민감 작업 이중 승인 | `ADMIN_MASTER` 권한이 필요한 작업에 대한 2단계 승인 플로우 |
