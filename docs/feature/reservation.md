# 예약 (Reservation) 기능 목록

현재 `reservation` 모듈의 예약 도메인에서 추가·완성이 필요한 기능 목록.

---

## 현재 구현 상태

- [x] 예약 엔티티 및 테이블 스키마 (`reservation`, `reservation_consultation`)
- [x] 예약 생성 Facade (Stop S2S 호출까지)
- [ ] 예약 저장 로직 (`ReservationService.createReservation()`) **← 블로커**
- [ ] 예약 목록 조회 (`GET /api/v1/reservation/me`)

---

## 추가 기능 목록

### 1. 예약 저장 및 조회 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 예약 저장 | `ReservationService.createReservation()` 구현. `ReservationEntity` + `ReservationConsultationEntity` 저장 |
| 내 예약 목록 조회 | `GET /api/v1/reservation/me` — 로그인 회원의 예약 목록 페이지네이션 |
| 예약 단건 조회 | `GET /api/v1/reservation/{id}` — 예약 상세 조회 (상담 내역 포함) |
| 예약 취소 | `DELETE /api/v1/reservation/{id}` — 상담 전 단계에서만 취소 허용 |

### 2. 예약 상태 관리 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 예약 상태 Enum 정의 | `PENDING` → `CONSULTING` → `COMPLETED` → `CANCELLED` 상태 플로우 |
| 상태 전이 검증 | 유효하지 않은 상태 전이 방어 (Guard/Validator 패턴) |
| 상담 내역 등록 | 관리자가 상담 결과 메모 작성 (`ReservationConsultationEntity.note`) |
| 상담 완료 처리 | 상담 후 예약 상태를 `COMPLETED`로 전환 + 계약 연결 |

### 3. 예약 가능 여부 검증 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 중복 예약 방지 | 동일 정류소에 이미 `ACTIVE` 계약 또는 `PENDING` 예약이 있으면 예약 거부 |
| 예약 가능 날짜 조회 | `GET /api/v1/reservation/available?stopId=` — 정류소별 예약 가능 시작일 조회 |
| 희망 시작일 유효성 검증 | 과거 날짜, 당일 예약 등 비즈니스 룰 검증 |

### 4. 관리자 예약 관리 (우선순위: 낮음)

| 기능 | 설명 |
|------|------|
| 전체 예약 목록 조회 | `GET /api/v1/admin/reservation` — 상태·정류소·기간 필터 + 페이지네이션 |
| 예약 배정 | 관리자가 특정 예약에 담당 매니저 배정 |
| 예약 일괄 처리 | 특정 조건의 예약 일괄 상태 변경 |
