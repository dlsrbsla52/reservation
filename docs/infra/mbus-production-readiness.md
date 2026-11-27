# mbus 운영 배포 준비

첨부된 「mbus 홈페이지·예약 시스템 운영 구성안」(12쪽, 요금 기준 2026-11-19)과 백엔드 `reservation`, 프런트엔드 `mbus`의 현재 체크아웃을 대조했다. 두 프로젝트의 공통 체크리스트는 이 문서에서 관리한다. 체크 항목은 운영 환경에서 완료 증빙이 필요한 작업이다.

## 구성 결정

| 항목 | A안 · 시범 운영 | B안 · 정식 운영 권장 |
| --- | --- | --- |
| 예약 서버 | Lightsail 서울 2GB, PostgreSQL·Valkey·앱을 한 호스트에서 운영 | Lightsail 서울 4GB, 동일 구성 |
| DB 백업 | S3 매일 백업, 2일 보관 | S3 매일 백업, 최근본 즉시 복구 및 장기 이력 보관 |
| PDF 월 추정액 | 17,612원 | 35,739원 |

두 안 모두 Next.js를 유지한다. `mbus.co.kr`은 `www.mbus.co.kr`로 이동시키고, `www`는 Cloudflare Pages, `api`는 Cloudflare Tunnel을 거쳐 Lightsail의 `app:8080`, `img`는 Cloudflare R2 공개 이미지 버킷에 연결한다. 단일 호스트 장애 시 예약과 로그인에 영향이 있으며, 4GB 선택은 메모리 여유를 늘린다.

- [ ] 운영 책임자: B안 또는 A안, 월 예산, 데이터 보관 기간, 허용 데이터 손실 시간(RPO), 복구 목표 시간(RTO)을 승인한다. PDF 금액은 백업 1회 1GB와 당시 환율을 가정한 추정치다. 결제 직전에 서울 번들·IPv4·스냅샷, S3 요청·복원·암호화, R2 저장량·요청량, 도메인·메일·감시 비용을 포함해 다시 산정한다. [Lightsail 요금](https://aws.amazon.com/lightsail/pricing/) · [S3 요금](https://aws.amazon.com/s3/pricing/) · [R2 요금](https://developers.cloudflare.com/r2/pricing/)
- [ ] 장애 목표가 단일 호스트와 일일 백업으로 충족되는지 판단한다. 더 짧은 RPO나 관리형 장애 대응이 필요하면 EC2/ECS와 프라이빗 Aurora PostgreSQL 조합을 별도 견적한다. 이 경로에서는 PostgreSQL 버전·확장 호환성, Liquibase 검증, 자동 백업·시점 복구 시험, Secrets Manager와 런타임 IAM 역할을 함께 설계한다.

## 회사에서 먼저 준비할 계정과 권한

| 준비 대상 | 담당자와 준비물 | 완료 기준 |
| --- | --- | --- |
| AWS 운영 계정 | 회사 관리 이메일, 복구 연락처, 결제수단, 청구 담당자. 기존 AWS Organizations가 있으면 운영 멤버 계정 사용 여부 결정 | 루트 MFA 및 복구 수단 확인, 일상 작업은 개인별 IAM Identity Center 또는 연동 권한으로 수행. 운영·청구 역할 분리, 예산 알림 수신 확인. [AWS 루트 계정 지침](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html) |
| Cloudflare | 회사 소유 계정, 운영 담당자 초대, MFA, 결제 담당자 및 R2 활성화 권한 | DNS·Pages·Tunnel·R2 담당 권한 확인. 배포와 R2에 별도 토큰 사용, 복구 담당자 확보 |
| 도메인 등록업체와 기존 DNS | 등록계정, 갱신·결제 연락처, 네임서버 변경 권한, 현재 DNS 내보내기 | 웹/DNS/메일 운영자가 동일한 레코드 목록과 전환 일정을 확인 |
| Git 호스팅과 배포 | 두 저장소의 조직·원격 호스트·운영 브랜치, CI 실행 권한, 배포 승인자, 서버 SSH 개인 계정 | 운영 브랜치 보호, 담당자별 접근, 배포 및 롤백 권한 검증. Pages Git 연결 또는 CI Direct Upload 선택. [Pages Git 연동](https://developers.cloudflare.com/pages/configuration/git-integration/) |
| 서비스 운영 계정 | 최초 `ADMIN_MASTER` 담당자, 예비 관리자, 콘텐츠·예약 담당자, 테스트용 일반 회원 | 첫 관리자 생성 절차 검증 후 개인별 계정과 역할 부여. 관리자 로그인 및 일반 회원의 관리자 API 접근 차단 확인 |
| 외부 서비스 | 회사 소유 카카오 Developers 앱, 서울 열린데이터광장 키(정류소 일괄 등록 사용 시), 인증 메일 발송 계정, 장애 수신 채널. Sentry 사용 시 회사 프로젝트 | 지도에 운영 도메인 등록, 메일 발신 도메인 인증, 외부 알림 수신 시험 |

운영 리소스 인벤토리에는 AWS 계정 ID·리전·버킷 이름, Cloudflare 계정·Zone·Pages 프로젝트·Tunnel·R2 버킷 식별자, 담당자와 교체 절차를 기록한다. 실제 키와 토큰은 서버 프로젝트 루트의 접근 제한된 `.env` 등 승인된 비밀 저장 경로로 전달한다. 운영 비밀은 Git, 이미지, 브라우저 빌드 변수, CI 로그에 넣지 않는다.

## AWS S3와 Cloudflare R2

### AWS S3 · PostgreSQL 백업

- [ ] 서울 리전에 비공개 백업 버킷을 만들고 퍼블릭 액세스 차단, 기본 암호화, TLS 전송 및 `postgres/` 범위 정책을 설정한다. 백업 작업에는 지정 경로 쓰기 권한을, 별도 복원 역할에는 필요한 조회·다운로드·아카이브 복원 권한을 부여한다. SSE-KMS 선택 시 양쪽 역할의 KMS 권한도 검증한다. EC2 방식은 인스턴스 역할을 사용한다. Lightsail 방식은 EC2 인스턴스 역할을 그대로 쓸 수 있다고 가정하지 말고 인증 경로를 먼저 검증한다. 전용 IAM 액세스 키를 쓰는 경우 최소 권한, 저장 위치, 교체·폐기 담당자를 기록한다. [Lightsail IAM](https://docs.aws.amazon.com/lightsail/latest/userguide/security_iam.html)
- [ ] B안 기준으로 최근 2일은 S3 Standard, 이후 Glacier Flexible Retrieval 전환을 검토한다. Glacier 최소 과금 기간은 전환 후 90일이므로 생성 후 90일에 만료하면 조기 삭제 요금이 발생할 수 있다. 총 90일 만료를 유지할지, 생성 후 92일 이상으로 늘릴지 결정 후 비용을 갱신한다. 작은 객체 전환 조건, 미완료 멀티파트 정리, 버전 관리를 켤 경우 이전 버전 만료도 검증한다. 기존 백엔드 문서의 일별 30일·월별 12개월 정책을 쓸 경우 별도로 승인한다. [S3 수명 주기 전환 제약](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html)
- [ ] 매일 PostgreSQL custom dump를 만들고 덤프 성공과 파일 유효성을 검사한 뒤 타임스탬프가 포함된 키에 업로드한다. 업로드 실패와 예정 시각까지 성공 기록이 없는 경우를 모두 알린다. 운영 DB에 덮어쓰기 없이 별도 DB로 최근본과 아카이브본을 복원해 예약·계약·회원 데이터와 소요 시간을 확인한다. 월 1회 복원 시험 담당자를 지정한다. Valkey 유실 시 세션·인증 토큰 영향을 확인하고 재로그인 절차를 준비한다. [기존 PostgreSQL 백업 계획](postgres-backup-plan.md)

### Cloudflare R2 · 공개 홈페이지 이미지

- [ ] 운영 이미지 버킷과 `img.mbus.co.kr`을 같은 Cloudflare 계정에서 연결하고 `r2.dev` 공개 주소는 비활성화한다. 포트폴리오·공지 이미지 등 공개 승인된 파일만 넣는다. 백업, 계약 증빙, 문의 개인정보는 비공개 저장이 필요하다. [R2 공개 버킷](https://developers.cloudflare.com/r2/buckets/public-buckets/)
- [ ] 버킷 한정 Object Read & Write 자격 증명을 발급한다. 백엔드에 `CONTENT_STORAGE_ENDPOINT`, `CONTENT_STORAGE_REGION=auto`, `CONTENT_STORAGE_BUCKET`, `CONTENT_STORAGE_ACCESS_KEY`, `CONTENT_STORAGE_SECRET_KEY`, `CONTENT_STORAGE_PUBLIC_BASE_URL=https://img.mbus.co.kr`을 설정한다. 버킷 CORS는 승인한 화면 origin, 필요한 `PUT`·`GET`, 서명에 포함된 `Content-Type`으로 시험한다. 브라우저의 서명 URL PUT, API 메타데이터 등록, 공개 URL GET을 한 흐름으로 검증한다. DB 복원 시 이미지 참조도 살아 있도록 R2 보존·복구 책임을 정한다. [R2 CORS](https://developers.cloudflare.com/r2/buckets/cors/)

## 출시 전에 닫아야 할 작업

| 대상 | 현재 확인 내용 | 출시 전 완료 기준 |
| --- | --- | --- |
| `mbus` 정적 빌드 | `next.config.ts`에 export 설정이 없고, `proxy.ts` 및 계약 상세·계약 갱신·관리자 정류소 상세에 런타임 ID 경로가 있다 | `output: 'export'` 적용, 정적 배포에 맞게 proxy 제거 및 세 ID 경로·링크를 쿼리 기반 등으로 조정. `npm run check`, `npm run build`, `out/` 배포 뒤 직접 진입·새로고침 검증. 정적 변환이 어려우면 Cloudflare Workers/OpenNext 호환성·비용을 검토. [Pages 정적 Next.js](https://developers.cloudflare.com/pages/framework-guides/nextjs/deploy-a-static-nextjs-site/) · [Workers Next.js](https://developers.cloudflare.com/workers/framework-guides/web-apps/nextjs/) |
| `reservation` 이미지·네트워크 | Dockerfile 및 운영 Compose가 ARM64 기준이다. Compose는 앱 호스트 포트를 게시하고 Tunnel 서비스와 정기 백업이 없다 | `linux/amd64` 이미지 빌드·기동, 불변 커밋 태그 사용, 운영 Compose에 Tunnel 추가 및 앱 호스트 포트 게시 제거. DB·Valkey·Actuator 내부 유지. `TRUSTED_PROXIES`를 지정된 Tunnel 주소로 좁혀 문의 요청 제한이 방문자별로 적용되는지 검증 |
| 운영 설정 | Compose는 `JWT_CLAIM_ENCRYPTION_KEY`를 필수로 요구하지만 백엔드 `.env.example`에 항목이 없다 | 키 생성·복구·교체 절차와 템플릿 보완. `JWT_SECRET`, `POSTGRES_PASSWORD`, `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGIN=https://www.mbus.co.kr` 등 정상 주입 확인. 초기 운영은 동일 호스트의 `/manager`를 기준으로 한다 |
| 최초 관리자·메일 | 관리자 생성 API에는 기존 `ADMIN_MASTER`의 `MANAGE`가 필요하다. 확인한 회원가입·비밀번호 재설정 코드는 토큰을 개발 로그에 남기며 메일 발송 연결이 확인되지 않았다 | 빈 DB의 최초 관리자 발급을 위한 통제된 일회성 절차를 작성하고 감사 기록을 남긴다. 공개 가입·재설정을 출시 범위에 포함하면 SES 등 발송 서비스 연결, 발신 도메인 인증, 운영 링크, 만료·재발송, 실제 수신을 검증한다 |
| 백엔드 배포 자동화 | 두 체크아웃에서 GitLab CI 설정 파일을 확인하지 못했다 | 실제 원격 호스트와 배포 권한 확정. 백엔드는 `./gradlew clean build` 및 `./gradlew :modules:app:bootJar` 검증 후 CI에서 Docker 이미지 빌드·전송. GitLab과 AWS 레지스트리를 선택하면 OIDC 단기 권한을 검증한다. DB 변경 전 복원 가능한 백업과 이전 이미지 확보. [GitLab AWS OIDC](https://docs.gitlab.com/ci/cloud_services/aws/) |
| 콘텐츠·기존 데이터 | 콘텐츠 이미지 저장소 설정은 존재하고, 프런트에는 레거시 콘텐츠 수집·등록 스크립트가 있다. 기존 호스팅 계획에는 정류장 사진 API가 미완료로 기록돼 있다 | 콘텐츠 및 기존 회원·예약·계약 이관 범위, 정류장 사진의 출시 포함 여부를 담당자와 확정. 별도 환경에서 이관 결과와 수량을 대조하고 운영 이관 후 공개 콘텐츠·관리자 업로드를 확인 |

## 전환 순서와 운영 인수

1. **계정과 정책 확정.** 담당자, 예산, 복구 목표, 데이터 이관·보관 범위, 오픈 및 롤백 판단권자를 지정한다.
2. **사전 환경 구성.** 운영과 분리한 DB·R2와 동일 상위 도메인의 테스트 화면/API로 검증한다. 화면 빌드에 `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_KAKAO_MAP_APP_KEY`, 필요 시 Sentry 값을 설정한다. `pages.dev`에서 운영 API를 호출하면 사이트가 달라 Refresh 쿠키 검증이 달라지므로 실제 사용자 도메인에서도 확인한다.
3. **두 서비스와 복구 시험.** 회원·관리자 로그인 및 새로고침 후 갱신·로그아웃, 예약 생성·조회·권한, 계약 상세·갱신, 지도, 문의 제한, 이미지 업로드 및 노출, 인증 메일을 확인한다. 운영 부하에서 메모리·디스크·DB 연결과 오류율을 측정한다. 외부에서는 비민감 공개 경로로 업타임을 확인하고 내부 Actuator, Tunnel 연결, 백업 지연, CPU·메모리·디스크 알림을 담당자가 수신하는지 시험한다.
4. **DNS 전환.** 기존 사이트와 DNS를 백업하고 MX·SPF·DKIM·DMARC·기타 도메인 검증 레코드와 DNSSEC/DS 상태를 대조한다. 가능하면 TTL을 미리 낮춘다. Cloudflare 네임서버 전환과 기존 웹 레코드 유지 확인 후 `www`를 Pages로 연결하고, apex 리다이렉트와 `api`·`img`의 TLS를 확인한다. 기존 메일 송수신도 재시험한다.
5. **오픈 및 롤백.** 이전 Pages 배포·백엔드 이미지·DNS 레코드를 보존하고 원복 조건과 권한을 기록한다. Liquibase 적용 후에는 이전 앱의 스키마 호환성을 확인한 뒤 이미지 롤백을 판단한다. 신규 예약이 들어온 뒤 DB 복원을 하면 이후 쓰기가 유실될 수 있으므로 쓰기 동결 및 데이터 대사 계획을 적용한다. 개인정보 접근·로그 마스킹·문의 보존과 삭제·장애 대응·비용 점검 담당자를 인수인계한다.

백엔드의 세부 절차는 [Docker Compose 운영 배포](docker-compose-deployment.md), [이미지 배포 계획](container-image-deployment-plan.md), [로그 보관 계획](log-retention-and-backup-plan.md)을 참고한다. 프런트엔드의 기존 작업안은 `mbus/docs/feature/hosting-setup.md`에 있다.
