# AGENTS.md — content

`modules/content`는 홈페이지 콘텐츠(포트폴리오·공지사항·FAQ·온라인 문의)와 그 이미지 저장을 맡는 도메인 라이브러리 모듈이다. 실행은 `modules/app`이 한다.
공개 화면은 이 모듈의 컨트롤러(`/api/v1/content/**`)를 직접 부르고, 관리자 화면은 iam admin API(`/api/v1/admin/content/**`) → `ContentAdminPort` → app `IamContentAdminAdapter`를 거쳐 이 모듈의 Service를 부른다.

Base package: `com.media.bus.content`

## Build

```bash
./gradlew :modules:content:test
./gradlew :modules:content:busLint
./gradlew :modules:app:bootRun --args='--spring.profiles.active=local'   # 실행은 app 모듈
```

## Database

- **Schema:** `content` (`db/changelog/content/`, `logicalFilePath`는 `db/changelog/content/<파일명>.xml`)
- 테이블: `portfolio`, `notice`, `notice_image`(FK `notice_id` → `notice`, ON DELETE CASCADE), `faq`, `inquiry`
- Exposed 테이블에도 외래키를 `reference(...)`로 선언한다. 선언하지 않으면 Exposed가 INSERT 순서를 몰라 자식 행을 먼저 넣어 FK 오류가 난다(테스트 H2에도 FK가 생겨야 이 문제를 잡는다)

## 구성

| 위치 | 역할 |
|---|---|
| `controller/Content*Controller` | 공개 API. 조회 5개와 문의 등록 1개가 `ApiPathPolicy.PUBLIC_ENDPOINTS`에 있다. 경로 변수 대신 `/detail?id=` 쿼리를 쓴다(공개 경로는 정확 일치) |
| `service/*Service` | 공개용(`getPublic*`, `readPublic`)과 관리자용 메서드. 진입점에서 `InputValidator.validate`를 호출한다 |
| `service/PortfolioWriter`·`NoticeWriter` | 포트폴리오·공지 쓰기 **트랜잭션 경계**. Service가 외부 호출(S3 HEAD)을 끝낸 뒤 부른다 |
| `scheduler/InquiryPurgeScheduler` | 처리 완료 문의의 보관 기간 파기(매일 03:30 KST) |
| `service/ContentImageService` | 업로드 URL 발급(용도·형식·크기 검증, 키 생성)과 등록 전 키 형식·업로드 여부 확인 |
| `storage/ContentStorage` | S3 호환 저장소(운영 R2) presigned PUT, HEAD, 커밋 후 삭제. 클라이언트는 처음 쓸 때 만든다(설정이 없어도 기동한다) |
| `guard/InquiryRateLimiter` | 공개 문의 IP별 호출 제한(Redis, 기본 10분 5회) |
| `result/ContentResult` | 결과 코드 `W` 접두사 |

## 규칙

- **이미지 키는 서버가 만든다.** 등록·수정 요청의 키는 `ContentImageService.keyPattern`(용도 접두사 + UUID)과 맞아야 하고, HEAD로 실제 업로드를 확인한다. 클라이언트가 임의 키를 넣지 못하게 한다
- **저장소 삭제는 커밋 뒤에 한다.** 교체·삭제된 이미지는 `ContentStorage.deleteAfterCommit`으로 넘긴다. 커밋 뒤 가상 스레드로 넘겨 DB 커넥션을 붙잡지 않고, 롤백이면 지우지 않는다. 실패(예외, `DeleteObjects` 응답의 `errors()`)는 로그만 남긴다(고아 객체는 허용, 깨진 이미지는 막는다)
- **교체 판정은 키 집합 차로 한다.** 포트폴리오 수정은 `{이전 원본, 이전 썸네일} - {새 원본, 새 썸네일}`만 삭제 대상이고, 새로 확인할 키는 `{새 키} - {이전 키}`다. 원본·썸네일을 맞바꾸거나 한 키를 두 칸에 써도 쓰는 객체를 지우지 않는다
- **이미지 키 소유권:** 다른 행(포트폴리오 원본·썸네일, 공지 이미지)이 이미 쓰는 키를 등록·수정에 쓰면 409(`W0019`)로 거절한다(`ContentImageRepository.isInUse`). 한 객체를 두 행이 공유하면 한쪽 삭제가 다른 쪽 이미지를 깨기 때문이다
- **트랜잭션 안에서 외부 호출을 하지 않는다.** 전역 벌크헤드(`@Transactional`·`@Repository` 감쌈, 퍼밋 19개)를 오래 점유하기 때문이다. 문의 호출 제한(Redis)과 이미지 업로드 확인(S3 HEAD)은 트랜잭션 시작 전에 끝내고, Service(트랜잭션 없음) → Writer·Repository(`@Transactional`)로 저장한다. 같은 클래스 안 호출은 프록시를 타지 않으므로 Writer를 따로 둔다
- **같은 `legacyIdx` 동시 등록:** 사전 조회 뒤 저장이 unique 위반으로 실패하면 먼저 저장된 항목을 다시 조회해 돌려준다(500이 되지 않는다)
- **문의 권한:** 목록·상세는 `READ`, 상태 변경·삭제는 `WRITE`(iam `AdminInquiryController`)
- **문의 처리 메모 의미:** 상태 변경 요청의 `memo`가 null이면 기존 메모 유지, 빈 문자열이면 지움, 그 외는 trim해 저장한다. "상담 완료"는 `PUT .../{id}/status`의 `DONE`이다
- **문의 삭제·파기:** `DELETE /api/v1/admin/content/inquiry/{id}`(WRITE)는 `ContentAdminPort.deleteInquiry` → `InquiryService.delete`. `DONE` 후 `content.inquiry.retention`(기본 90d)이 지난 문의는 `InquiryPurgeScheduler`가 한 번의 DELETE로 지우고 건수만 로그에 남긴다(개인정보 값은 남기지 않는다). `content.inquiry.purge-enabled=false`로 끈다(`application-openapi.yml`, 앱 컨텍스트 테스트)
- **프록시 뒤 클라이언트 IP:** 호출 제한이 `request.remoteAddr`를 쓴다. 운영(Cloudflare Tunnel)에서는 `TRUSTED_PROXIES`를 cloudflared IP 하나로 지정해야 방문자별로 제한된다(기본값은 루프백만 믿는다)
- **접근 로그:** `common`의 `AccessLoggingFilter`는 공개 문의·관리자 문의 경로의 요청·응답 본문을 기록하지 않는다
- **공개 응답에는 객체 키 대신 공개 URL**(`public-base-url` + 키)을 준다. DB에는 키만 저장한다
- **이관 멱등성:** `legacyIdx`가 같은 포트폴리오·공지는 새로 만들지 않고 기존 항목을 돌려준다(이관 스크립트 재실행 대비)
- **요청 DTO의 생략 가능한 Boolean은 nullable로 둔다.** 이 프로젝트의 Jackson 설정은 Kotlin 기본값을 역직렬화에 쓰지 않아, `Boolean = true` 같은 필드를 JSON에서 빼면 400(본문 해석 불가)이 된다. 기본값은 어댑터·서비스에서 채운다
- 정렬: 모든 목록의 마지막 기준은 `id DESC`다(같은 값일 때 순서 고정). 포트폴리오는 `sort_order` 큰 값이 먼저(새 항목이 맨 앞), FAQ는 작은 값이 먼저(새 항목이 맨 뒤). 순서 변경은 전체 ID 목록을 받아야 한다

## Configuration

```yaml
# modules/app/src/main/resources/application.yml (값은 루트 .env 의 CONTENT_STORAGE_*)
content.storage: endpoint, presign-endpoint, region, bucket, access-key, secret-key, public-base-url, path-style, upload-expiry(10m), max-upload-bytes(10MB)
content.storage 타임아웃: api-call-timeout(5s), api-call-attempt-timeout(2s). spring.data.redis.timeout·connect-timeout 500ms
content.inquiry: rate-limit(5), rate-window(10m), retention(90d), purge-enabled(true)
```

- 운영: Cloudflare R2. 버킷 한정 API 토큰, region `auto`, 공개 도메인 `img.mbus.co.kr`, 버킷 CORS에 사이트 origin의 PUT 허용
- **로컬 확인용: RustFS**(`docker-compose-local.yml`의 `rustfs`·`rustfs-init`). 운영에는 배포되지 않는다. `application-local.yml`이 `localhost:9000`, path-style, 로컬 전용 더미 키를 쓴다. 운영 `CONTENT_STORAGE_*`는 읽지 않고 `LOCAL_CONTENT_STORAGE_*`만 읽는다(운영 R2 오접속 방지)
- `presign-endpoint`: 서버가 접속하는 주소와 브라우저가 업로드할 주소가 다를 때(로컬 compose의 app 컨테이너: `rustfs:9000` / `localhost:9000`)만 지정한다

## 테스트

| 테스트 | 고정하는 것 |
|---|---|
| `*ServiceTest` (H2 + MockK 저장소) | 공개/관리자 목록 노출 규칙, 정렬(동률 `id DESC`), 검색, 이관 멱등성(동시 등록 충돌 포함), 이미지 검증·교체(맞바꾸기·같은 키)·삭제 대상·키 소유권, 조회수, 문의 동의·호출 제한·처리·메모 의미·삭제·파기 |
| `ContentImageServiceTest` | 키 형식, 형식·용량·용도 검증 |
| `ContentStorageTest` | presigned URL 서명 헤더(`content-type`·`content-length`), 커밋 후에만 삭제·롤백이면 삭제 안 함 |
| `InquiryPurgeSchedulerTest` | 보관 기간 기준 시각, 예외 격리 |
| `InquiryRateLimiterTest` | IP 해시 키, 429, Redis 장애 변환 |
| app `IamContentAdminAdapterTest` | DTO 필드 변환, 생략 Boolean 기본값, 4xx 전달 |
