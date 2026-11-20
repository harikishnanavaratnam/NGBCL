#!/usr/bin/env bash
# Pull the latest main and rebuild/restart the API. Downtime is the API's start time (~30–60 s);
# avoid deploying during the auction or while matches are on court.
. "$(dirname "$0")/_common.sh"
cd "$DEPLOY_DIR/.." || exit 1
git fetch --quiet origin
git checkout --quiet main
git pull --ff-only --quiet
cd "$DEPLOY_DIR" || exit 1
"${COMPOSE[@]}" up -d --build
echo "Waiting for the API to report healthy…"
for _ in $(seq 1 40); do
  id=$("${COMPOSE[@]}" ps -q api)
  [ "$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null)" = "healthy" ] && { echo "API healthy."; docker image prune -f >/dev/null; exit 0; }
  sleep 5
done
echo "API did not become healthy — check: docker compose -f docker-compose.prod.yml logs --tail=200 api" >&2
exit 1
