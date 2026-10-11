#!/usr/bin/env bash
# Run from repository root. API is paused so DB and private files form a consistent pair.
set -euo pipefail
umask 077
: "${AGE_RECIPIENT:?Set an age public recipient; keep the private identity off the VPS}"
zem_output=${1:?Usage: bash infra/backup.sh /secure/path/backup.age}
[[ "$zem_output" == *.age && ! -e "$zem_output" ]] || { echo 'Use a new .age output path' >&2; exit 1; }
for zem_cmd in docker age jq tar sha256sum; do command -v "$zem_cmd" >/dev/null; done
zem_tmp=$(mktemp -d)
zem_stopped=false
cleanup() {
  if [[ "$zem_stopped" == true ]]; then docker compose start api >/dev/null || true; fi
  rm -f -- "$zem_tmp/database.dump" "$zem_tmp/documents.tar" "$zem_tmp/SHA256SUMS"
  rmdir -- "$zem_tmp"
}
trap cleanup EXIT
zem_volume=$(docker compose config --format json | jq -er '.volumes.documents.name')
docker compose stop api >/dev/null
zem_stopped=true
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$zem_tmp/database.dump"
docker run --rm --network none -v "$zem_volume:/data:ro" alpine:3.22 tar -C /data -cf - . > "$zem_tmp/documents.tar"
(cd "$zem_tmp" && sha256sum database.dump documents.tar > SHA256SUMS)
tar -C "$zem_tmp" -cf - database.dump documents.tar SHA256SUMS | age -r "$AGE_RECIPIENT" -o "$zem_output"
echo 'Encrypted database and private-document backup created. Test restoration before production.'
