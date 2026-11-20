#!/usr/bin/env bash
# Nightly encrypted database backup to Cloudflare R2 (daily/ and, on Sundays, weekly/), then prunes old copies.
# Pings healthchecks.io (HC_BACKUP_URL): start, success, or fail with the reason — so a silent failure emails you.
. "$(dirname "$0")/_common.sh"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
kind=daily; [ "$(date -u +%u)" = 7 ] && kind=weekly
file="/tmp/ngbcl-$stamp.sql.gz.enc"
trap 'rm -f "$file"' EXIT
trap 'hc "${HC_BACKUP_URL:-}" /fail "backup failed at line $LINENO"' ERR

[ -n "${BACKUP_PASSPHRASE:-}" ] || { echo "BACKUP_PASSPHRASE is empty" >&2; false; }
hc "${HC_BACKUP_URL:-}" /start

"${COMPOSE[@]}" exec -T db pg_dump -U ngbcl -d ngbcl --no-owner --no-privileges \
  | gzip -9 \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -out "$file"

size=$(stat -c %s "$file")
[ "$size" -gt 2048 ] || { echo "backup suspiciously small ($size bytes)" >&2; false; }

r2 copyto "$file" "r2:$BACKUP_R2_BUCKET/$kind/$(basename "$file")"
r2 delete --min-age "${BACKUP_KEEP_DAILY:-14}d" "r2:$BACKUP_R2_BUCKET/daily"
r2 delete --min-age "$(( ${BACKUP_KEEP_WEEKLY:-8} * 7 ))d" "r2:$BACKUP_R2_BUCKET/weekly"

echo "$(date -u +%FT%TZ) backup ok: $kind/$(basename "$file") ($size bytes)"
hc "${HC_BACKUP_URL:-}" "" "$kind/$(basename "$file") $size bytes"
