# Kordi 운영 배포 (단일 EC2)

EC2 한 대에서 `api`(Spring Boot) + `mysql` + `caddy`를 docker compose로 운영합니다.
이미지는 `main` 푸시 때 GitHub Actions가 `ghcr.io/kordi-app/kordi-api:latest`와 `:<커밋 SHA>`로 올립니다.
Flyway가 api 기동 시 마이그레이션합니다.

| 파일 | 역할 |
|---|---|
| `docker-compose.prod.yml` | 운영 스택. restart 정책, 메모리 제한, 헬스체크, 로그 로테이션, MySQL 비공개 |
| `.env.example` | 필요한 환경변수 **이름** 목록 → 박스에서 `.env`로 복사해 값 채움 |
| `Caddyfile` | TLS + api 리버스 프록시 (`/ws/world` WebSocket 포함) |
| `dc` | `docker compose` 래퍼. 배포된 태그(`.state/release.env`)를 자동 적용 |
| `deploy.sh` | 백업 → pull → api 교체 → 헬스 대기 → 실패 시 자동 롤백 |
| `backup.sh` / `crontab.example` | mysqldump → gzip → (선택) S3, 보관 개수 관리 |
| `restore.sh` / `RESTORE.md` | 덤프 복구, 복구 훈련 절차 |

## 가정 (메모리)

2 GB 인스턴스 기준: api `768m`(JVM 힙 = 75% ≈ 576m), mysql `640m`(buffer pool 256M), caddy `128m`.
나머지 ~0.5 GB는 OS/도커/페이지 캐시. 다른 크기면 `.env`에서 `API_MEM_LIMIT`, `MYSQL_MEM_LIMIT`,
`MYSQL_INNODB_BUFFER_POOL_SIZE`를 조정합니다 (1 GB면 512m / 384m / 128M + 스왑 권장).
api 정상 기동 후 `docker stats`로 실제 사용량을 보고 다시 맞추세요.

---

## 0. 기존 박스 점검 체크리스트 (도입 전에 먼저)

박스에 SSH로 들어가 확인만 합니다. 아무것도 바꾸지 않습니다.

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
```

- [ ] **3306 노출**: `0.0.0.0:3306->3306` 이 보이면 MySQL이 인터넷에 열려 있을 수 있음.
      `sudo ss -ltnp | grep 3306`, EC2 보안 그룹 인바운드에 3306이 있는지도 확인 → 있으면 즉시 제거.
- [ ] **restart 정책**: `docker inspect -f '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' $(docker ps -q)`
      → `no`면 재부팅/크래시 후 안 살아남. 도커 데몬 자동 시작: `systemctl is-enabled docker`.
- [ ] **메모리 제한**: `docker inspect -f '{{.Name}} {{.HostConfig.Memory}}' $(docker ps -q)` → `0`이면 무제한.
      `free -m`, `docker stats --no-stream`으로 현재 사용량 기록. 스왑 유무 `swapon --show`.
- [ ] **디스크**: `df -h /`, `docker system df`, `sudo du -sh /var/lib/docker/containers/*/*-json.log | sort -h | tail`
      → 로그 로테이션이 없으면 json 로그가 계속 자람.
- [ ] **현재 데이터 볼륨 이름**: `docker volume ls`, `docker inspect -f '{{range .Mounts}}{{.Name}} -> {{.Destination}}{{"\n"}}{{end}}' <mysql 컨테이너>`
      → `/var/lib/mysql`에 붙은 볼륨 이름을 적어 둠 (**가장 중요**). Caddy의 `/data` 볼륨도 (인증서).
- [ ] **MySQL 버전**: `docker exec <mysql> mysql --version` → 새 compose의 `MYSQL_IMAGE_TAG`와 같아야 함.
- [ ] **현재 api 이미지**: `docker inspect -f '{{.Image}} {{.Config.Image}}' <api 컨테이너>` (롤백 대비 기록).
- [ ] **기존 compose 파일/Caddyfile 위치**와 내용 백업: `cp` 해서 보관.
- [ ] **현재 이미지에 `/actuator/health`가 있는지**: `docker exec <api> curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/actuator/health`
      → 200이 아니면 `.env`에 `API_HEALTH_PATH=/health-check` (그것도 없으면 먼저 새 이미지를 기존 방식으로 배포).

## 1. 기존 박스에 안전하게 도입하기

순서가 중요합니다. **데이터 볼륨 이름을 맞추지 않으면 MySQL이 빈 DB로 뜨고 Flyway가 새 스키마를 만듭니다.**

```bash
# 1) 기존 상태를 백업 (기존 mysql 컨테이너에서 직접)
docker exec <기존 mysql> sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction \
  --routines --triggers --events --add-drop-database --databases "$MYSQL_DATABASE"' | gzip > ~/pre-migration.sql.gz
gzip -t ~/pre-migration.sql.gz

# 2) 파일 배치 (repo의 deploy/ 내용을 /opt/kordi 로)
sudo mkdir -p /opt/kordi && sudo chown "$USER" /opt/kordi
#   scp 등으로 docker-compose.prod.yml Caddyfile dc *.sh .env.example 복사
cd /opt/kordi && chmod +x dc *.sh

# 3) .env 작성: 기존 .env를 옮기고 .env.example의 이름과 대조. 그리고
#    MYSQL_VOLUME=<체크리스트에서 적은 mysql 볼륨 이름>
#    CADDY_DATA_VOLUME=<기존 caddy /data 볼륨 이름>   (없으면 생략 → 인증서 새로 발급)
#    API_DOMAIN=<api 도메인>
chmod 600 .env

# 4) 설정 검증 (실행 안 함) — 볼륨 이름, 포트(mysql에 ports 없음) 확인
./dc config | less

# 5) 전환: 기존 스택 내림 → 새 스택 올림 (수십 초 다운타임)
cd <기존 compose 디렉터리> && docker compose down      # -v 절대 금지 (볼륨 삭제)
cd /opt/kordi && ./dc up -d
./dc ps            # 세 컨테이너 모두 healthy 될 때까지
./dc logs -f api   # Flyway "Schema ... is up to date" 확인, 기존 데이터 보이는지 앱에서 확인

# 6) 보안 그룹에서 3306 인바운드 제거(있었다면), 8080도 열려 있었다면 제거 (Caddy 80/443만)
```

볼륨이 다른 compose 프로젝트에서 만들어졌다면 `./dc up` 때 "volume already exists but was not created by Docker Compose for this project" 경고가 나올 수 있는데, 같은 볼륨을 그대로 쓰는 것이므로 정상입니다.

문제가 생기면: `./dc down` → 기존 디렉터리에서 `docker compose up -d` 로 원복 (볼륨은 그대로이므로 데이터 유지).

이후 `crontab.example`을 참고해 야간 백업을 등록합니다.

## 2. 처음부터 새 박스 세팅

```bash
# Docker + compose plugin 설치 후
sudo usermod -aG docker "$USER" && newgrp docker
echo "<ghcr 읽기 토큰>" | docker login ghcr.io -u <github 계정> --password-stdin   # 이미지가 private일 때
sudo mkdir -p /opt/kordi && sudo chown "$USER" /opt/kordi   # deploy/ 파일 복사
cd /opt/kordi && cp .env.example .env && vi .env && chmod 600 .env
./dc up -d && ./dc ps
crontab -e   # crontab.example 참고
```

DNS(A 레코드)가 박스를 가리키고 보안 그룹에 80/443이 열려 있어야 Caddy가 인증서를 받습니다.
1~2 GB 박스는 스왑 1~2 GB 추가를 권장합니다 (OOM 시 MySQL이 죽는 것 방지).

## 3. 일상 배포

`main`에 머지 → Actions가 이미지 푸시 → 박스에서:

```bash
cd /opt/kordi
./deploy.sh <커밋 SHA 40자>   # 권장: 정확히 그 커밋
./deploy.sh latest            # 가능: :latest를 받아 이미지 라벨의 커밋 SHA로 고정해 기록
```

`deploy.sh`가 하는 일:
1. 실행 중인 api 이미지를 로컬 `:rollback` 태그로 보존
2. `backups/kordi-predeploy-*.sql.gz` 덤프 (`SKIP_BACKUP=1`로 생략 가능, 비권장)
3. 이미지 pull → `api`만 재생성 (mysql/caddy는 그대로)
4. 헬스체크 대기 (`HEALTH_TIMEOUT`, 기본 300초)
5. 성공: `.state/release.env`에 `KORDI_API_TAG`/`PREVIOUS_TAG` 기록
   실패: 마지막 로그 출력 → 이전 이미지로 자동 롤백 → Flyway 이력이 바뀌었으면 복구 방법 안내

배포 중 api 재시작 동안 짧은 다운타임(기동 시간만큼)이 있습니다. 단일 박스라 무중단은 아닙니다.

수동 명령은 항상 `./dc`로 실행합니다 (`docker compose`를 직접 쓰면 `.state/release.env`가 빠져 `:latest`로 바뀔 수 있음):

```bash
./dc ps
./dc logs -f --tail 200 api
./dc restart api
```

## 4. 롤백

```bash
cat .state/release.env        # KORDI_API_TAG(현재), PREVIOUS_TAG(직전)
./deploy.sh <PREVIOUS_TAG>    # 직전 SHA로 다시 배포 (백업·헬스체크 포함)
./deploy.sh rollback          # 직전 이미지가 SHA로 기록되지 않았을 때 (로컬 :rollback 태그)
```

마이그레이션이 이미 적용된 상태에서 코드만 되돌리는 것이므로, 스키마가 호환되지 않으면 덤프 복구가 필요합니다 → `RESTORE.md`.
가능하면 마이그레이션은 "추가만"(expand) 하고 삭제/rename은 다음 배포로 미루면 롤백이 안전해집니다.

## 5. 백업 / 복구

- 야간: `crontab.example` (`./backup.sh nightly`, `BACKUP_KEEP`개 보관, `BACKUP_S3_URI`가 있으면 `aws s3 cp`)
- 배포 전: `deploy.sh`가 자동으로 `predeploy` 덤프
- 수동: `./backup.sh manual`
- 복구: `./restore.sh backups/<file>.sql.gz` — 절차와 분기별 복구 훈련은 `RESTORE.md`

백업은 박스 디스크에만 있으면 박스와 함께 사라집니다. S3 업로드(+ 버킷 lifecycle로 30일 등 보관)를 권장합니다.
`backups/`와 `.state/`는 박스에만 두고 git에 넣지 않습니다.

## 6. 기타

- **로그**: 모든 컨테이너 json-file `10m × 5`개 로테이션. `docker system df`로 가끔 확인.
- **종료**: api는 `stop_grace_period: 45s` (Spring graceful shutdown 30초 + 여유).
- **포트**: 호스트에 열리는 포트는 Caddy 80/443뿐. MySQL은 internal 네트워크라 외부로 나가지도 못합니다.
  디버깅용 DB 접속은 `./dc exec mysql mysql -u<user> -p` 또는 SSH 터널 대신 이 방법을 씁니다.
- **레이트 리밋**: Caddy 기본 빌드에는 없음 → `Caddyfile` 주석 참고 (플러그인/WAF/앱 레벨).
- **actuator**: Caddy가 `/actuator/health` 외의 `/actuator/*`를 외부에 404로 막습니다.
