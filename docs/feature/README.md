# Feature 목록 인덱스

프로젝트 현황 분석을 기반으로 카테고리별로 정리한 추가 기능 목록.

---

## 카테고리별 문서

| 파일 | 대상 모듈 | 주요 내용 |
|------|-----------|-----------|
| [reservation.md](./reservation.md) | `reservation` | 예약 저장·조회, 상태 관리, 가용성 검증, 관리자 예약 관리 |
| [contract.md](./contract.md) | `reservation` | 계약 조회, 갱신·취소·만료 생명주기, 결제 관리, 통계 |
| [stop.md](./stop.md) | `stop` | 정류소 CRUD 완성, 공공 API 자동화, 가용성 관리, 지도 연동 |
| [iam.md](./iam.md) | `iam` | 이메일 발송, 회원 프로필, 역할 관리 API, 보안 강화, 소셜 로그인 |
| [notification.md](./notification.md) | 신규 모듈 | 이메일·SMS·푸시 알림, 알림 이력, 실시간 WebSocket |
| [admin.md](./admin.md) | 전 모듈 | 관리자 회원·예약·계약 관리, 대시보드 통계, 감사 로그 |
| [infra.md](./infra.md) | 전 모듈 | 스케줄러, 테스트, 모니터링, AWS 연동, API 문서화, 성능 |
| [settlement.md](./settlement.md) | 신규 `settlement` (8184) | 정산 주기, 인센티브 산정·승인·지급, 영업사원 실적, 정산서 |

---

## 우선순위 요약

### 즉시 구현 필요 (블로커)

1. **예약 저장 로직** — `ReservationService.createReservation()` 미구현으로 예약 API 동작 불가
2. **이메일 발송 연동** — AWS SES 미연결로 회원가입 이메일 인증 불완전

### 높은 우선순위

3. 예약 목록 조회 (`GET /api/v1/reservation/me`)
4. 계약 조회·취소·갱신 API
5. 계약 만료·갱신 스케줄러
6. 공공 API 벌크 등록 접근 제어 완성

### 중간 우선순위

7. 관리자 전용 조회 API (회원·예약·계약)
8. 알림 모듈 설계 및 이메일 템플릿
9. Spring Boot Actuator + Micrometer 모니터링
10. Repository/Service 단위 테스트 + Testcontainers 통합 테스트

### 낮은 우선순위

11. 지도 기반 정류소 조회
12. 소셜 로그인 (OAuth2)
13. 실시간 WebSocket 알림
14. Resilience4j Circuit Breaker / Bulkhead
