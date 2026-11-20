#!/usr/bin/env bash
# Keeps the DuckDNS name pointing at this server (cron every 5 minutes; harmless if the IP never changes).
. "$(dirname "$0")/_common.sh"
out=$(curl -fsS -m 15 "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAINS:?}&token=${DUCKDNS_TOKEN:?}&ip=")
[ "$out" = "OK" ] || { echo "$(date -u +%FT%TZ) DuckDNS update failed: $out" >&2; exit 1; }
