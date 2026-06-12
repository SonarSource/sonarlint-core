# API Deprecation Monitor Report

**Generated:** 2026-06-12T12:52:30+00:00
**Trigger:** cron (automation)
**Repos scanned:** sonarlint-core
**Repos skipped:** (none)

## Run notes

- First catalog build on branch `cursor/api-deprecation-monitor-2ab4`; applied `unattended_defaults.non_master_branch: keep`.
- Full scan (no prior catalog state).
- All 3 configured upstream sources fetched successfully.
- 5 findings verified against source code (5 confirmed, 0 false positives).

---

No new action needed this week beyond ongoing awareness of the Web API v1→v2 migration. All confirmed findings are compatibility notices with no hard sunset dates within 90 days.

## Recommended Actions

1. **WARNING — Web API v1 gradual v2 migration** — Track SonarQube Server release notes for v1 endpoint deprecations affecting issues, hotspots, rules, and components APIs. Prioritize migrating endpoints as v2 equivalents ship. Evidence: [IssueApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java#L162). [source notice](https://www.sonarsource.com/blog/new-web-api-v2/)

2. **INFO — Legacy `/batch/issues` fallback** — No immediate change; retained for SQ < 9.6 compatibility. Re-evaluate when minimum supported server version increases. Evidence: [IssueApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java#L155). [source notice](https://docs.sonarsource.com/sonarqube-server/latest/extension-guide/web-api/)

---

## Findings

### Deprecations

(none with hard sunset dates affecting our code)

### Compatibility Notices

| Kind | Endpoint / Pattern | Platform | Sunset / Rollout | Severity | Affected Repos | Source |
|---|---|---|---|---|---|---|
| compatibility_notice | `/api/*` | sonarqube_server | staged rollout (11.x LTS) | WARNING | sonarlint-core | [notice](https://www.sonarsource.com/blog/new-web-api-v2/) |
| compatibility_notice | `/batch/issues` | sonarqube_server | unknown | INFO | sonarlint-core | [notice](https://docs.sonarsource.com/sonarqube-server/latest/extension-guide/web-api/) |
| compatibility_notice | Authorization header scheme | sonarqube_server | unknown | INFO | sonarlint-core | [notice](https://docs.sonarsource.com/sonarqube-server/latest/extension-guide/web-api/) |
| compatibility_notice | `/fix-suggestions/*`, `/sca/*`, `/dop-translation/*` | sonarqube_cloud | unknown | INFO | sonarlint-core | [notice](https://docs.sonarsource.com/sonarqube-cloud/) |
| compatibility_notice | `ingest.us.sentry.io` | other (Sentry) | unknown | INFO | sonarlint-core | [notice](https://docs.sentry.io/platforms/java/) |

### [dep-001] Web API v1 endpoints subject to gradual v2 migration

- **Kind:** compatibility_notice
- **Platform:** sonarqube_server
- **Affected endpoint or pattern:** `/api/*`
- **Announced:** 2024-01-15
- **Sunset:** staged rollout (v1 dropped as v2 equivalents ship; target 11.x LTS cycle)
- **Severity:** WARNING
- **Replacement:** Migrate to Web API v2 equivalents at `/api/v2/*` as they become available
- **Affected repos and files:**
  - [IssueApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java#L162)
  - [FixSuggestionsApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/fixsuggestions/FixSuggestionsApi.java#L41)
- **Source notice:** [vendor notice](https://www.sonarsource.com/blog/new-web-api-v2/)

### [dep-002] Legacy /batch/issues endpoint for pre-9.6 servers

- **Kind:** compatibility_notice
- **Platform:** sonarqube_server
- **Affected endpoint or pattern:** `/batch/issues`
- **Announced:** unknown
- **Sunset:** unknown
- **Severity:** INFO
- **Replacement:** `/api/issues/pull` (primary path for SQ >= 9.6)
- **Affected repos and files:**
  - [IssueApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java#L155)
  - [LineLevelServerIssue.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-connection/src/main/java/org/sonarsource/sonarlint/core/serverconnection/issues/LineLevelServerIssue.java#L34)
- **Source notice:** [vendor notice](https://docs.sonarsource.com/sonarqube-server/latest/extension-guide/web-api/)

### [dep-003] Bearer token authentication recommended since SonarQube 10.4

- **Kind:** compatibility_notice
- **Platform:** sonarqube_server
- **Affected endpoint or pattern:** `Authorization` header scheme
- **Announced:** 2024-01-15
- **Sunset:** unknown
- **Severity:** INFO
- **Replacement:** `Authorization: Bearer` token (already implemented)
- **Affected repos and files:**
  - [SonarQubeClientManager.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/core/src/main/java/org/sonarsource/sonarlint/core/SonarQubeClientManager.java)
- **Source notice:** [vendor notice](https://docs.sonarsource.com/sonarqube-server/latest/extension-guide/web-api/)

### [dep-004] SonarCloud-specific API paths differ from Server v2 prefix

- **Kind:** compatibility_notice
- **Platform:** sonarqube_cloud
- **Affected endpoint or pattern:** `/fix-suggestions/*`, `/sca/*`, `/dop-translation/*`
- **Announced:** unknown
- **Sunset:** unknown
- **Severity:** INFO
- **Replacement:** Use api_base_url paths without `/api/v2` prefix on SonarCloud
- **Affected repos and files:**
  - [ScaApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/sca/ScaApi.java#L41)
  - [FixSuggestionsApi.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/fixsuggestions/FixSuggestionsApi.java#L39)
- **Source notice:** [vendor notice](https://docs.sonarsource.com/sonarqube-cloud/)

### [dep-005] Sentry SDK DSN hardcoded default

- **Kind:** compatibility_notice
- **Platform:** other (Sentry)
- **Affected endpoint or pattern:** `https://o1316750.ingest.us.sentry.io/*`
- **Announced:** unknown
- **Sunset:** unknown
- **Severity:** INFO
- **Replacement:** Override via `sonarlint.internal.monitoring.dsn` system property
- **Affected repos and files:**
  - [MonitoringService.java](https://github.com/SonarSource/sonarlint-core/blob/07fbe6f84669772b6615f9c915926186ea1e0153/backend/core/src/main/java/org/sonarsource/sonarlint/core/monitoring/MonitoringService.java#L39)
- **Source notice:** [vendor notice](https://docs.sentry.io/platforms/java/)

---

## Catalog Summary

| Platform | Endpoints Tracked | Repos Using |
|---|---|---|
| sonarqube_server | 36 | sonarlint-core |
| sonarqube_cloud | 10 | sonarlint-core |
| sonarsource_binaries | 3 | sonarlint-core |
| sonarsource_telemetry | 3 | sonarlint-core |
| other | 1 | sonarlint-core |

**Total: 53 endpoints across 1 repos**

---

## SDK/Library Versions

| Library | Version in Use | Repos |
|---|---|---|
| io.sentry:sentry | 8.38.0 | sonarlint-core |
| org.eclipse.jgit:org.eclipse.jgit | 7.5.0.202512021534-r | sonarlint-core |
| org.eclipse.jgit:org.eclipse.jgit | 6.10.1.202505221210-r | sonarlint-core |
| com.squareup.okhttp3:okhttp | 5.3.2 | sonarlint-core |
| com.google.code.gson:gson | 2.13.2 | sonarlint-core |
| com.google.protobuf:protobuf-java | 4.34.1 | sonarlint-core |
