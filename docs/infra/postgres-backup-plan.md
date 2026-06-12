# PostgreSQL 백업 계획

이 문서는 단일 EC2 Docker Compose 운영 기준에서 PostgreSQL 백업을 어떻게 가져갈지 정리한다. 아직 AWS 계정과 S3 버킷이 준비되지 않았으므로, 현재 단계에서는 구현하지 않고 계획만 확정한다.

## 현재 단계

초기 운영은 Docker named volume인 `postgres_data`에 PostgreSQL 데이터를 저장한다.

```text
database 컨테이너
  -> postgres_data Docker volume
```

`docker compose down`만 실행하면 volume은 유지된다. 데이터를 지우는 명령인 `docker compose down -v`는 운영 서버에서 사용하지 않는다.

## 목표 구조

AWS 계정, S3 버킷, EC2 IAM Role이 준비되면 백업 전용 컨테이너를 추가한다.

```text
postgres-backup 컨테이너
  -> database:5432 접속
  -> pg_dump --format=custom 실행
  -> S3 업로드
```

백업 파일 예시:

```text
s3://<bucket-name>/postgres/daily/default_2026-06-12_030000.dump
```

## 권장 방식

초기에는 물리 백업이나 WAL 아카이빙보다 `pg_dump --format=custom` 기반 논리 백업이 적합하다.

이유:

- DB 규모가 작을 때 운영과 복구가 단순하다.
- Docker volume 전체 스냅샷보다 데이터베이스 단위 복구가 명확하다.
- `pg_restore`로 특정 환경에 복원 테스트하기 쉽다.
- 나중에 RDS/Aurora로 이전할 때도 dump 파일을 재사용하기 쉽다.

## 예정 작업

AWS 준비 후 다음 작업을 진행한다.

1. S3 버킷 생성
2. EC2 IAM Role에 S3 업로드 권한 부여
3. 백업 전용 Dockerfile과 스크립트 추가
4. `docker-compose.yml`에 `postgres-backup` 서비스 추가
5. 하루 1회 백업 스케줄 설정
6. S3 Lifecycle 정책 설정
7. 복구 테스트 절차 문서화

## 운영 정책 초안

| 항목 | 기준 |
|------|------|
| 백업 주기 | 하루 1회 |
| 백업 형식 | `pg_dump --format=custom` |
| 저장 위치 | S3 |
| daily 보관 | 30일 |
| monthly 보관 | 12개월 |
| 암호화 | S3 기본 암호화 또는 SSE-KMS |
| 인증 | AWS Access Key 대신 EC2 IAM Role 사용 |
| 복구 테스트 | 월 1회 또는 주요 배포 전 |

## 백업 스크립트 방향

향후 추가할 스크립트는 다음 흐름을 따른다.

```bash
pg_dump \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --username="$DB_USERNAME" \
  --dbname="$DB_NAME" \
  --format=custom \
  --file="/backups/${DB_NAME}_${TIMESTAMP}.dump"

aws s3 cp \
  "/backups/${DB_NAME}_${TIMESTAMP}.dump" \
  "s3://${S3_BACKUP_BUCKET}/postgres/daily/${DB_NAME}_${TIMESTAMP}.dump"
```

실제 구현 시에는 다음을 함께 처리한다.

- `PGPASSWORD` 주입
- 실패 시 컨테이너 로그에 명확한 오류 출력
- 업로드 성공 후 로컬 임시 백업 파일 삭제
- `pg_restore --list` 또는 별도 테스트 DB 복원으로 백업 파일 유효성 검증

## 복구 테스트 기준

백업은 업로드보다 복구 가능성이 더 중요하다. S3 연동 후에는 최소 월 1회 다음 절차를 실행한다.

```text
1. S3에서 최신 백업 파일 다운로드
2. 임시 PostgreSQL 컨테이너 실행
3. pg_restore로 복원
4. 주요 테이블 row count 확인
5. 애플리케이션이 임시 DB에 연결 가능한지 확인
```

복구 테스트가 없는 백업은 운영 안전장치로 보지 않는다.
