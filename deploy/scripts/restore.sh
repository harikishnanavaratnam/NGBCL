#!/usr/bin/env bash
# Restores the database from a backup. DESTRUCTIVE: replaces everything in the current database.
#   scripts/restore.sh --list                         # show backups in R2
#   scripts/restore.sh daily/ngbcl-20261120T211500Z.sql.gz.enc
#   scripts/restore.sh /path/to/local-file.sql.gz.enc
. "$(dirname "$0")/_common.sh"

if [ "${1:-}" = "--list" ]; then r2 lsl "r2:$BACKUP_R2_BUCKET"; exit 0; fi
src="${1:?give a backup key (daily/…, weekly/…) or a local file — see --list}"

work="/tmp/ngbcl-restore-$$.sql.gz.enc"
trap 'rm -f "$work"' EXIT
if [ -f "$src" ]; then cp "$src" "$work"; else r2 copyto "r2:$BACKUP_R2_BUCKET/$src" "$work"; fi

# Check the passphrase and the archive before touching the database.
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$work" | gunzip | head -c 0
echo "Backup decrypts fine."

read -r -p "This REPLACES the live database with $src. Type RESTORE to continue: " answer
[ "$answer" = "RESTORE" ] || { echo "Cancelled."; exit 1; }

"${COMPOSE[@]}" stop api
"${COMPOSE[@]}" exec -T db psql -U ngbcl -d ngbcl -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$work" | gunzip \
  | "${COMPOSE[@]}" exec -T db psql -U ngbcl -d ngbcl -v ON_ERROR_STOP=1 -q
"${COMPOSE[@]}" start api
echo "Restored $src. The API is starting — check https://$API_DOMAIN/actuator/health in a minute."
