# 모듈러 모놀리스 전환 코드 리뷰

- 대상: `bigbang/modular-monolith` (main 대비 커밋 3개, 커밋 `b462133` 이후 작업 트리 변경 포함)
- 작성일: 2026-11-17
- 방법: `/code-review` 서브에이전트가 `main...HEAD` 와 작업 트리를 읽고 15건을 보고했다. 이 문서는 보고를 현재 코드와 대조해 정리한 것이다.
- 상태: **정리만 했다. 아래 항목은 아직 하나도 고치지 않았다.**
- 앞선 문서: [회귀 계획](modular-monolith-migration-plan.md), [S2S 대응표와 검증 결과](modular-monolith-s2s-mapping.md)

## 요약

| 구분 | 건수 | 항목 |
|---|---|---|
| 바로 고칠 것 | 4 | #1, #3, #9, #14 |
| 방향을 정해야 하는 것 | 3 | #4, #5, #6 |
| 배포 환경이 생길 때 | 1 | #7 |
| 낮음·정리 | 7 | #2, #8, #10, #11, #12, #13, #15 |

**출처별로 보면**
- 이번 전환의 security-review 반영분(작업 트리)에서 생긴 것: #1, #2, #13, #15, #5
- 전환 커밋에서 생긴 것: #8, #12(중복), #14(예전 compose 기본값을 그대로 옮김)
- 전환 전(main)부터 있었고 새 코드로 다시 드러난 것: #3, #4, #6, #9
- 구조적 한계: #10, #11, #7

**확인 수준**: 아래 표의 "확인" 칸은 이 문서를 쓰면서 코드를 직접 열어 본 항목(직접)과, 리뷰 에이전트의 보고를 그대로 옮긴 항목(보고)을 구분한다. #1의 소요 시간(60~110ms)은 에이전트가 측정한 값이고 다시 재현하지 않았다.

---

## 바로 고칠 것

### #1 마스킹 정규식의 백트래킹 (DoS 가능성)

| | |
|---|---|
| 위치 | `modules/common/.../logging/AccessLoggingFilter.kt:62,84` (`SENSITIVE_KEY`, `FORM_FIELD_PATTERN`) |
| 출처 | security-review 반영분 (키를 정확 일치에서 "포함"으로 넓히면서 `[A-Za-z0-9_-]*(?:password\|token)[A-Za-z0-9_-]*`가 됨) |
| 확인 | 직접(패턴 형태) / 보고(측정값) |

- 키워드 앞뒤의 탐욕적 `*` 때문에 `=` 없는 긴 쿼리(`tokentoken…` 8KB)에서 백트래킹이 제곱으로 늘어난다. 에이전트 측정으로 replace 1회 60~110ms, 이전 패턴은 0.5~1.7ms였다.
- `AccessLoggingFilter`는 Spring Security 체인보다 바깥(+300)에서 돌고 START·ACCESS에서 두 번 적용된다. 그래서 토큰 없이, 404로 끝나는 요청도 같은 비용을 낸다.
- **수정안**: 키를 possessive `[A-Za-z0-9_-]++`로 잡고, 키워드 포함 여부는 `replace` 람다에서 확인한다. #13(쿼리를 두 번 계산)도 이때 함께 고친다. `AccessLoggingFilterMaskingTest`에 긴 입력의 시간 상한 테스트를 추가한다.

### #3 `findMe`의 `require`가 500을 낸다

| | |
|---|---|
| 위치 | `modules/iam/.../member/service/MemberService.kt:81` |
| 출처 | main부터 있었음(`require(request.phoneNumber …)`가 main에도 있음) |
| 확인 | 직접 |

- 공개 API `GET /api/v1/member/find/me`에 `phoneNumber`·`email` 없이 `memberName`만 보내면 `IllegalArgumentException`이 catch-all로 가서 500/`00100`과 ERROR 로그가 남는다. 비인증 요청으로 오류 알람을 계속 울릴 수 있다.
- **수정안**: `BusinessException(CommonResult.VALIDATION_FAIL, …)`로 바꾼다(400/`00260`).

### #9 `stopName` 조회가 `LIKE '…%'`를 그대로 쓴다

| | |
|---|---|
| 위치 | `modules/stop/.../repository/StopRepository.kt:35`, `StopSearchCriteria.of`(`stopName != null -> ByStopName`) |
| 출처 | main부터 있었음. 이번에 관리자 어댑터(`IamStopAdminAdapter.getStopByStopName`)로도 같은 경로가 열림 |
| 확인 | 직접 |

- `GET /api/v1/admin/stop?stopName=`(빈 문자열)이나 공개 `GET /api/v1/stop?stopName=%25`가 `LIKE '%'`가 되어 정류소 전체와 단가 맵을 페이지 없이 한 번에 읽는다. 공개 쪽은 인증된 일반 회원이면 누구나 반복 호출할 수 있다.
- **수정안**: `StopSearchCriteria.of`에서 빈 값은 400으로 막고, 저장소에서 `%`·`_`·`\`를 이스케이프한다. 같은 위험이 `keyword`(`%$it%`, `StopRepository.kt:59`)에도 있는지 함께 본다.

### #14 운영 compose에 위험한 기본값이 있다

| | |
|---|---|
| 위치 | `docker-compose.yml` `app` 서비스: `DB_PASSWORD: ${POSTGRES_PASSWORD:-postgres}`, `COOKIE_SECURE:-false`, `CORS_ALLOWED_ORIGIN:-http://localhost:3000` (`database` 서비스의 `POSTGRES_PASSWORD:-postgres`도 같음) |
| 출처 | 전환 전 compose의 값을 그대로 옮김. 기존부터 있던 규칙 위반 |
| 확인 | 직접 |

- 루트 `AGENTS.md`의 비밀값 규칙("yml·compose에 운영에 쓰일 수 있는 기본값을 넣지 않는다")과 어긋난다. 서버 `.env`에서 값이 빠지면 기동이 막히지 않고 DB 비밀번호 `postgres`, secure 없는 쿠키, localhost CORS로 뜬다.
- **수정안**: `JWT_SECRET`처럼 `${VAR:?메시지}`로 기동을 막는다. 로컬 compose(`docker-compose-local.yml`)는 기본값을 허용해도 된다.

---

## 방향을 정해야 하는 것

### #4 아이디 찾기가 `GET` + 요청 본문이다

| | |
|---|---|
| 위치 | `modules/iam/.../member/controller/MemberController.kt:43`, `docs/frontend-onboarding.md:275` |
| 출처 | main부터 있었음(`@GetMapping("/find/me")` + `@RequestBody`). 이번 변경에서 문서가 이 사용법을 안내하게 됨 |
| 확인 | 직접 |

- 브라우저 `fetch`는 GET에 본문을 실으면 `TypeError`가 난다. axios는 GET의 `data`를 버려서 서버가 본문 없음(400)으로 응답한다. 문서대로 만든 화면이 동작하지 않는다.
- **선택지**: (a) `POST /api/v1/member/find/me`로 바꾼다(API 변경, 공개 경로 목록과 프런트 갱신 필요), (b) 쿼리 파라미터로 받는다(이름·전화번호·이메일이 URL과 액세스 로그에 남아 비권장). **(a)를 권한다.**

### #5 정류소 단건 등록의 ADMIN_USER 권한이 줄었다

| | |
|---|---|
| 위치 | `modules/stop/.../controller/StopController.kt`(`POST /api/v1/stop/simple` 삭제), `modules/iam/.../AdminStopPriceController.kt:29` |
| 출처 | security-review 반영분 |
| 확인 | 직접 |

- 삭제된 경로는 `@Authorize(categories=[ADMIN], permissions=[WRITE])`였다. 남은 `/api/v1/admin/stop/simple`은 `ADMIN_MASTER`·`ADMIN_DEVELOPER` + `MANAGE`만 허용한다. V002 시드 기준 `ADMIN_USER`는 READ·WRITE만 가지므로 예전에는 등록할 수 있었지만 이제 403이다. 다른 대체 경로는 없다.
- `docs/frontend-onboarding.md:280`의 6-3 제목은 "ADMIN 권한 필요"라고만 적혀 있어 실제 권한과 다르다.
- **결정**: 의도한 축소라면 문서에 "ADMIN_MASTER·ADMIN_DEVELOPER + MANAGE"로 명시한다. 아니라면 관리자 경로의 `@Authorize`를 메서드 단위로 조정한다.

### #6 아이디 조회 창구 자체가 열려 있다 (근본 원인)

| | |
|---|---|
| 위치 | `modules/iam/.../member/service/MemberService.kt:79-89` |
| 출처 | main부터 있었음. security-review가 응답을 `loginId` 하나로 줄였지만 창구는 그대로 |
| 확인 | 부분(공개·응답 분기는 직접, 상태 조건 유무와 rate limit 부재는 보고) |

- 이름과 전화번호(또는 이메일)를 아는 사람은 토큰 없이 로그인 아이디를 얻는다. 성공과 `USER_NOT_FOUND`의 응답이 달라서 (이름, 번호) 조합이 회원인지도 알 수 있다. 에이전트 보고로는 쿼리에 상태 조건이 없어 `WITHDRAWN`·`SUSPENDED` 회원도 나온다. 호출 제한이 없어 대량 수집이 가능하다.
- **근본 수정안**: 아이디는 검증된 이메일·SMS로 보내고 응답은 항상 같게 한다. 이메일 서버가 아직 없어서(`AuthorizeHandlerInterceptor`의 이메일 인증 검사도 꺼져 있음) 당장은 어렵다. 그 전까지는 호출 제한(Caddy 또는 Bucket4j)과 상태 조건 추가가 최소 방어다.

---

## 배포 환경이 생길 때

### #7 프록시 뒤에서는 모든 IP가 프록시 주소로 기록된다

| | |
|---|---|
| 위치 | `modules/iam/.../auth/support/AuthCookieSupport.kt:81`, `AuditLogService.kt:64` |
| 출처 | security-review 반영분 (`X-Forwarded-For`를 읽지 않고 `remoteAddr`만 사용) |
| 확인 | 직접 (`forward-headers-strategy`·`internal-proxies`·`remoteip` 설정은 `app` 설정과 compose 어디에도 없음) |

- 지금처럼 앱이 직접 요청을 받으면 위조도 없고 정확하다. 그러나 이 앱은 TLS를 직접 처리하지 않아서 `COOKIE_SECURE=true` 운영에는 TLS 프록시가 필수다. 그러면 세션의 `ip`와 `audit_log`의 IP가 프록시 주소로 저장되어 기기 구분과 감사 추적이 의미를 잃는다.
- `AuthCookieSupport`의 주석은 `internal-proxies` 설정을 언급하지만, 설정을 강제하는 장치는 없다.
- **수정안**: Caddy 도입 때 한꺼번에 처리한다(계획 문서의 S8). `server.forward-headers-strategy: native`, `server.tomcat.remoteip.internal-proxies`를 Caddy 주소 하나로 좁히고, 8080을 호스트에 공개하지 않는다. 설정이 빠졌을 때를 잡는 테스트나 기동 검사를 함께 둔다.

---

## 낮음·정리

| # | 문제 | 위치 | 출처 | 확인 | 수정안 |
|---|---|---|---|---|---|
| 2 | 본문 마스킹이 2KB 캐시 한도와 "닫는 따옴표가 있는 문자열 값"에만 의존한다. 2048바이트에서 잘린 `newPassword`나 숫자 값(`"password":12345678`)은 평문으로 로그에 남는다 | `AccessLoggingFilter.kt:98`(`ContentCachingRequestWrapper(request, 2048)`), `:76-77` | 이전 패턴도 같았음. 포함 매칭 확장으로 범위가 넓어짐 | 직접 | 값 패턴을 따옴표 유무와 무관하게 `[^,}\s]*`까지 잡고, 잘린 본문은 마지막 민감 키 이후를 통째로 가린다 |
| 8 | `callProvider`의 `catch (BaseException)`가 `NoAuthenticationException`(401)·`NoAuthorizationException`(403)까지 500으로 바꾼다. 함수 자신의 기준("ExceptionAdvisor가 매기는 HTTP 상태")과 어긋난다. 지금은 제공자가 이 예외를 던지지 않아 잠복 상태 | `app/.../adapter/ProviderCalls.kt:39` | 전환 커밋 | catch 구조는 직접 / 두 예외가 `BaseException` 하위인 점은 보고 | 두 예외를 `StorageException`처럼 그대로 다시 던지는 분기를 추가하고 테스트를 고정한다 |
| 10 | 관리자 발급처(`aud=ADMIN`) 검사가 URL prefix `/api/v1/admin`에만 걸려 있다. 관리자 전용 `@Authorize` 핸들러가 prefix 밖에 생기면 일반 사이트 토큰(`aud=USER`)을 가진 관리자 계정이 호출할 수 있다 | `app/.../security/JwtAuthenticationFilter.kt:105` | 구조적 한계 (gateway도 같았음) | 직접 | `MemberPrincipal`에 audience를 담고 `AuthorizeHandlerInterceptor`가 관리자 전용 규칙에 `aud=ADMIN`을 요구하게 한다(경로와 무관하게 막힘) |
| 11 | 관리자 전용 핸들러 위치를 검사하는 아키텍처 테스트가 `Method.getAnnotation`/`Class.getAnnotation`만 본다. 인터셉터의 `HandlerMethod.getMethodAnnotation`은 메타·합성 어노테이션까지 찾는다. `permissions`만 지정한 규칙도 관리자 전용으로 보지 않는다 | `app/.../architecture/WebSurfaceArchitectureTest.kt:146-147`, `:150` | security-review 반영분 | 직접 | `AnnotatedElementUtils.findMergedAnnotation`으로 바꾸고 `MANAGE` 단독 규칙도 관리자 전용으로 취급한다. #10을 구조로 해결하면 이 테스트는 축소해도 된다 |
| 12 | 클라이언트 IP 추출이 두 곳(`AuthCookieSupport.extractClientIp`, `AuditLogService`의 `request?.remoteAddr`)에 중복돼 주석으로만 "같은 기준"이라고 연결돼 있다. 원래 버그가 `X-Forwarded-For` 로직의 복제였다 | `AuditLogService.kt:63-64`, `AuthCookieSupport.kt:81` | 전환 커밋 + security-review | 직접 | #7을 처리하면서 iam 공용 `ClientIpResolver` 하나로 모은다 |
| 13 | `buildPath`(→`maskQueryString`)가 START와 ACCESS에서 요청마다 두 번 계산된다. #1의 비용을 두 배로 만든다 | `AccessLoggingFilter.kt:106,123` | security-review 반영분 | 직접 | 체인 실행 전에 한 번 계산해 재사용한다. #1과 함께 고친다 |
| 15 | `docs/feature/stop.md`가 삭제된 `InternalStopController`를 "[x] S2S 내부 API"로 구현 완료라고 적고 있다(13행). 57행도 "Stop S2S로 기준 단가 조회"라고 적혀 있다 | `docs/feature/stop.md:13,57` | security-review가 이 문서를 고쳤지만 이 줄들은 남음 | 직접 | 포트·어댑터(`StopLookupPort`/`StopAdminPort`) 기준으로 문구를 바꾼다 |

---

## 제안 순서

1. **한 번에**: #1 + #13 (같은 파일, 같은 원인). 이어서 #3, #9, #14.
2. **결정 후**: #5(문서만 고칠지), #4(POST 전환), #6(호출 제한 + 상태 조건).
3. **같이 묶어서**: #8, #10, #11, #12, #2, #15.
4. **Caddy 도입 때**: #7(S8과 함께).

## 이 리뷰가 보지 못한 것

- 에이전트는 코드를 읽는 방식으로 리뷰했고, #1의 시간 측정을 빼면 어떤 항목도 테스트로 재현하지 않았다.
- 이번에도 `./gradlew clean build`와 실제 HTTP 검증(`modular-monolith-s2s-mapping.md`의 "검증 후 수정")은 security-review 반영분이 들어가기 전의 코드를 대상으로 했다. 작업 트리 변경분은 커밋 전에 다시 빌드와 `BusApplicationContextTest`로 확인해야 한다.
