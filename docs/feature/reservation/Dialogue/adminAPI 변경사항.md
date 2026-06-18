# 어드민 예약 API 변경사항 및 라우팅 명세

어드민 사이트 전용 예약 목록 API가 서비스 독립성과 보안 일관성을 확보하기 위해 `reservation` 모듈에서 `iam` 모듈 아래의 통합 어드민 경로(`/api/v1/admin/reservation`)로 진입점이 일원화되었습니다. 

프론트엔드 개발자는 기존 API 경로를 아래의 신규 라우팅 경로로 변경하여 호출하여야 합니다.

> [!IMPORTANT]
> **Gateway 라우팅 준수**
> 개별 서비스 인스턴스 포트(8181, 8183)는 외부에 오픈되어 있지 않습니다. 반드시 **API Gateway 공통 도메인/포트(8080)**를 경유하여 신규 통합 엔드포인트인 `/api/v1/admin/reservation/...` 주소로 요청을 전송해 주시기 바랍니다.

---

## 1. 경로 변경 API 명세

### 회원별 예약 목록 조회
* **HTTP Method**: `GET`
* **기존 엔드포인트 (Reservation 모듈 라우팅)**: 
  * `/api/v1/reservation/admin/members/{memberId}/reservations`
* **신규 엔드포인트 (Gateway 경유 IAM 모듈 라우팅)**: 
  * `/api/v1/admin/reservation/members/{memberId}/reservations`
* **요청 파라미터 (Query Parameters)**:
  * `page` (default: 0, 0-base 페이지 번호)
  * `size` (default: 20, 페이지 크기)
* **인가 요구사항 (수정됨)**:
  * 기존: `MemberCategory.ADMIN` + `Permission.READ` 권한
  * 변경 후: `ADMIN_MASTER` / `ADMIN_DEVELOPER` 타입 + `MANAGE` 권한
