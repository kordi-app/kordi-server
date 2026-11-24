# 복구(Restore) 절차와 복구 훈련

백업 파일은 `backup.sh`가 만든 `backups/kordi-<label>-<UTC 시각>.sql.gz` 입니다.

- `nightly` — cron 야간 백업 (`BACKUP_S3_URI`가 있으면 S3에도 복사)
- `predeploy` — `deploy.sh`가 배포 직전에 만든 덤프
- `prerestore` — `restore.sh`가 복구 직전에 만든 현재 상태 덤프 (복구를 되돌릴 때 사용)

덤프는 `--single-transaction --add-drop-database --databases <DB>`로 만들어집니다.
**복구하면 데이터베이스 전체가 DROP 후 다시 만들어집니다.** 덤프 이후에 쌓인 데이터는 사라집니다.
DB 단위 권한(앱 계정의 `GRANT ... ON <DB>.*`)은 `mysql` 시스템 DB에 있으므로 DROP 후에도 유지됩니다.

## 1. 실제 복구 (운영)

```bash
cd /opt/kordi            # deploy/ 파일이 있는 디렉터리
ls -lt backups/ | head   # 복구할 파일 고르기

# S3에만 있는 경우
aws s3 cp s3://<bucket>/<prefix>/kordi-nightly-<ts>.sql.gz backups/

./restore.sh backups/kordi-predeploy-<ts>.sql.gz
# 'restore' 입력 → prerestore 덤프 → api 중지 → 복구 → api 시작

./dc ps                  # api가 healthy 인지
./dc logs -f api         # Flyway / 기동 로그 확인
```

### 배포 실패 + 마이그레이션이 이미 실행된 경우

`deploy.sh`가 롤백하면서 "Flyway history changed"를 출력했다면, 이전 코드가 새 스키마와 맞지 않을 수 있습니다.

1. 이전 버전 api가 healthy 라면, 새 마이그레이션이 **추가만** 한 경우(테이블/nullable 컬럼 추가) 그대로 두어도 됩니다.
   Flyway는 기본적으로 모르는 "미래" 마이그레이션을 무시하고, Hibernate `validate`는 추가 컬럼을 문제삼지 않습니다.
2. 이전 버전이 기동하지 못하거나(컬럼 rename/drop 등) 데이터가 잘못 변환됐다면 predeploy 덤프로 복구합니다:
   `./restore.sh <deploy.sh가 출력한 파일>` — 그 사이 들어온 쓰기는 잃습니다.
3. 원인을 고친 새 SHA로 다시 `./deploy.sh <sha>`.

### 복구를 되돌리기

`./restore.sh backups/kordi-prerestore-<ts>.sql.gz`

## 2. 복구 훈련 (분기 1회 권장, 운영 DB를 건드리지 않음)

같은 박스에서 **별도 임시 MySQL 컨테이너**에 최신 덤프를 넣어 보고, 행 수를 확인합니다.
운영 컨테이너/볼륨과 이름이 겹치지 않게 합니다. 박스 메모리가 작으므로 짧게 끝내고 반드시 정리합니다.

```bash
cd /opt/kordi
DUMP=$(ls -1 backups/kordi-nightly-*.sql.gz | sort | tail -n 1)
gzip -t "$DUMP" && echo "gzip OK: $DUMP"

# 1) 임시 MySQL (네트워크·포트 노출 없음, 볼륨 없음 → 종료 시 사라짐)
docker run -d --rm --name kordi-restore-drill --network none \
  -e MYSQL_ROOT_PASSWORD=drill --memory 512m mysql:8.0 \
  --innodb-buffer-pool-size=128M
until docker exec kordi-restore-drill mysqladmin ping -uroot -pdrill --silent 2>/dev/null; do sleep 2; done

# 2) 복구
gzip -cd "$DUMP" | docker exec -i kordi-restore-drill mysql -uroot -pdrill

# 3) 검증: Flyway 이력과 주요 테이블 행 수 — 운영 값과 비슷한지 비교
docker exec kordi-restore-drill mysql -uroot -pdrill -e "
  SELECT version, description, success FROM <DB_NAME>.flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
  SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = '<DB_NAME>' ORDER BY table_rows DESC LIMIT 10;"

# 4) 정리
docker stop kordi-restore-drill
```

기록할 것: 날짜, 사용한 덤프 파일, 복구 소요 시간, 확인한 행 수, 문제점.
덤프가 비었거나 `gzip -t`가 실패하면 cron 로그(`backups/cron.log`)와 디스크 여유부터 확인합니다.

S3 백업도 분기에 한 번은 위 1)~4)를 `aws s3 cp`로 내려받은 파일로 똑같이 해 봅니다.
