#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: APIWATCH_RESTORE_CONFIRM=restore-apiwatch $0 <backup.dump>" >&2
  exit 1
fi

if [[ "${APIWATCH_RESTORE_CONFIRM:-}" != "restore-apiwatch" ]]; then
  echo "Restore refused. Set APIWATCH_RESTORE_CONFIRM=restore-apiwatch to confirm." >&2
  exit 1
fi

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup_path="$1"

if [[ ! -s "${backup_path}" ]]; then
  echo "Backup '${backup_path}' does not exist or is empty." >&2
  exit 1
fi

backup_directory="$(cd "$(dirname "${backup_path}")" && pwd -P)"
backup_name="$(basename "${backup_path}")"
backup_path="${backup_directory}/${backup_name}"
checksum_path="${backup_path}.sha256"

if [[ -f "${checksum_path}" ]]; then
  (
    cd "${backup_directory}"
    if command -v sha256sum > /dev/null 2>&1; then
      sha256sum --check "${backup_name}.sha256"
    else
      shasum -a 256 --check "${backup_name}.sha256"
    fi
  )
else
  echo "Warning: no checksum file found at '${checksum_path}'." >&2
fi

cd "${repository_root}"
postgres_container="$(docker compose ps -q postgres)"
if [[ -z "${postgres_container}" ]] ||
  [[ "$(docker inspect --format '{{.State.Running}}' "${postgres_container}")" != "true" ]]; then
  echo "The APIWatch postgres service must be running before a restore." >&2
  exit 1
fi

backend_container="$(docker compose ps -q backend)"
if [[ -n "${backend_container}" ]] &&
  [[ "$(docker inspect --format '{{.State.Running}}' "${backend_container}")" == "true" ]]; then
  echo "Stop the APIWatch backend before restoring the database." >&2
  exit 1
fi

database_name="$(docker compose exec -T postgres printenv POSTGRES_DB | tr -d '\r')"
database_user="$(docker compose exec -T postgres printenv POSTGRES_USER | tr -d '\r')"

docker compose exec -T postgres pg_restore \
  --username="${database_user}" \
  --dbname="${database_name}" \
  --clean \
  --if-exists \
  --exit-on-error \
  --no-owner \
  --no-privileges < "${backup_path}"

docker compose exec -T postgres \
  psql \
  --username="${database_user}" \
  --dbname="${database_name}" \
  --command="ANALYZE;"

echo "Restore completed from: ${backup_path}"
echo "Start the backend and verify the readiness endpoint before returning traffic."
