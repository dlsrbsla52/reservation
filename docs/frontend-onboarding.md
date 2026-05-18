# 프론트엔드 개발자 온보딩 가이드

> 본 문서는 **프론트엔드 개발자**가 본 예약 시스템(MSA) 백엔드와 통신할 때 필요한 최소한의 정보를 한 곳에 모아둔 가이드입니다.
> API 명세는 **Swagger UI(OpenAPI 3.0)** 를 단일 진실의 원천(Single Source of Truth) 으로 사용합니다.
> 본 문서가 잘못된 경우 항상 **Swagger UI > 코드 > 본 문서** 순으로 신뢰하십시오.

---

## 1. 프로젝트 한눈에 보기

본 프로젝트는 **버스 정류소 광고 예약 시스템**입니다.
Spring Boot 4.0.5 + Kotlin 2.3.20 기반의 **MSA(Microservices Architecture)** 로, 단일 Repo(Monorepo) 안에 다음 5개 모듈이 독립적인 마이크로서비스로 배포됩니다.

| 서비스 | 포트 | 역할 |
|--------|------|------|
| **gateway** | 8080 | 모든 트래픽의 단일 진입점(Single Entry Point). JWT 검증 후 하위 서비스로 라우팅 |
| **iam** | 8181 | 회원가입 · 로그인 · 토큰 발급 · 회원/관리자 관리 |
| **stop** | 8182 | 버스 정류소 데이터 CRUD (서울 열린데이터광장 연동) |
| **reservation** | 8183 | 정류소 광고 계약 + 예약 핵심 비즈니스 |
| **common / auth-contract** | — | 모든 서비스가 공유하는 라이브러리 모듈 (런타임 서비스 아님) |

추가 인프라:

| 컴포넌트 | 포트 | 용도 |
|----------|------|------|
| PostgreSQL 18.3 | 15433 | 주 DB (모듈별 schema 격리: `auth` / `member` / `stop` / `reservation`) |
| Valkey 8.1.6 (Redis 호환) | 6379 | 캐시 / Refresh Token 저장 / 분산 락 |

> 💡 **프론트엔드 입장에서 알아둘 핵심 한 줄**
> 클라이언트는 **항상 Gateway(8080)** 만 호출합니다. 각 서비스 포트(8181/8182/8183)는 직접 호출하지 않습니다.

---

## 2. Base URL & 환경별 진입점

| 환경 | Base URL |
|------|----------|
| 로컬 개발 (Docker Compose) | `http://localhost:8080` |
| 로컬 개발 (개별 서비스 디버그용, 비권장) | `http://localhost:8181` (iam), `http://localhost:8182` (stop), `http://localhost:8183` (reservation) |
| 스테이징 / 운영 | 별도 공유 (AWS 환경, HTTPS 필수) |

모든 API의 prefix는 `/api/v1/`이며, Gateway가 경로 기준으로 라우팅합니다.

| 경로 패턴 | 라우팅 대상 |
|-----------|-------------|
| `/api/v1/auth/**` | iam (8181) |
| `/api/v1/admin/**` | iam (8181) |
| `/api/v1/member/**` | iam (8181, S2S 전용 — 직접 호출 불가) |
| `/api/v1/stop/**` | stop (8182) |
| `/api/v1/reservation/**` | reservation (8183) |
| `/api/v1/contract/**` | reservation (8183) |

---

## 3. Swagger UI를 통한 API 개발 (가장 중요)

본 프로젝트는 **springdoc-openapi** 기반으로 각 마이크로서비스마다 독립적인 Swagger UI를 제공합니다.
API 명세, 요청/응답 스키마, 시도(Try it out)는 **모두 Swagger UI에서 확인**하는 것을 표준 워크플로로 잡습니다.

### 3-1. Swagger UI 진입점

| 서비스 | Swagger UI | OpenAPI JSON (스펙 다운로드) |
|--------|------------|-------------------------------|
| iam (인증/회원/관리자) | http://localhost:8181/swagger-ui.html | http://localhost:8181/api-docs |
| stop (정류소) | http://localhost:8182/swagger-ui.html | http://localhost:8182/api-docs |
| reservation (예약/계약) | http://localhost:8183/swagger-ui.html | http://localhost:8183/api-docs |

> ⚠️ **주의**: Gateway(8080)는 Swagger UI를 제공하지 않습니다. 명세 확인 시에는 **각 서비스 포트로 직접 접근**하세요.
> 단, 실제 API 호출은 명세에서 본 경로를 그대로 **Gateway(8080)** 에 사용하면 됩니다.

### 3-2. 프론트엔드 권장 워크플로

1. **명세 확인** — 각 서비스의 Swagger UI에서 엔드포인트 · Request/Response DTO · 에러 코드를 확인
2. **OpenAPI 스펙 다운로드** — `/api-docs` 엔드포인트에서 JSON을 받아 클라이언트 코드 자동 생성
   - 권장 도구: [openapi-typescript](https://github.com/drwpow/openapi-typescript), [orval](https://orval.dev/), [openapi-generator-cli](https://openapi-generator.tech/)
   - 예: `npx openapi-typescript http://localhost:8183/api-docs -o ./src/api/reservation.ts`
3. **Mock & 시도** — Swagger UI의 `Try it out` 기능으로 실제 응답 확인 후 프론트엔드 구현 시작
4. **변경 감지** — 백엔드 PR 머지 후 `/api-docs` 스펙을 재생성하여 타입을 동기화 (CI에 자동화 권장)

### 3-3. Swagger UI에서 인증 토큰 사용하기

대부분의 API는 JWT 인증이 필요합니다. Swagger UI 우측 상단 **Authorize** 버튼을 클릭하여 토큰을 주입할 수 있습니다.

```
Authorization: Bearer {accessToken}
```

토큰은 `POST /api/v1/auth/login` 응답 본문의 `accessToken` 필드를 사용합니다.

---

## 4. 공통 응답 포맷 (`ApiResponse<T>`)

모든 API 응답은 다음 래퍼로 직렬화됩니다. 프론트엔드는 응답 처리 시 항상 `code`로 분기하고 `data`를 본 페이로드로 추출하세요.

### 4-1. 성공 응답

```json
{
  "code": "00000",
  "message": "success",
  "data": { /* 실제 페이로드 */ }
}
```

### 4-2. 데이터 없는 성공 응답

```json
{
  "code": "00000",
  "message": "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.",
  "data": null
}
```

### 4-3. 페이지 응답 (`PageResult<T>`)

```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "content": [ /* 항목 배열 */ ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7
  }
}
```

> 페이지네이션 파라미터는 **`?page=0&size=20`** 형식, `page`는 **0-base** 입니다.

### 4-4. 에러 응답

```json
{
  "code": "00301",
  "message": "이메일 또는 비밀번호가 일치하지 않습니다.",
  "data": null
}
```

- `code`는 **5자리 영숫자 문자열** (예: `00000` 성공, `00301` 인증 실패 등)
- HTTP 상태 코드는 예외 종류에 따라 `400`/`401`/`403`/`404`/`500`이 자동 매핑됩니다.
- 비즈니스 에러 코드는 모듈별로 영역이 분리되어 있으며, 정확한 매핑은 **Swagger 응답 예시 + 백엔드 `Result` enum** 을 참고하세요.

| 상태 코드 | 의미 | 프론트엔드 대응 가이드 |
|-----------|------|------------------------|
| 400 | `BusinessException` — 유효성/비즈니스 위반 | 사용자에게 `message` 노출 |
| 401 | 인증 누락/만료 | `/api/v1/auth/token/refresh` 호출 후 재시도. 실패 시 로그인 페이지 이동 |
| 403 | 권한 없음 | 페이지 접근 차단 / 안내 모달 |
| 404 | 리소스 없음 | 404 화면 또는 토스트 |
| 500 | 서버 오류 | 사용자에게는 일반 메시지, 상세는 Sentry/로그 |

---

## 5. 인증 흐름 (JWT)

### 5-1. 토큰 전달 전략

| 토큰 | 전달 방식 | 저장소 | 만료 |
|------|-----------|--------|------|
| **Access Token** | Response Body `data.accessToken` | 클라이언트 메모리 (XSS 방어 위해 `localStorage` 비권장) | 60분 |
| **Refresh Token** | **HttpOnly Cookie** `refresh_token` | 서버가 자동 설정. JS 접근 불가 | 7일 |

> Refresh Token은 **자바스크립트로 읽을 수 없습니다.** 자동으로 쿠키에 저장되며, `/api/v1/auth/token/refresh` 호출 시 자동 전송됩니다.
> 프론트엔드는 **Refresh Token을 직접 다루지 않습니다.**

### 5-2. 인증 시퀀스

```
[로그인]
  Client → POST /api/v1/auth/login  { loginId, password }
  Server → 200 OK
           Body: { code: "00000", data: { accessToken: "eyJ..." } }
           Set-Cookie: refresh_token=...; HttpOnly; Path=/api/v1/auth; Max-Age=604800

[인증 필요 API 호출]
  Client → GET /api/v1/reservation/me
           Headers: Authorization: Bearer eyJ...
  Server → 200 OK / 401 (만료)

[토큰 갱신]
  Client → POST /api/v1/auth/token/refresh
           (HttpOnly Cookie 자동 전송)
  Server → 200 OK
           Body: { code: "00000", data: { accessToken: "새토큰" } }
           Set-Cookie: refresh_token=새토큰 (Token Rotation)

[로그아웃]
  Client → POST /api/v1/auth/logout
           Headers: Authorization: Bearer eyJ...
  Server → 200 OK (Cookie 즉시 만료)
```

### 5-3. CORS · Cookie 주의사항

- 모든 인증 관련 요청(`/api/v1/auth/**`)은 `credentials: 'include'`(fetch) 또는 `withCredentials: true`(axios) 설정이 **반드시 필요**합니다.
- Refresh Token Cookie의 Path는 `/api/v1/auth`로 제한되어 있어, 그 외 경로로는 자동 전송되지 않습니다.
- 운영 환경에서는 `Secure` 플래그가 활성화되어 **HTTPS 환경에서만 동작**합니다.

### 5-4. axios 인터셉터 예시

```ts
import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080",
  withCredentials: true, // Refresh Cookie 송수신
});

let accessToken: string | null = null;

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;
      const { data } = await api.post("/api/v1/auth/token/refresh");
      accessToken = data.data.accessToken;
      original.headers.Authorization = `Bearer ${accessToken}`;
      return api(original);
    }
    return Promise.reject(error);
  },
);

export default api;
```

---

## 6. 주요 API 카탈로그 (요약)

> 상세 명세는 **반드시 각 서비스의 Swagger UI**에서 확인하십시오. 본 표는 화면 설계용 개요입니다.

### 6-1. 인증 (iam, `/api/v1/auth`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/register` | 회원가입 (이메일 인증 메일 발송) |
| POST | `/login` | 로그인 → Access Token + Refresh Cookie |
| GET  | `/verify-email?token=...` | 이메일 인증 |
| POST | `/token/refresh` | Access Token 재발급 |
| POST | `/verify` | 2차 본인 인증 (민감 작업 전) |
| POST | `/logout` | 현재 세션 로그아웃 |
| POST | `/password-reset/request` | 비밀번호 초기화 메일 요청 |
| POST | `/password-reset/verify` | 초기화 토큰 검증 |
| POST | `/password-reset/confirm` | 새 비밀번호로 변경 |
| POST | `/password/change` | 로그인 상태에서 비밀번호 변경 |
| POST | `/reactivate` | 비활성화 계정 재활성화 |
| GET  | `/sessions` | 내 활성 세션 목록 |
| DELETE | `/sessions/{sessionId}` | 특정 세션 강제 로그아웃 |
| DELETE | `/sessions` | 현재 세션 제외 모두 로그아웃 |

### 6-2. 회원 (iam, `/api/v1/member`)

| Method | Path | 설명 |
|--------|------|------|
| GET  | `/find/me` | 내 정보 조회 |
| POST | `/modify` | 내 정보 수정 |
| DELETE | `/withdraw` | 회원 탈퇴 |
| POST | `/deactivate` | 계정 비활성화 |

### 6-3. 관리자 (iam, `/api/v1/admin`) — ADMIN 권한 필요

| Method | Path | 설명 |
|--------|------|------|
| POST | `/members` | 회원 생성 |
| GET  | `/members` | 회원 목록 |
| GET  | `/members/search` | 회원 검색 |
| GET  | `/members/{memberId}` | 회원 상세 |
| PUT  | `/members/{memberId}/suspend` | 회원 정지 |
| PUT  | `/members/{memberId}/unsuspend` | 정지 해제 |
| GET  | `/members/{memberId}/status-history` | 상태 변경 이력 |
| GET/POST/PUT/DELETE | `/authorization/...` | 역할/권한 관리 |

### 6-4. 정류소 (stop, `/api/v1/stop`)

| Method | Path | 설명 |
|--------|------|------|
| GET  | `/api/v1/stop` | 정류소 목록 (검색/필터) |
| POST | `/api/v1/stop/simple` | 정류소 단건 등록 (수동) |
| POST | `/api/v1/stop/register/bulk` | 서울 열린데이터광장 일괄 동기화 |

### 6-5. 계약 (reservation, `/api/v1/contract`) — ADMIN + WRITE 권한 필요(생성)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/contract` | 계약 생성 (정류소 광고) |
| GET  | `/api/v1/contract/me?page=0&size=20` | 내 계약 목록 |
| GET  | `/api/v1/contract/{contractId}` | 계약 단건 상세 |

### 6-6. 예약 (reservation, `/api/v1/reservation`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/reservation` | 예약 생성 |
| GET  | `/api/v1/reservation/me?page=0&size=20` | 내 예약 목록 |
| GET  | `/api/v1/reservation/{reservationId}` | 예약 상세 |
| DELETE | `/api/v1/reservation/{reservationId}` | 예약 취소 (PENDING 단계만 가능) |

---

## 7. 권한 모델

본 시스템은 두 축으로 권한을 표현합니다. 프론트엔드 라우팅 가드는 **JWT Payload의 클레임** 또는 `GET /api/v1/member/find/me` 응답을 사용해 구현합니다.

| 클레임 | 의미 | 예시 값 |
|--------|------|---------|
| `memberType` | 회원 카테고리 | `MEMBER`, `ADMIN`, `ADMIN_MASTER` 등 |
| `permissions` | 행위 권한 (쉼표 구분) | `READ,WRITE,MANAGE` |
| `emailVerified` | 이메일 인증 여부 | `true` / `false` |

> JWT Payload는 Base64URL로 인코딩되어 있을 뿐 **암호화되지 않습니다.** 클라이언트에서 디코딩하여 UI 분기로만 사용하고, **민감한 로직은 반드시 서버 응답을 신뢰**하십시오.

---

## 8. 로컬 백엔드 실행 방법 (프론트엔드 개발자용)

프론트엔드 개발자가 백엔드 환경을 직접 띄울 때 사용하는 최단 경로입니다.

### 8-1. 사전 준비

- Docker Desktop (또는 OrbStack)
- 포트 충돌 확인: `8080`, `8181`, `8182`, `8183`, `15433`, `6379`

### 8-2. 실행

```bash
# 1. 저장소 클론 후 디렉토리 이동
cd reservation

# 2. 전체 서비스 기동 (로컬 모드)
docker-compose -f docker-compose-local.yml up -d --build

# 3. 정상 기동 확인 — gateway 헬스체크
curl http://localhost:8080/api/v1/gateway/health-check

# 4. Swagger UI 접속 — 예: 예약 서비스
open http://localhost:8183/swagger-ui.html
```

### 8-3. 종료

```bash
docker-compose -f docker-compose-local.yml down
```

> 💡 `postgres_data` Volume이 매핑되어 있어 `down`만으로는 DB 데이터가 보존됩니다. 데이터까지 초기화하려면 `-v` 옵션을 추가하십시오.

---

## 9. 백엔드 ↔ 프론트엔드 협업 규칙

1. **API 변경은 PR + Swagger 갱신을 동반합니다.**
   백엔드 PR 머지 후 프론트엔드는 `/api-docs` 스펙을 재생성해 타입 동기화하세요.
2. **신규 API는 Swagger `@Operation` · `@Tag` 문서가 필수입니다.**
   누락된 명세가 발견되면 백엔드에 이슈로 알려주세요.
3. **에러 코드(`code`) 카탈로그가 필요합니다.**
   누락된 코드가 발견되면 백엔드와 함께 정의 후 본 문서에도 표를 보강합니다.
4. **민감 데이터 노출 금지.**
   `accessToken`을 `localStorage`에 저장하지 않고, 비밀번호/주민번호 등은 Payload에 절대 포함하지 않습니다.
5. **인증이 필요한 모든 요청은 `withCredentials: true`로.**
   Refresh Cookie 흐름이 깨지면 자동 재로그인 동작이 망가집니다.

---

## 10. 참고 문서

- 프로젝트 전체 README — [`/README.md`](../README.md)
- 모듈별 상세 — `modules/<module>/CLAUDE.md`
  - iam: [`modules/iam/CLAUDE.md`](../modules/iam/CLAUDE.md)
  - stop: [`modules/stop/CLAUDE.md`](../modules/stop/CLAUDE.md)
  - reservation: [`modules/reservation/CLAUDE.md`](../modules/reservation/CLAUDE.md)
  - gateway: [`modules/gateway/CLAUDE.md`](../modules/gateway/CLAUDE.md)
  - common: [`modules/common/CLAUDE.md`](../modules/common/CLAUDE.md)
- OpenAPI 스펙
  - iam: http://localhost:8181/api-docs
  - stop: http://localhost:8182/api-docs
  - reservation: http://localhost:8183/api-docs