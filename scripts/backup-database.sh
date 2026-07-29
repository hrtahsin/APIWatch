#!/usr/bin/env bash

set -euo pipefail
umask 077

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup_directory="${1:-${repository_root}/backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_name="apiwatch-${timestamp}.dump"
backup_path="${backup_directory}/${backup_name}"
temporary_path="${backup_path}.tmp"

cleanup() {
  rm -f "${temporary_path}"
}
trap cleanup EXIT

cd "${repository_root}"
mkdir -p "${backup_directory}"

postgres_container="$(docker compose ps -q postgres)"
if [[ -z "${postgres_container}" ]] ||
  [[ "$(docker inspect --format '{{.State.Running}}' "${postgres_container}")" != "true" ]]; then
  echo "The APIWatch postgres service must be running before a backup." >&2
  exit 1
fi

database_name="$(docker compose exec -T postgres printenv POSTGRES_DB | tr -d '\r')"
database_user="$(docker compose exec -T postgres printenv POSTGRES_USER | tr -d '\r')"

docker compose exec -T postgres \
  pg_dump \
  --username="${database_user}" \
  --dbname="${database_name}" \
  --format=custom \
  --compress=9 \
  --no-owner \
  --no-privileges > "${temporary_path}"

if [[ ! -s "${temporary_path}" ]]; then
  echo "PostgreSQL produced an empty backup." >&2
  exit 1
fi

docker compose exec -T postgres pg_restore --list < "${temporary_path}" > /dev/null
mv "${temporary_path}" "${backup_path}"

(
  cd "${backup_directory}"
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "${backup_name}" > "${backup_name}.sha256"
  else
    shasum -a 256 "${backup_name}" > "${backup_name}.sha256"
  fi
)

trap - EXIT
echo "Backup created: ${backup_path}"
echo "Checksum created: ${backup_path}.sha256"
