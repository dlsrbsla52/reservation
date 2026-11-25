# 운영 환경 구축 작업 문서

> 권장 구성(Cloudflare Pages + R2 + AWS Lightsail 4GB)이 확정되면 바로 착수하기 위한 작업 목록.
> 구성안과 비용 근거: [mbus 홈페이지·예약 시스템 운영 구성안](https://claude.ai/artifact/6x8wsoArYGBttJDkKjdT2V) (2026-11-19 요금 기준, 월 35,739원)
> 대상 저장소: 화면 `mbus-front`(이 저장소), 백엔드 `reservation`(`~/IdeaProjects/reservation`)

## 1. 목표 구성

| 주소 | 대상 | 비고 |
| --- | --- | --- |
| `www.mbus.co.kr` | Cloudflare Pages (Next.js 정적 빌드 `out/`) | `mbus.co.kr`은 `www`로 리다이렉트 |
| `api.mbus.co.kr` | Cloudflare Tunnel → Lightsail `app:8080` | 서버 인바운드 80/443 미개방 |
| `img.mbus.co.kr` | Cloudflare R2 공개 버킷 | 갤러리·정류장 사진 |
| 예약 서버 | AWS Lightsail 4GB (서울, x86) | app + PostgreSQL + Valkey + cloudflared, Docker Compose |
| DB 백업 | S3 버킷 → 1일 후 Glacier → 90일 후 삭제 | 매일 `pg_dump` |

- 쿠키: `www`와 `api`는 같은 사이트(`mbus.co.kr`)라 refresh 쿠키(`SameSite=Lax`, `AuthCookieSupport`)가 그대로 동작함.
- `*.pages.dev` 주소는 `api.mbus.co.kr`과 다른 사이트(cross-site)로 판정되어 refresh 쿠키가 전송되지 않음. 이 주소로는 로그인 검증 불가.

## 2. 착수 전 결정 사항

- [ ] 서버 OS: Ubuntu 24.04 LTS(권장, Docker 패키지 설치가 단순함) 또는 Amazon Linux 2023
- [ ] 이미지 전달 방식: `docker save` + ssh 전송(권장, 레지스트리 불필요) 또는 GHCR·ECR
- [ ] 갤러리 이미지 테이블의 소유 모듈 (정류장 사진은 stop 모듈)
- [ ] 기존 mbus.co.kr 콘텐츠(회사소개·비즈니스·포트폴리오·고객지원·홍보센터)의 이관 범위와 이미지 수집 방법
- [ ] 관리자 도메인 분리 여부 (v1은 `www.mbus.co.kr/manager` 유지 권장)
- [ ] 외부 업타임 감시 도구 (무료 플랜의 상업적 이용 조건 확인)와 헬스 확인 경로 (actuator는 컨테이너 내부 8081 전용)
- [ ] 백업 보관 기간 90일 확정 (Glacier 최소 보관 기간 90일, 더 짧게 지워도 90일분 과금)

## 3. 단계별 작업

### 3.1 계정·도메인

- [ ] AWS 결제 카드 등록과 세금 설정(사업자등록번호) 입력
  - 카드 결제 시 원화로 청구되고, 부가세 공제 증빙은 카드 전표로 처리됨.
- [ ] AWS IAM 사용자 `mbus-backup` 생성: 백업 버킷의 `postgres/` 경로에 대한 `s3:PutObject`만 허용하고 액세스 키 발급
- [ ] Cloudflare 계정 생성 후 `mbus.co.kr` 추가
  - 가져온 DNS 레코드를 기존 DNS와 대조 (메일 MX·TXT, 기존 `www` A 레코드 유지)
- [ ] 도메인 등록업체에서 네임서버를 Cloudflare로 변경하고 전파 확인
  - 위험: 메일 레코드가 빠지면 메일 수신이 중단됨. 변경 전 기존 DNS 레코드 전체를 백업할 것.

### 3.2 백엔드 (`reservation` 저장소)

- [ ] Dockerfile x86 지원 (현재 ARM 전용)
  - JDK 다운로드를 `TARGETARCH`로 분기: arm64 → `linux-aarch64`, amd64 → `linux-x64`
  - 체크섬은 아키텍처별로 관리 (corretto-25 릴리스 페이지의 tar.gz SHA256)
  - `uname -m`으로 ARM을 검사하는 부분 제거
  - builder 단계에 `FROM --platform=$BUILDPLATFORM` 적용: 맥(arm64)에서 네이티브로 빌드하고(JAR는 아키텍처 무관), 런타임 단계만 대상 아키텍처로 만듦
  - 검증: `docker buildx build --platform linux/amd64 -t bus-app:<tag> --load .` 후 컨테이너 기동 확인
- [ ] `docker-compose.yml` 운영 조정
  - `platform: linux/arm64` → `linux/amd64`
  - app의 `ports`를 제거하고 `expose: 8080`으로 변경 (Tunnel만 접근)
  - `cloudflared` 서비스 추가: `cloudflare/cloudflared:<버전 고정>`, `tunnel --no-autoupdate run`, 토큰은 환경변수로 주입
  - 서버 기동은 `docker compose up -d --no-build` (서버에서 Gradle 빌드 금지)
  - app의 `mem_limit`과 `-Xmx`를 4GB 서버 기준으로 다시 정함 (시작값 예: `mem_limit: 1536m`, `-Xmx1g`, 부하 측정 후 확정)
- [x] `application.yml` 프록시 헤더 설정 (2026-11-25 반영)
  - `server.forward-headers-strategy: native`, `server.tomcat.remoteip.remote-ip-header: x-forwarded-for`, `server.tomcat.remoteip.internal-proxies: ${TRUSTED_PROXIES:<루프백 정규식>}`
  - 기본값은 루프백만 믿는다(헤더 위조 방지). **운영 `.env`의 `TRUSTED_PROXIES`를 cloudflared 컨테이너 IP 하나로 지정해야** 문의 호출 제한(`request.remoteAddr` 기준)이 방문자 IP별로 동작한다. 지정하지 않으면 모든 요청이 cloudflared IP 하나로 보여 5회/10분 제한을 전체가 나눠 쓴다. 사설 대역 전체를 넣지 않는다
  - cloudflared 컨테이너 IP는 compose 네트워크에 고정 IP를 주거나 `docker inspect`로 확인한다
  - 근거: `docs/infra/docker-compose-deployment.md`의 기존 권고
- [ ] 운영 환경변수(`.env`)
  - 운영값: `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGIN=https://www.mbus.co.kr`, `CORS_ADMIN_ALLOWED_ORIGIN`은 비워 두면 일반 origin과 같게 동작
  - 기존 값 유지: `JWT_SECRET`, `JWT_CLAIM_ENCRYPTION_KEY`, `POSTGRES_PASSWORD`, `SEOUL_API_KEY`
  - 신규: R2 접속값, Tunnel 토큰, `TRUSTED_PROXIES`(cloudflared 컨테이너 IP 하나)
- [x] 이미지 업로드 API (포트폴리오·공지사항, 2026-11-25 `content` 모듈로 구현. 설계: mbus `docs/feature/homepage-migration.md`)
  - 업로드 URL 발급: `POST /api/v1/admin/content/images/upload-url` → R2 presigned PUT URL (`common`의 AWS SDK v2 `S3Presigner`)
  - 관리: `/api/v1/admin/content/{portfolio,notice,faq,inquiry}` (iam admin 컨트롤러 → `ContentAdminPort` → app 어댑터)
  - 공개 조회: `GET /api/v1/content/{portfolio,notice,faq}` (+ `/detail?id=`), 문의 등록 `POST /api/v1/content/inquiry`
  - 업로드 제한: webp·jpeg·png, 10MB. 객체 키 `portfolio/{uuid}.webp`·`portfolio/{uuid}_thumb.webp`·`notice/{uuid}.webp`
  - 환경변수: `CONTENT_STORAGE_ENDPOINT`(`https://<계정ID>.r2.cloudflarestorage.com`), `CONTENT_STORAGE_REGION`(`auto`), `CONTENT_STORAGE_BUCKET`, `CONTENT_STORAGE_ACCESS_KEY`, `CONTENT_STORAGE_SECRET_KEY`, `CONTENT_STORAGE_PUBLIC_BASE_URL`(`https://img.mbus.co.kr`)
  - 로컬 확인은 `docker-compose-local.yml`의 RustFS(로컬 확인용, 운영 미배포)로 한다. local 프로필은 운영 `CONTENT_STORAGE_*`를 읽지 않고 `LOCAL_CONTENT_STORAGE_*`(기본값은 로컬 더미 값)만 읽는다
  - 트랜잭션 밖에서 외부 호출: 문의 호출 제한(Redis)과 이미지 업로드 확인(S3 HEAD)은 DB 트랜잭션 시작 전에 끝낸다. 타임아웃: Redis 500ms, S3 호출 5s(시도 2s)
  - 문의 관리: 조회(목록·상세)는 `READ`, 처리 상태 변경·삭제(`DELETE /api/v1/admin/content/inquiry/{id}`)는 `WRITE` 권한. 처리 완료(`DONE`) 후 `content.inquiry.retention`(기본 90일)이 지난 문의는 매일 03:30(KST)에 자동 삭제한다
- [ ] 정류장 사진 API (`/api/v1/admin/stop/{stopId}/images`, 공개 `GET /api/v1/stop/images?stopId=`): 아직 없음. 같은 업로드 방식을 stop 모듈에 붙인다
- [ ] DB 백업 스크립트 (서버 cron, 매일 1회)
  - `docker compose exec -T database pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc | aws s3 cp - "s3://<버킷>/postgres/$(date +%F).dump"`
  - 종료 코드로 실패를 감지해 알림을 보내고 실행 로그를 보관
  - Valkey는 백업 대상에서 제외 (refresh 토큰 등 휘발성 데이터라 유실 시 재로그인으로 해결)

### 3.3 서버 (Lightsail)

- [ ] 인스턴스 생성: 서울(ap-northeast-2), Linux 4GB 번들(2 vCPU, SSD 80GB, 월 전송량 4TB), OS는 2장 결정안
- [ ] 고정 IP 연결
- [ ] 방화벽: SSH(22)만 관리자 IP에 허용, 80/443은 열지 않음
- [ ] 기본 설정: 스왑 2GB, 시간대 Asia/Seoul, 자동 보안 업데이트
- [ ] 설치와 설정: Docker·Compose, 백업용 AWS CLI, `.env` 배치(권한 600)
- [ ] 이미지 전달
  - 맥에서 전송: `docker save bus-app:<tag> | gzip | ssh <서버> 'gunzip | docker load'`
  - 서버에서 기동: `APP_TAG=<tag> docker compose up -d --no-build`
- [ ] 부하 측정
  - 예약 조회·생성 시나리오로 부하 테스트를 돌리고 `docker stats`, `free -m`으로 메모리 확인
  - 기준: 피크 시 메모리 사용률 80% 이하, OOM 없음, 스왑 과다 사용 없음
- [ ] 감시: Lightsail 지표 알림(CPU·상태 확인), 외부 업타임 감시 등록

### 3.4 화면 (`mbus-front` 저장소)

- [ ] `next.config.ts`에 `output: 'export'`, `images: { unoptimized: true }` 설정
- [ ] `proxy.ts` 삭제 (정적 배포에서 미지원)
  - `lib/auth/site.ts`의 관리자 호스트 관련 주석 정리. `NEXT_PUBLIC_ADMIN_HOST`는 계속 미사용
- [ ] 동적 라우트 3개를 쿼리 방식으로 전환 (`useSearchParams` + `Suspense`)
  - `app/(protected)/contract/[id]/page.tsx` → `/contract/detail?id=`
  - `app/(protected)/contract/[id]/renew/page.tsx` → `/contract/renew?id=`
  - `app/(manager)/manager/stop/[id]/page.tsx` → `/manager/stop/detail?id=`
  - 링크 수정: `contract/page.tsx`(40행), `contract/[id]/page.tsx`(74행), `contract/[id]/renew/page.tsx`(24·35·58행), `/manager/stop/` 상세를 참조하는 곳 검색
- [ ] Sentry: `instrumentation.ts`, `sentry.server.config.ts`, `sentry.edge.config.ts`는 정적 배포에서 쓰이지 않음. 빌드 결과를 확인한 뒤 정리
- [ ] Pages 빌드 환경변수
  - `NEXT_PUBLIC_API_BASE_URL=https://api.mbus.co.kr`
  - `NEXT_PUBLIC_KAKAO_MAP_APP_KEY`: 카카오 개발자 콘솔에 `https://www.mbus.co.kr` 도메인 등록 필요
  - `NEXT_PUBLIC_SENTRY_DSN`: 선택
- [ ] 관리자 사진 관리 화면
  - 정류장 상세의 사진 영역, 갤러리 관리 화면(`/manager/gallery`), 관리자 메뉴(`constants/navigation.ts`) 추가
  - 업로드 흐름: 업로드 URL 발급 → 브라우저에서 R2로 직접 PUT → 메타데이터 등록
  - 업로드 전 브라우저에서 WebP로 변환하고 리사이즈 (원본 1장, 썸네일 1장)
- [ ] 공개 화면에서 갤러리·정류장 사진 표시 (`img.mbus.co.kr` URL)
- [ ] 검증: `npm run lint`, `npx tsc --noEmit --incremental false`, `npm run build` (`out/` 생성 확인)

### 3.5 Cloudflare

- [ ] Pages 프로젝트 생성
  - GitHub `mbus-front` 저장소 연결
  - 빌드 명령 `npm run build`, 출력 디렉터리 `out`, `NODE_VERSION=22`
  - 3.4의 환경변수 등록
- [ ] Pages 사용자 지정 도메인 `www.mbus.co.kr` 연결 (3.7 전환 단계에서), `mbus.co.kr` → `www` 리다이렉트 규칙 추가
- [ ] R2 설정
  - 버킷 생성 후 사용자 지정 도메인 `img.mbus.co.kr` 연결
  - CORS: 허용 origin `https://www.mbus.co.kr`(관리자 사이트를 별도 호스트로 쓰면 그 origin도 함께), 메서드 PUT·GET, `AllowedHeaders: content-type`(presigned PUT은 `Content-Type`을 서명하므로 브라우저가 보내는 이 헤더를 허용해야 한다)
  - 버킷 한정 API 토큰(Object Read & Write) 발급
- [ ] Tunnel 생성: 공개 호스트 `api.mbus.co.kr` → `http://app:8080`, 발급된 토큰을 서버 `.env`에 등록

### 3.6 S3 백업 버킷

- [ ] 버킷 생성: 서울 리전, 퍼블릭 액세스 차단, 기본 암호화
- [ ] 수명 주기 규칙 (prefix `postgres/`)
  - 1일 후 Glacier Flexible Retrieval로 전환
  - 90일 후 만료 삭제
  - 미완료 멀티파트 업로드는 1일 후 삭제
- [ ] 복원 절차 문서화와 월 1회 복원 시험
  - 최신본(S3 Standard)은 즉시 복원
  - 이전본은 Glacier 복원을 요청해 수 시간 뒤 다운로드한 다음 `pg_restore`

### 3.7 전환

- [ ] 사전 검증
  - 스테이징 도메인(예: `beta.mbus.co.kr`)을 Pages에 연결하고 `CORS_ALLOWED_ORIGIN`을 임시로 변경
  - 로그인, 예약, 관리자 화면, 사진 업로드 동작 확인
- [ ] 기존 사이트 백업 (페이지, 이미지, DNS 레코드)
- [ ] DNS TTL을 미리 단축하고 `www.mbus.co.kr`을 Pages로 전환, `CORS_ALLOWED_ORIGIN` 운영값 복원
- [ ] 전환 후 확인: 로그인과 토큰 갱신(쿠키), 예약 생성, 관리자 로그인(`/manager`), 사진 업로드·노출, 카카오 지도
- [ ] 롤백 절차: 문제 발생 시 `www` 레코드를 기존 사이트로 복원

## 4. 월 비용 (부가세 포함, 2026-11-19 기준)

| 항목 | 월 비용 |
| --- | --- |
| Lightsail 4GB (저장공간 80GB·고정 IP·전송량 4TB 포함) | 35,078원 |
| DB 백업 (S3 → Glacier, 백업 1회 1GB 가정) | 661원 |
| Cloudflare Pages·R2·Tunnel | 0원 (무료 범위) |
| **합계** | **35,739원** |

## 5. 참고 문서

- 구성안: [mbus 홈페이지·예약 시스템 운영 구성안](https://claude.ai/artifact/6x8wsoArYGBttJDkKjdT2V)
- 백엔드 단일 서버 배포 기준: `reservation/docs/infra/docker-compose-deployment.md`
- API 명세: `docs/api-contract.md`
