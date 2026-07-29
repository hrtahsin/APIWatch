#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release_version="$(tr -d '[:space:]' < "${repository_root}/VERSION")"
release_tag="${1:-}"

if [[ ! "${release_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]]; then
  echo "VERSION must contain a semantic version; found '${release_version}'." >&2
  exit 1
fi

backend_version="$(
  python3 - "${repository_root}/apiwatch-backend/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
project = ET.parse(sys.argv[1]).getroot()
version = project.find("m:version", namespace)
if version is None or not version.text:
    raise SystemExit("Backend project version is missing from pom.xml")
print(version.text.strip())
PY
)"

frontend_version="$(
  node -p "require('${repository_root}/apiwatch-frontend/package.json').version"
)"

if [[ "${backend_version}" != "${release_version}" &&
      "${backend_version}" != "${release_version}-SNAPSHOT" ]]; then
  echo "Backend version '${backend_version}' does not match VERSION '${release_version}'." >&2
  exit 1
fi

if [[ "${frontend_version}" != "${release_version}" ]]; then
  echo "Frontend version '${frontend_version}' does not match VERSION '${release_version}'." >&2
  exit 1
fi

if [[ -n "${release_tag}" && "${release_tag}" != "v${release_version}" ]]; then
  echo "Release tag '${release_tag}' must equal 'v${release_version}'." >&2
  exit 1
fi

echo "Release version ${release_version} is consistent."
