# shellcheck shell=bash
# Shared by the scripts in this folder. Loads deploy/.env (as data, not as a script) and defines helpers.
set -Eeuo pipefail
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR" || exit 1
if [ ! -f .env ]; then echo "deploy/.env is missing — copy .env.example and fill it in." >&2; exit 1; fi
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in '' | '#'*) continue ;; esac
  key="${line%%=*}"
  val="${line#*=}"
  val="${val%%[[:space:]]#*}"            # drop an inline " # comment"
  val="${val%"${val##*[![:space:]]}"}"   # trim trailing spaces
  val="${val#\"}"; val="${val%\"}"
  [[ "$key" =~ ^[A-Z_][A-Z0-9_]*$ ]] && export "$key=$val"
done < .env
# shellcheck disable=SC2034  # used by the scripts that source this file
COMPOSE=(docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml")

# healthchecks.io ping: hc <url> [/start|/fail|""] [message]
hc() {
  local url="${1:-}" suffix="${2:-}" msg="${3:-}"
  [ -n "$url" ] || return 0
  curl -fsS -m 10 --retry 3 --data-raw "$msg" "$url$suffix" >/dev/null || true
}

# rclone in a container (no install needed), configured for the backup bucket on Cloudflare R2.
r2() {
  docker run --rm -v /tmp:/tmp \
    -e RCLONE_CONFIG_R2_TYPE=s3 -e RCLONE_CONFIG_R2_PROVIDER=Cloudflare \
    -e RCLONE_CONFIG_R2_ACCESS_KEY_ID="$BACKUP_R2_ACCESS_KEY" \
    -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$BACKUP_R2_SECRET_KEY" \
    -e RCLONE_CONFIG_R2_ENDPOINT="$BACKUP_R2_ENDPOINT" \
    -e RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true \
    rclone/rclone:1 "$@"
}
