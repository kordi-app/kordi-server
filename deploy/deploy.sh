#!/usr/bin/env bash
# Deploy a kordi-api image tag with a pre-deploy backup and automatic rollback.
#
#   ./deploy.sh <git-sha|latest>
#
# 1. snapshot the running api image as :rollback (works even when it was :latest)
# 2. mysqldump -> backups/kordi-predeploy-<ts>.sql.gz   (SKIP_BACKUP=1 to skip)
# 3. pull the tag, recreate only the api container
# 4. wait for the container healthcheck (HEALTH_TIMEOUT seconds, default 300)
# 5. healthy  -> record the tag in .state/release.env
#    unhealthy -> start the previous image again, and print how to restore the dump
#                 if a Flyway migration ran in the meantime
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=ghcr.io/kordi-app/kordi-api
STATE_DIR=.state
STATE_FILE=$STATE_DIR/release.env
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-300}
SKIP_BACKUP=${SKIP_BACKUP:-0}

log() { printf '[deploy %s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() {
  log "ERROR: $*"
  exit 1
}

if [[ $# -ne 1 || $1 == -h || $1 == --help ]]; then
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi
TAG=$1
[[ $TAG =~ ^[A-Za-z0-9_.-]+$ ]] || die "invalid tag '$TAG'"
[[ -f .env ]] || die ".env not found next to $0 (see .env.example)"

mkdir -p "$STATE_DIR"
if command -v flock >/dev/null; then
  exec 9>"$STATE_DIR/deploy.lock"
  flock -n 9 || die "another deploy is running"
fi

state_get() { # state_get KEY -> value or empty
  [[ -f $STATE_FILE ]] || return 0
  sed -n "s/^$1=//p" "$STATE_FILE" | tail -n 1
}

write_state() { # write_state TAG PREVIOUS [FAILED]
  local tmp=$STATE_FILE.tmp
  {
    printf 'KORDI_API_TAG=%s\n' "$1"
    printf 'PREVIOUS_TAG=%s\n' "$2"
    [[ -z ${3:-} ]] || printf 'FAILED_TAG=%s\n' "$3"
    printf 'UPDATED_AT=%s\n' "$(date -u +%FT%TZ)"
  } >"$tmp"
  mv "$tmp" "$STATE_FILE"
}

api_container() { ./dc ps -q api 2>/dev/null || true; }

# highest Flyway installed_rank, or "unknown"
flyway_rank() {
  local rank
  # shellcheck disable=SC2016  # expands inside the mysql container
  rank=$(./dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B \
      -e "SELECT COALESCE(MAX(installed_rank), 0) FROM flyway_schema_history" "$MYSQL_DATABASE"' \
    2>/dev/null) || rank=unknown
  printf '%s\n' "${rank:-unknown}"
}

# wait until the api container's healthcheck says healthy; 1 on timeout
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) cid status
  while ((SECONDS < deadline)); do
    cid=$(api_container)
    if [[ -n $cid ]]; then
      status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$cid" 2>/dev/null || echo missing)
      case $status in
        healthy) return 0 ;;
        no-healthcheck) die "api container has no healthcheck — is docker-compose.prod.yml in use?" ;;
      esac
      log "api: $status"
    fi
    sleep 5
  done
  return 1
}

# --- preflight --------------------------------------------------------------------------
mysql_cid=$(./dc ps -q mysql 2>/dev/null || true)
[[ -n $mysql_cid ]] || die "mysql is not running — first start the stack with ./dc up -d"
[[ $(docker inspect -f '{{.State.Health.Status}}' "$mysql_cid") == healthy ]] ||
  die "mysql is not healthy"

prev_tag=$(state_get KORDI_API_TAG)
prev_image_id=""
cid=$(api_container)
if [[ -n $cid ]]; then
  prev_image_id=$(docker inspect -f '{{.Image}}' "$cid")
  # keep the running image addressable even after :latest moves
  docker tag "$prev_image_id" "$IMAGE:rollback"
  log "current: ${prev_tag:-unrecorded} (${prev_image_id:7:12}), snapshot tagged :rollback"
else
  log "no api container running — nothing to roll back to"
fi

# --- backup -----------------------------------------------------------------------------
backup_file=""
if [[ $SKIP_BACKUP == 1 ]]; then
  log "SKIP_BACKUP=1 — no pre-deploy dump"
else
  backup_file=$(./backup.sh predeploy) || die "backup failed — not deploying"
fi
rank_before=$(flyway_rank)

# --- pull -------------------------------------------------------------------------------
if [[ $TAG == latest ]] || ! docker image inspect "$IMAGE:$TAG" >/dev/null 2>&1; then
  log "pulling $IMAGE:$TAG"
  KORDI_API_TAG=$TAG ./dc pull api
fi

# pin :latest to the commit it was built from, so the state file and a later rollback name a
# real tag (needs the org.opencontainers.image.revision label set by the CI workflow)
deploy_tag=$TAG
if [[ $TAG == latest ]]; then
  rev=$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$IMAGE:latest" 2>/dev/null || true)
  if [[ $rev =~ ^[0-9a-f]{7,40}$ ]]; then
    docker tag "$IMAGE:latest" "$IMAGE:$rev"
    deploy_tag=$rev
    log ":latest is $rev"
  fi
fi

# --- roll out ---------------------------------------------------------------------------
log "starting api $deploy_tag"
KORDI_API_TAG=$deploy_tag ./dc up -d --no-deps api

# what ran before: the recorded tag, else the local :rollback snapshot, else nothing
previous=${prev_tag:-${prev_image_id:+rollback}}
if wait_healthy; then
  write_state "$deploy_tag" "$previous"
  log "deployed $deploy_tag (previous: ${previous:-none})"
  docker image prune -f >/dev/null || true
  exit 0
fi

# --- rollback ---------------------------------------------------------------------------
log "api did not become healthy within ${HEALTH_TIMEOUT}s — last logs:"
./dc logs --no-color --tail 80 api >&2 || true

[[ -n $prev_image_id ]] || die "no previous image to roll back to; fix forward with ./deploy.sh <sha>"

# prefer the recorded tag if it still points at the image that was running
rollback_tag=rollback
if [[ -n $prev_tag && $prev_tag != latest ]] &&
  [[ $(docker image inspect -f '{{.Id}}' "$IMAGE:$prev_tag" 2>/dev/null || true) == "$prev_image_id" ]]; then
  rollback_tag=$prev_tag
fi

log "rolling back to $rollback_tag"
KORDI_API_TAG=$rollback_tag ./dc up -d --no-deps api
write_state "$rollback_tag" "$(state_get PREVIOUS_TAG)" "$deploy_tag"

if wait_healthy; then
  log "rolled back to $rollback_tag"
else
  log "rollback image is not healthy either — check ./dc logs api"
fi

rank_after=$(flyway_rank)
if [[ $rank_before != "$rank_after" || $rank_before == unknown ]]; then
  log "Flyway history changed ($rank_before -> $rank_after) or could not be read."
  log "The old code may not match the migrated schema. To restore the pre-deploy dump:"
  if [[ -n $backup_file ]]; then
    log "  ./restore.sh $backup_file      (see RESTORE.md)"
  else
    log "  no pre-deploy dump was taken (SKIP_BACKUP=1) — use the latest nightly in backups/"
  fi
fi
die "deploy of $deploy_tag failed"
