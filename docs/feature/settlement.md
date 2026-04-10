# 정산 (Settlement) 기능 목록

신규 `settlement` 모듈 (port 8184)로 분리. 계약·인센티브 정산을 독립 Bounded Context로 관리.

---

## 모듈 위치 결정 근거

| 구분 | Contract 도메인 | Settlement 도메인 |
|------|----------------|-------------------|
| 핵심 언어 | 계약, 갱신, 취소 | 정산서, 지급, 승인, 정산 주기 |
| 주체 | 영업/운영팀 | 재무팀 |
| 변경 빈도 | 계약 이벤트 중심 | 월/분기 주기 중심 |
| 집계 루트 | `Contract` | `Settlement`, `IncentivePayout` |

→ 별도 Bounded Context이므로 독립 모듈로 분리.

---

## 모듈 구조

```
modules/
├── gateway        (8080)
├── iam            (8181)
├── stop           (8182)
├── reservation    (8183)  ← 계약·예약 (정산 트리거 소스)
└── settlement     (8184)  ← 정산 신규 모듈
```

---

## DB 스키마 (`settlement` 스키마)

```
settlement 스키마
├── settlement_cycle     — 정산 기준 주기 (월/분기, 시작일, 종료일, 상태)
├── settlement           — 주기별 정산서 (총 계약 수, 총 매출, 상태)
└── incentive_payout     — 영업사원별 인센티브 지급 내역
```

**설계 원칙:**
- `incentive_payout.contract_id`는 `reservation` 모듈 계약 ID를 ID로만 보관 — **DB FK 금지** (모듈 경계 유지)
- `incentive_payout.referrer_id`, `referrer_name` 스냅샷 — 영업사원 탈퇴 후에도 이력 보존

---

## 데이터 연동 전략

현재 프로젝트는 Kafka 없이 RestClient S2S 방식. 단계적 전환 계획:

### 1단계: 폴링 방식 (현재 아키텍처 유지)

```
settlement 스케줄러
  → reservation S2S: 정산 기간 내 PAID 계약 + referrer 목록 조회
  → 인센티브 산정 후 incentive_payout 저장
```

- 장점: 현재 인프라 변경 없음, 구현 단순
- 단점: 실시간성 없음, reservation 모듈 S2S 결합도 존재

### 2단계: 이벤트 드리븐 (향후 Kafka/Redis Streams 도입 시)

```
reservation: 계약 결제 완료 이벤트 발행 (ContractPaidEvent)
  → settlement: 이벤트 구독 → 인센티브 즉시 산정 및 저장
```

- 장점: 모듈 간 완전한 디커플링, 실시간 정산
- 단점: Kafka 인프라 추가 필요

---

## 추가 기능 목록

### 1. 정산 주기 관리 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 정산 주기 생성 | `POST /api/v1/admin/settlement/cycle` — 월/분기 정산 기간 생성 (시작일, 종료일) |
| 정산 주기 조회 | `GET /api/v1/admin/settlement/cycle` — 전체 정산 주기 목록 |
| 정산 실행 | `POST /api/v1/admin/settlement/cycle/{id}/run` — 해당 주기 내 PAID 계약 집계 + 인센티브 산정 |
| 정산 상태 관리 | `DRAFT` → `CONFIRMED` → `CLOSED` 상태 전이. CONFIRMED 이후 수정 불가 |
| 정산 자동 실행 | 스케줄러: 정산 주기 종료일 다음날 자동 실행 |

### 2. 인센티브 지급 관리 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 인센티브 비율 설정 | `POST /api/v1/admin/settlement/incentive-rate` — 영업사원별 또는 전사 기본 비율(%) 설정 |
| 인센티브 자동 산정 | 정산 실행 시 `paid_amount × 비율` 자동 계산 후 `incentive_payout` 저장 |
| 인센티브 목록 조회 | `GET /api/v1/admin/settlement/{cycleId}/incentive` — 주기별 영업사원 인센티브 목록 |
| 인센티브 지급 승인 | `PUT /api/v1/admin/settlement/incentive/{id}/approve` — 관리자 승인 (`PENDING` → `APPROVED`) |
| 인센티브 지급 처리 | `PUT /api/v1/admin/settlement/incentive/{id}/paid` — 실제 지급 완료 기록 (`APPROVED` → `PAID`, `paid_at` 기록) |
| 인센티브 지급 취소 | `PUT /api/v1/admin/settlement/incentive/{id}/cancel` — 지급 전 취소 (`CANCEL_REASON` 필수) |

### 3. 영업사원 실적 조회 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 내 실적 조회 | `GET /api/v1/settlement/my` — 영업사원 본인의 정산 주기별 실적 + 인센티브 내역 |
| 영업사원별 실적 | `GET /api/v1/admin/settlement/referrer/{memberId}` — 기간별 유치 계약 수, 총 매출, 인센티브 합계 |
| 실적 랭킹 | `GET /api/v1/admin/settlement/ranking` — 기간 내 영업사원 매출 기여 순위 |

### 4. 정산서 발행 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 정산서 조회 | `GET /api/v1/admin/settlement/{cycleId}/report` — 주기별 정산 요약 (총 계약, 총 매출, 총 인센티브 지급액) |
| 정산서 PDF 출력 | 정산서 PDF 생성 및 다운로드 (iText 또는 JasperReports) |
| 매출 집계 | 기간별 `paid_amount` 합산, `payment_cycle`(월/분기/연)별 분류 |

---

## 인센티브 상태 플로우

```
PENDING → APPROVED → PAID
           ↓
        CANCELLED
```

- `PENDING`: 정산 실행 시 자동 생성
- `APPROVED`: 관리자 승인 완료
- `PAID`: 실제 지급 처리 완료 (`paid_at` 기록)
- `CANCELLED`: 지급 전 취소 (APPROVED 단계까지만 취소 가능)

---

## 갱신 계약 인센티브 귀속 정책

**갱신 시점 담당자 기준** — `POST /api/v1/contract/{id}/renew` 요청 시 새로운 `referrer_id`를 명시적으로 받음. 이전 계약(`previous_contract_id`)의 추천인은 갱신 인센티브에 영향 없음.
