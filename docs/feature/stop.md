# 정류소 (Stop) 기능 목록

현재 `stop` 모듈에서 추가가 필요한 기능 목록.

---

## 현재 구현 상태

- [x] 정류소 단건 수기 등록 (`POST /api/v1/stop/simple`)
- [x] 정류소 조회 (`GET /api/v1/stop`) — pk/stopId/stopName 기준
- [x] 정류소 변경 이력 기록 (`stop_update_history`)
- [x] 서울시 공공 API 연동 기본 구조 (`SeoulBusApiClient`)
- [x] S2S 내부 API (`InternalStopController`)
- [ ] 공공 API 벌크 등록 접근 제어 미완성

---

## 추가 기능 목록

### 1. 정류소 CRUD 완성 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 정류소 수정 | `PUT /api/v1/stop/{id}` — 정류소 정보 수정 + `stop_update_history` 자동 기록 |
| 정류소 삭제 | `DELETE /api/v1/stop/{id}` — 소프트 삭제 (계약 연결 여부 선검증) |
| 정류소 목록 조회 | `GET /api/v1/stop/list` — 타입·이름 필터 + 페이지네이션 |
| 정류소 변경 이력 조회 | `GET /api/v1/stop/{id}/history` — 해당 정류소의 변경 이력 목록 |

### 2. 공공 API 연동 완성 (우선순위: 높음)

| 기능 | 설명 |
|------|------|
| 벌크 등록 접근 제어 | `StopRegisterController`에 S2S 토큰 검증 필터 적용 |
| 벌크 등록 스케줄러 | 주기적으로 서울시 공공 API를 호출해 정류소 데이터 자동 갱신 |
| 공공 API 변경분 감지 | 기존 정류소와 비교해 변경된 항목만 업데이트 + 이력 기록 |
| 신규 정류소 자동 감지 | 공공 API에 새로 추가된 정류소 자동 등록 |
| 폐지 정류소 처리 | 공공 API에서 사라진 정류소 비활성화 처리 |

### 3. 정류소별 가격 관리 (우선순위: 높음)

정류소마다 유동 인구, 위치, 노선 수 등에 따라 광고 단가가 다르므로 별도 가격 테이블이 필요.
현재 `contract_detail.total_amount`는 수기 입력으로, 기준 단가 없이 계약마다 금액이 제각각임.

**스키마 설계 방향:**
- `stop_price` 테이블 신설 (stop 스키마)
  - `stop_id` (FK) — 정류소
  - `payment_cycle` (`MONTHLY`/`QUARTERLY`/`YEARLY`) — 결제 주기별 단가
  - `base_price` — 기준 단가
  - `effective_from` — 단가 적용 시작일 (이력 관리)
  - `effective_to` — 단가 종료일 (nullable, 현행 단가는 null)

| 기능 | 설명 |
|------|------|
| 정류소 단가 등록 | `POST /api/v1/stop/{id}/price` — 결제 주기별 단가 설정 (ADMIN) |
| 정류소 단가 수정 | `PUT /api/v1/stop/{id}/price` — 기존 단가 종료 후 신규 단가 시작 (이력 보존) |
| 정류소 단가 조회 | `GET /api/v1/stop/{id}/price` — 현행 단가 + 이력 목록 |
| 계약 생성 시 단가 자동 적용 | `ContractFacade`에서 Stop S2S로 기준 단가 조회 후 `contract_detail.total_amount` 자동 산정 |
| 단가 미설정 정류소 예약 차단 | 단가가 없는 정류소는 계약 생성 불가 (Validator에서 검증) |
| 일괄 단가 설정 | `PUT /api/v1/admin/stop/price/bulk` — `StopType`별 또는 복수 정류소 단가 일괄 적용 |

> **설계 주의사항:**
> - 단가 변경 시 기존 `ACTIVE` 계약 금액은 불변 — `contract_detail`에 계약 시점 단가 스냅샷 보존
> - `payment_cycle`별로 단가가 다를 수 있음 (예: 월 100만원, 분기 270만원, 연 1000만원)
> - 할인율 적용이 필요하다면 `discount_rate` 필드 추가 고려

---

### 4. 정류소 가용성 관리 (우선순위: 중간)

| 기능 | 설명 |
|------|------|
| 정류소 광고 가능 여부 표시 | `ACTIVE` 계약이 있는 정류소는 `예약 불가` 상태 노출 |
| 가용 정류소 목록 조회 | `GET /api/v1/stop/available` — 계약 없는 정류소만 조회 |
| 정류소 타입별 통계 | `StopType`별 등록 수, 계약 수 집계 |

### 4. 지도 연동 (우선순위: 낮음)

| 기능 | 설명 |
|------|------|
| 좌표 기반 근처 정류소 조회 | `GET /api/v1/stop/nearby?lat=&lng=&radius=` — 반경 내 정류소 목록 |
| 정류소 지도 뷰 지원 | 위경도(`x_crd`, `y_crd`) 데이터를 지도 클라이언트에 제공하는 응답 포맷 |
