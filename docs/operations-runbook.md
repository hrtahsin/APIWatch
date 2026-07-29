# APIWatch Operations Runbook

This runbook defines the supported deployment, backup, restore, and rollback
workflow for the Docker Compose installation. Keep the host behind a TLS reverse
proxy and keep the default loopback port binding.

## Release contract

`VERSION` is the canonical product version. A release tag must be exactly
`v<VERSION>`, for example `v0.1.0`. CI verifies that the backend, frontend, and
tag agree.

Every successful push to `main` publishes backend and frontend images to GitHub
Container Registry with immutable `sha-<short-commit>` tags. Version tags also
publish semantic-version tags. CI pins third-party actions to immutable commits,
reviews pull-request dependency changes, and generates image SBOM and provenance
attestations.

Deploy and roll back with matching backend and frontend `sha-*` tags. Do not
deploy a floating `main` or `latest` tag.

## Initial production deployment

1. Install Docker with Compose support and authenticate the host to GitHub
   Container Registry if the packages are private.
2. Copy the repository's Compose file, `.env.production.example`, and `scripts`
   directory to the host.
3. Copy `.env.production.example` to `.env`, restrict it to the service account,
   and replace every placeholder. Generate the encryption key with
   `openssl rand -base64 32`.
4. Set `APIWATCH_BACKEND_IMAGE` and `APIWATCH_FRONTEND_IMAGE` to matching
   immutable `sha-*` tags.
5. Pull and start without local builds:

   ```bash
   docker compose pull
   docker compose up -d --no-build
   docker compose ps
   ```

6. Confirm backend readiness and the frontend through the local listener before
   enabling reverse-proxy traffic:

   ```bash
   curl --fail http://127.0.0.1:8080/actuator/health/readiness
   curl --fail http://127.0.0.1:5173/
   ```

The frontend proxies same-origin `/api` requests to the backend. This makes the
published frontend image environment-independent and avoids cross-origin API
credentials in normal Compose deployments.

## Upgrade

1. Review the release notes and Flyway migrations.
2. Create and retain a verified database backup.
3. Record the currently deployed backend and frontend image tags.
4. Change both image variables in `.env` to the new matching `sha-*` tags.
5. Pull and replace the application containers:

   ```bash
   docker compose pull backend frontend
   docker compose up -d --no-build backend frontend
   docker compose ps
   ```

6. Confirm readiness, sign in, and verify the dashboard, one manual health check,
   incidents, notification settings, and Prometheus scraping.

## Backup

The backup script writes a compressed PostgreSQL custom-format dump with
permissions restricted by `umask 077`, validates its catalog, and creates a
SHA-256 checksum:

```bash
./scripts/backup-database.sh
```

An optional first argument selects a different backup directory. Copy both the
`.dump` and `.sha256` files to encrypted off-host storage. Test a restore on a
non-production instance before relying on a backup policy.

## Restore

Restoring is destructive. Schedule maintenance, preserve the current backup,
and stop the backend so no writes occur:

```bash
docker compose stop backend
APIWATCH_RESTORE_CONFIRM=restore-apiwatch \
  ./scripts/restore-database.sh backups/apiwatch-YYYYMMDDTHHMMSSZ.dump
docker compose start backend
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

The script refuses to restore while the backend is running, validates the
checksum when present, uses `pg_restore --clean --if-exists --exit-on-error`,
and analyzes the restored database.

## Application rollback

If the database schema remains compatible, set both image variables back to the
recorded previous `sha-*` tags, then run:

```bash
docker compose pull backend frontend
docker compose up -d --no-build backend frontend
```

If a migration is incompatible with the prior application, stop the backend,
restore the pre-upgrade backup, set the previous image tags, and start the
application. Never manually edit Flyway history.

## Incident checks

- `docker compose ps`: containers are running and healthy.
- `docker compose logs --since=15m backend`: correlated application errors.
- `/actuator/health/liveness`: process health.
- `/actuator/health/readiness`: process and database readiness.
- Authenticated `/actuator/prometheus`: request, scheduler, service, incident,
  and notification metrics.
- `X-Request-Id`: connect client errors to backend logs.

Rotate compromised bootstrap passwords or the encryption key through a planned
maintenance procedure. Changing the encryption key without re-encrypting stored
service and notification secrets makes those secrets unreadable.
