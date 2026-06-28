# 영업사원 정산 비율(커미션) 관리 — 설계 문서

> 영업사원이 계약을 체결하면 커미션(수수료)을 가져가는 구조에서, **정산 비율**을
> 관리·수정·이력화하기 위한 도메인/DB/API 설계를 정리한다.

---

## 1. 요구사항
 
1. 영업사원은 **기본 10%** 의 정산 비율을 가진다.
2. 영업사원의 정산 비율은 **Master 권한**을 가진 사람만 조회·수정할 수 있다.
3. 정산 시 기본적으로 해당 영업사원의 정산 비율을 반영하되,
   특수한 경우 **Master가 해당 계약의 정산 비율을 임의로 오버라이드**할 수 있다.

---

## 2. 도메인 소유권 결정

### 2-1. 정산 비율의 소유자 = IAM

정산 비율은 **영업사원(=멤버)의 속성**이다. 따라서 비율의 마스터값과 변경 이력은
모두 **IAM 모듈(`auth` 스키마)** 이 단독으로 소유한다.

- 커미션 관련 **write 진입점을 IAM 한 곳으로 단일화** → 감사(audit) 추적 용이
- 변경 이력이 여러 모듈로 분산되지 않음

### 2-2. 계약별 오버라이드도 IAM에서 관리 (스냅샷 미사용)

초기에는 "계약 속성이니 Reservation 계약 테이블에 비율 스냅샷 컬럼을 둔다"는 안을
검토했으나 **폐기**했다. 이유:

- 같은 값이 IAM 이력과 Reservation 계약에 **중복 저장**됨
- 오버라이드 시 두 모듈을 동시에 갱신 → **분산 트랜잭션 / 정합성 위험**
- Reservation이 "커미션"이라는 IAM 도메인 개념을 침범

> 스냅샷(시점 고정)이 진짜 필요해지는 순간은 **정산이 실제 발생하는 시점**이며,
> 그때 정산 결과 레코드(`settlement`)에 "그 시점의 비율 + 계산 결과"를 함께 기록하면
> 자연스러운 스냅샷이 된다. 계약 테이블에 미리 복제할 이유가 없다.

### 2-3. 관심사 분리

| 관심사 | 소유 모듈 | 비고 |
|--------|-----------|------|
| 커미션율 **마스터값 + 이력** (기본율, 계약별 오버라이드) | **IAM** (`auth`) | 영업사원=멤버 속성, write 단일 진입점 |
| "이 계약에 **적용할 최종 비율**" 결정 (오버라이드 우선, 없으면 기본율) | **IAM** (`auth`) | 우선순위 로직을 IAM에 캡슐화 |
| 정산 **계산 + 결과** (`금액 × 비율`) | **Reservation** | 계약 금액(분모)을 소유. 변동성 큰 쪽이 비율을 당겨옴 |

**현재 작업 범위는 위 표의 IAM 영역(비율 관리)** 이다.
정산 계산(Reservation)은 별도 작업으로 분리한다.

---

## 3. 데이터 흐름

```
[비율 변경 흐름]  Master → IAM 만 write
  기본율 수정       → manager_commission 갱신 + commission_rate_history 기록
  계약별 오버라이드  → contract_commission_override upsert + commission_rate_history 기록

[정산 발생 흐름]  계약 완납 트리거 (Reservation, 향후 구현)
  Reservation → IAM "contract_id 의 최종 적용 비율?" 질의
             → IAM: 오버라이드 있으면 그것, 없으면 영업사원 기본율 반환
             → Reservation: 금액 × 비율 계산 → settlement 레코드에 결과+비율 기록
```

### 정산 비율 적용 우선순위

```
1순위: contract_commission_override.commission_rate  (해당 계약에 오버라이드가 있으면)
2순위: manager_commission.commission_rate            (영업사원 기본율)
3순위: 10.00 (DEFAULT)                                (기본율 레코드도 없으면 시스템 기본값)
```

---

## 4. DB 설계

스키마: `auth` (IAM). Liquibase changelog: `V009__create_commission_tables.xml`
ORM: Exposed DAO 패턴 (Table object + Entity 분리, 다음 단계에서 구현).

**"현재값 테이블 + 이력 테이블" 분리 원칙** — 현재 유효 비율은 전용 테이블에서
바로 조회하고, 변경 이력은 append-only 단일 테이블에 통합한다.
(오버라이드 현재값을 이력에서 `ORDER BY ... LIMIT 1`로 매번 뒤지는 비용 회피)

| 테이블 | 역할 | 키 |
|--------|------|----|
| `manager_commission` | 영업사원 기본 정산 비율 (현재값) | `member_id` UNIQUE |
| `contract_commission_override` | 계약별 정산 비율 오버라이드 (현재값) | `contract_id` UNIQUE |
| `commission_rate_history` | 정산 비율 변경 이력 (append-only) | — |

### 4-1. `auth.manager_commission` — 영업사원 기본 정산 비율

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | uuid | PK | |
| `member_id` | uuid | NOT NULL, UNIQUE, FK→member.id | 대상 영업사원 (1:1) |
| `commission_rate` | DECIMAL(5,2) | NOT NULL, DEFAULT 10.00, CHECK 0~100 | 정산 비율(%), 10.00=10% |
| `created_at` | timestamptz | NOT NULL, now() | |
| `updated_at` | timestamptz | NOT NULL, now() | |

- 영업사원 가입/역할 부여 시 **기본 10%로 생성**한다.

### 4-2. `auth.contract_commission_override` — 계약별 오버라이드

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | uuid | PK | |
| `contract_id` | uuid | NOT NULL, UNIQUE | 대상 계약 (reservation 소유, **논리적 참조** — 모듈 경계상 FK 미설정) |
| `member_id` | uuid | NOT NULL, FK→member.id | 계약 담당 영업사원 (조회 편의용) |
| `commission_rate` | DECIMAL(5,2) | NOT NULL, CHECK 0~100 | 오버라이드 비율(%) |
| `updated_by` | uuid | NOT NULL | 수정한 Master 회원 |
| `created_at` | timestamptz | NOT NULL, now() | |
| `updated_at` | timestamptz | NOT NULL, now() | |

- 인덱스: `member_id` (영업사원별 오버라이드 목록 조회)
- Master가 특수 계약에만 등록. 없으면 기본율(4-1) 적용.

### 4-3. `auth.commission_rate_history` — 변경 이력 (통합)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | uuid | PK | |
| `member_id` | uuid | NOT NULL, FK→member.id | 대상 영업사원 |
| `contract_id` | uuid | NULL | 오버라이드 시에만 채워짐 (NULL=기본율 변경) |
| `change_type` | varchar(20) | NOT NULL | `DEFAULT_RATE` / `CONTRACT_OVERRIDE` |
| `previous_rate` | DECIMAL(5,2) | NULL | 변경 전 비율(최초 생성 시 NULL) |
| `new_rate` | DECIMAL(5,2) | NOT NULL | 변경 후 비율 |
| `reason` | varchar(500) | NULL | 변경 사유 (Master 입력, 선택) |
| `changed_by` | uuid | NOT NULL | 변경한 Master 회원 |
| `created_at` | timestamptz | NOT NULL, now() | |

- 인덱스: `member_id`, `contract_id`
- `change_type`은 후속 단계에서 `BaseEnum` 구현 Enum으로 매핑.

> **H2 호환** — 테스트(H2)에서는 FK / CHECK 제약을 제외한 `*-h2` changeset을 별도 제공
> (기존 `V007` 패턴 준수).

---

## 5. API 설계

> DB 설계 확정 후 작성 예정.
> 컨트롤러: `modules/iam/.../admin/controller/AdminManagerCommissionController`

(다음 단계에서 작성)
