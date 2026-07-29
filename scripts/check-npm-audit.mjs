#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const frontendDirectory = path.join(repositoryRoot, "apiwatch-frontend");
const allowlistPath = path.join(
  repositoryRoot,
  "security",
  "npm-audit-allowlist.json",
);
const allowlist = JSON.parse(readFileSync(allowlistPath, "utf8"));
const audit = spawnSync(
  "npm",
  ["audit", "--omit=dev", "--audit-level=high", "--json"],
  {
    cwd: frontendDirectory,
    encoding: "utf8",
  },
);

if (audit.error) {
  console.error(`Unable to run npm audit: ${audit.error.message}`);
  process.exit(1);
}

let report;
try {
  report = JSON.parse(audit.stdout);
} catch {
  console.error("npm audit did not return a valid JSON report.");
  if (audit.stderr) {
    console.error(audit.stderr.trim());
  }
  process.exit(1);
}

const blockingSeverities = new Set(["high", "critical"]);
const findings = [];
const allowedFindings = [];
const today = new Date().toISOString().slice(0, 10);

for (const [packageName, vulnerability] of Object.entries(
  report.vulnerabilities ?? {},
)) {
  if (!blockingSeverities.has(vulnerability.severity)) {
    continue;
  }

  const advisories = vulnerability.via.filter(
    (entry) => typeof entry === "object" && entry.url,
  );

  for (const advisory of advisories) {
    const advisoryId = advisory.url.split("/").at(-1);
    const exception = allowlist[advisoryId];
    const exceptionIsValid =
      exception?.package === packageName && exception.expires >= today;

    if (exceptionIsValid) {
      allowedFindings.push({
        advisoryId,
        packageName,
        exception,
      });
    } else {
      findings.push({
        advisoryId,
        packageName,
        severity: advisory.severity,
        title: advisory.title,
        exception,
      });
    }
  }
}

for (const finding of allowedFindings) {
  console.warn(
    `Allowed ${finding.advisoryId} for ${finding.packageName} ` +
      `until ${finding.exception.expires}: ${finding.exception.reason}`,
  );
}

if (findings.length > 0) {
  console.error("Blocking production dependency vulnerabilities found:");
  for (const finding of findings) {
    const exceptionStatus = finding.exception
      ? " (allowlist entry is invalid or expired)"
      : "";
    console.error(
      `- ${finding.advisoryId} [${finding.severity}] ${finding.packageName}: ` +
        `${finding.title}${exceptionStatus}`,
    );
  }
  process.exit(1);
}

if (audit.status !== 0 && allowedFindings.length === 0) {
  console.error("npm audit failed without a recognized advisory.");
  if (audit.stderr) {
    console.error(audit.stderr.trim());
  }
  process.exit(1);
}

console.log("Production dependency audit passed.");
