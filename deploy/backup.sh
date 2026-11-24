#!/usr/bin/env bash
# Dump the Kordi database to a gzip file, prune old dumps, optionally copy to S3.
#
#   ./backup.sh [label]        label: nightly (default) | predeploy | manual | ...
#
# Env (all optional):
#   BACKUP_DIR      where dumps go                     (default: ./backups)
#   BACKUP_KEEP     dumps to keep per label            (default: 14)
#   BACKUP_S3_URI   e.g. s3://my-bucket/kordi/mysql — if set, `aws s3 cp` each dump there.
#                   Retention in S3 is a bucket lifecycle rule, not this script.
#
# Credentials never leave the mysql container: mysqldump runs inside it with the
# container's own MYSQL_ROOT_PASSWORD / MYSQL_DATABASE.
#
# stdout: the path of the new dump (only that — deploy.sh reads it). Logs go to stderr.
set -euo pipefail
cd "$(dirname "$0")"

label=${1:-nightly}
if [[ ! $label =~ ^[a-z0-9-]+$ ]]; then
  echo "backup: invalid label '$label'" >&2
  exit 2
fi

BACKUP_DIR=${BACKUP_DIR:-./backups}
BACKUP_KEEP=${BACKUP_KEEP:-14}
BACKUP_S3_URI=${BACKUP_S3_URI:-}

log() { printf '[backup %s] %s\n' "$(date -u +%FT%TZ)" "$*" >&2; }

umask 077
mkdir -p "$BACKUP_DIR"

ts=$(date -u +%Y%m%dT%H%M%SZ)
out="$BACKUP_DIR/kordi-$label-$ts.sql.gz"
tmp="$out.partial"
trap 'rm -f "$tmp"' EXIT

log "dumping to $out"
# --single-transaction: consistent InnoDB snapshot without locking the app out
# --add-drop-database: restoring the file replaces the database as a whole, so tables a later
#   migration created don't survive a restore (Flyway would trip over them on the next boot)
# shellcheck disable=SC2016  # $MYSQL_* expand inside the container, not here
./dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
    --single-transaction --quick --routines --triggers --events \
    --set-gtid-purged=OFF --add-drop-database --databases "$MYSQL_DATABASE"' \
  | gzip -6 >"$tmp"

# a dump cut short (OOM, disk full) has no completion marker
if ! gzip -cd "$tmp" | tail -n 1 | grep -q '^-- Dump completed'; then
  log "ERROR: dump is incomplete"
  exit 1
fi
mv "$tmp" "$out"
trap - EXIT
log "ok ($(du -h "$out" | cut -f1))"

if [[ -n $BACKUP_S3_URI ]]; then
  log "uploading to ${BACKUP_S3_URI%/}/"
  aws s3 cp --only-show-errors "$out" "${BACKUP_S3_URI%/}/$(basename "$out")"
fi

# retention: newest BACKUP_KEEP dumps of this label (timestamps sort lexicographically)
find "$BACKUP_DIR" -maxdepth 1 -type f -name "kordi-$label-*.sql.gz" | sort -r |
  tail -n +"$((BACKUP_KEEP + 1))" |
  while IFS= read -r stale; do
    log "pruning $stale"
    rm -f -- "$stale"
  done

printf '%s\n' "$out"
