#!/usr/bin/env bash
# Restore a dump made by backup.sh into the running mysql container.
#
#   ./restore.sh backups/kordi-predeploy-20261124T010203Z.sql.gz [--yes]
#
# The dump was taken with --add-drop-database: the database is DROPPED and recreated.
# The api is stopped during the restore and started again afterwards.
# Takes a safety dump of the current state first (label: prerestore).
set -euo pipefail
cd "$(dirname "$0")"

log() { printf '[restore %s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }

dump=${1:-}
[[ -n $dump && -f $dump ]] || {
  echo "usage: $0 <dump.sql.gz> [--yes]" >&2
  exit 2
}
gzip -t "$dump"

if [[ ${2:-} != --yes ]]; then
  read -r -p "This REPLACES the production database with $dump. Type 'restore' to continue: " answer
  [[ $answer == restore ]] || {
    log "aborted"
    exit 1
  }
fi

log "safety dump of the current database"
./backup.sh prerestore >/dev/null

log "stopping api"
./dc stop api

log "restoring $dump"
# shellcheck disable=SC2016  # expands inside the mysql container
gzip -cd "$dump" | ./dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'

log "starting api"
./dc up -d --no-deps api
log "done — watch ./dc ps / ./dc logs -f api until api is healthy"
