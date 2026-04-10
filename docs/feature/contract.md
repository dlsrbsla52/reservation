# 계약 (Contract) 기능 목록

현재 `reservation` 모듈의 계약 도메인에서 추가가 필요한 기능 목록.

---

## 현재 구현 상태

- [x] 계약 생성 (`POST /api/v1/contract`) — ContractFacade → IAM/Stop S2S → ContractService
- [x] 계약 엔티티 (`contract`, `contract_detail`) 및 Liquibase 스키마
- [x] 계약 갱신 체인 구조 (`previous_contract_id`)
- [x] 자동 갱신 플래그 (`auto_renewal`)
- [ ] 계약 조회, 갱신, 취소 API

---

## 추가 기능 목록

### 1. 계약 조회 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 계약 단건 조회 | `GET /api/v1/contract/{id}` — 계약 상세 + 결제 정보 포함 |
| 내 계약 목록 조회 | `GET /api/v1/contract/me` — 로그인 회원의 계약 목록 (상태 필터) |
| 정류소별 계약 조회 | `GET /api/v1/contract?stopId=` — 특정 정류소의 계약 이력 조회 |
| 관리자 전체 계약 조회 | `GET /api/v1/admin/contract` — 상태·기간·회원 필터 + 페이지네이션 |

### 2. 계약 생명주기 관리 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 계약 취소 | `DELETE /api/v1/contract/{id}` — `cancel_reason` 저장, `CANCELLED` 상태 전이, `cancelled_at` 기록 |
| 계약 만료 처리 | 스케줄러: `contract_end_date` 도달 시 `EXPIRED` 전환 |
| 계약 갱신 | `POST /api/v1/contract/{id}/renew` — `previous_contract_id` 체인으로 신규 계약 생성 |
| 자동 갱신 처리 | 스케줄러: `auto_renewal=true` + 만료 N일 전 갱신 계약 자동 생성 |
| 갱신 알림 발송 | `renewal_notified_at` 기록 + 회원에게 알림 (이메일/푸시) |

### 3. 결제 관리 (우선순위: 중간)

> `contract_detail`의 `payment_status`, `paid_amount`, `paid_at` 필드 활용

| 기능 | 설명 |
|------|------|
| 결제 등록 | 관리자가 실제 납부 금액·일자 수기 기록 (`PAID` 전환) |
| 연체 처리 | 스케줄러: 납부 기한 초과 시 `OVERDUE` 전환 |
| 결제 주기별 청구 생성 | `MONTHLY`/`QUARTERLY`/`YEARLY` 주기에 따라 청구 레코드 자동 생성 |
| 결제 이력 조회 | `GET /api/v1/contract/{id}/payments` — 청구·납부 이력 |

### 4. 추천인(영업사원) 관리 (우선순위: 높음)

예약 또는 계약 시점에 영업사원을 연결하여, 계약 성사 시 인센티브 산정의 근거로 활용.

**스키마 설계 방향:**
- `reservation.referrer_id` — 예약 시점에 영업사원 회원 ID 기록 (IAM `member` FK, nullable)
- `reservation.referrer_name` — 영업사원 이름 스냅샷 (탈퇴·이름 변경 대비 비정규화)
- `contract.referrer_id` — 계약 생성 시 예약의 추천인 인계 또는 직접 지정
- `referrer_incentive` 테이블 신설 — 계약별 인센티브 지급 내역 별도 관리

| 기능 | 설명 |
|------|------|
| 예약 시 추천인 지정 | `POST /api/v1/reservation` 요청에 `referrerId` 필드 추가. IAM S2S로 유효한 영업사원 검증 |
| 계약 시 추천인 인계 | 예약 → 계약 전환 시 `referrer_id` 자동 인계. 계약 직접 생성 시에도 수동 지정 가능 |
| 추천인별 계약 조회 | `GET /api/v1/admin/referrer/{memberId}/contracts` — 해당 영업사원이 유치한 계약 목록 |
| 인센티브 비율 설정 | 관리자가 영업사원별 또는 전사 기본 인센티브 비율(%) 설정 |
| 인센티브 금액 자동 산정 | 계약 `paid_amount` 확정 시 `인센티브 = paid_amount × 비율` 자동 계산 후 기록 |
| 인센티브 지급 처리 | 관리자가 지급 완료 처리 + 지급일 기록 (`paid_at`) |
| 영업사원 실적 대시보드 | 기간별 유치 계약 수, 총 매출액, 인센티브 합계 조회 |

> **설계 주의사항:**
> - 영업사원이 탈퇴해도 과거 인센티브 이력은 보존 → `referrer_name` 스냅샷 필수
> - **갱신 계약 추천인 귀속 정책: 갱신 시점 담당자 기준** — `POST /api/v1/contract/{id}/renew` 요청 시 새로운 `referrer_id`를 명시적으로 받음. `previous_contract_id`의 추천인은 인계하지 않음
> - `referrer_incentive`는 `contract_detail`과 분리해 독립 테이블로 관리 (결제 내역과 인센티브 내역 혼용 방지)

---

### 5. 계약 상태 통계 (우선순위: 낮음)

| 기능 | 설명 |
|------|------|
| 계약 현황 집계 | `ACTIVE`/`EXPIRED`/`CANCELLED` 건수 집계 API |
| 매출 집계 | 기간별 `paid_amount` 합산 |
| 갱신율 조회 | 만료 계약 대비 갱신 계약 비율 |
| 추천인별 매출 기여도 | 영업사원별 유치 매출 합산 및 전체 대비 비율 |
