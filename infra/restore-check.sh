#!/usr/bin/env bash
# Validates a backup in a new disposable DB; never targets the live Compose database.
set -euo pipefail
umask 077
: "${AGE_IDENTITY:?Set the path to an age private identity on a trusted recovery host}"
zem_archive=${1:?Usage: bash infra/restore-check.sh /secure/path/backup.age}
for zem_cmd in docker age tar sha256sum; do command -v "$zem_cmd" >/dev/null; done
zem_tmp=$(mktemp -d)
zem_container="zem-restore-check-$(date +%s)-$$"
cleanup() {
  docker rm -f "$zem_container" >/dev/null 2>&1 || true
  rm -f -- "$zem_tmp/bundle.tar" "$zem_tmp/database.dump" "$zem_tmp/documents.tar" "$zem_tmp/SHA256SUMS"
  rmdir -- "$zem_tmp"
}
trap cleanup EXIT
age -d -i "$AGE_IDENTITY" -o "$zem_tmp/bundle.tar" "$zem_archive"
for zem_file in database.dump documents.tar SHA256SUMS; do tar -xOf "$zem_tmp/bundle.tar" "$zem_file" > "$zem_tmp/$zem_file"; done
(cd "$zem_tmp" && sha256sum -c SHA256SUMS)
tar -tf "$zem_tmp/documents.tar" >/dev/null
docker run -d --name "$zem_container" --network none -e POSTGRES_PASSWORD=isolated-restore-test postgres:17-alpine >/dev/null
for zem_attempt in $(seq 1 60); do
  if docker exec "$zem_container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec -i "$zem_container" pg_restore -U postgres -d postgres --no-owner --no-privileges --exit-on-error < "$zem_tmp/database.dump"
docker exec "$zem_container" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'SELECT version,success FROM flyway_schema_history; SELECT count(*) AS dossiers FROM dossier; SELECT count(*) AS cards FROM digital_card;'
echo 'Isolated PostgreSQL restoration and document archive integrity verified.'
