#!/usr/bin/env bash
# Every 10 minutes from cron: disk space, memory and the API container's health.
# Reports to healthchecks.io (HC_DISK_URL): success when fine, /fail with the problem otherwise.
. "$(dirname "$0")/_common.sh"

problems=()
used=$(df --output=pcent / | tail -1 | tr -dc '0-9')
[ "$used" -lt "${DISK_ALERT_PERCENT:-85}" ] || problems+=("disk ${used}% full")
avail_mb=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
[ "$avail_mb" -gt 300 ] || problems+=("only ${avail_mb} MB memory free")
api_id=$("${COMPOSE[@]}" ps -q api || true)
state=$( [ -n "$api_id" ] && docker inspect -f '{{.State.Health.Status}}' "$api_id" 2>/dev/null || echo "missing")
[ "$state" = "healthy" ] || problems+=("api container is $state")

if [ ${#problems[@]} -eq 0 ]; then
  hc "${HC_DISK_URL:-}" "" "disk ${used}%, ${avail_mb} MB free, api healthy"
else
  msg="$(IFS='; '; echo "${problems[*]}")"
  echo "$(date -u +%FT%TZ) $msg" >&2
  hc "${HC_DISK_URL:-}" /fail "$msg"
fi
