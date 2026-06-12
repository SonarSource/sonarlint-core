# API Deprecation Monitor Report
**Generated:** 2026-06-12T12:57:31Z · **Trigger:** cron
**Repos scanned:** sonarlint-core · **Repos skipped:** none

## Run notes
- Scanned on non-master branch cursor/api-deprecation-monitor-a155 (unattended_defaults: keep)
- First monitor run — no prior state branch

## Recommended Actions
1. **[INFO]** SonarQube Web API v1 endpoints subject to gradual v2 migration (compatibility_notice) — sunset 2027-01-01
2. **[INFO]** SonarCloud Web API v1 at sonarcloud.io/api/ being replaced by v2 at api.sonarcloud.io (compatibility_notice) — sunset 2027-01-01
3. **[INFO]** Protobuf-based Web API responses may be superseded by JSON v2 endpoints (deprecation) — sunset 2027-06-01

## Findings

### Deprecations
| Kind | Endpoint | Platform | Sunset | Severity | Repos | Source |
|---|---|---|---|---|---|---|
| compatibility_notice | /api/* | sonarqube_server | 2027-01-01 | info | sonarlint-core | [source notice](https://www.sonarsource.com/blog/new-web-api-v2) |
| compatibility_notice | /api/* | sonarqube_cloud | 2027-01-01 | info | sonarlint-core | [source notice](https://docs.sonarsource.com/sonarqube-cloud/appendices/web-api) |
| deprecation | /api/issues/search.protobuf | sonarqube_server | 2027-06-01 | info | sonarlint-core | [source notice](https://docs.sonarsource.com/sonarqube-server/extension-guide/web-api) |

### [dep-001] SonarQube Web API v1 endpoints subject to gradual v2 migration
- Kind: compatibility_notice, platform: sonarqube_server, endpoint: `/api/*`
- Announced: 2024-01-01, sunset: 2027-01-01, severity: info
- Replacement: Migrate to Web API v2 endpoints at /api/v2/* as v2 equivalents become available
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/hotspot/HotspotApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/hotspot/HotspotApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/rules/RulesApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/rules/RulesApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/settings/SettingsApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/settings/SettingsApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/component/ComponentApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/component/ComponentApi.java)
- [source notice](https://www.sonarsource.com/blog/new-web-api-v2)

### [dep-002] SonarCloud Web API v1 at sonarcloud.io/api/ being replaced by v2 at api.sonarcloud.io
- Kind: compatibility_notice, platform: sonarqube_cloud, endpoint: `/api/*`
- Announced: 2024-01-01, sunset: 2027-01-01, severity: info
- Replacement: Use api.sonarcloud.io for v2 endpoints; existing v1 paths on sonarcloud.io remain during transition
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/plugins/PluginsApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/plugins/PluginsApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/authentication/AuthenticationApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/authentication/AuthenticationApi.java)
- [sonarlint-core] [backend/core/src/main/java/org/sonarsource/sonarlint/core/SonarCloudRegion.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/core/src/main/java/org/sonarsource/sonarlint/core/SonarCloudRegion.java)
- [source notice](https://docs.sonarsource.com/sonarqube-cloud/appendices/web-api)

### [dep-003] Protobuf-based Web API responses may be superseded by JSON v2 endpoints
- Kind: deprecation, platform: sonarqube_server, endpoint: `/api/issues/search.protobuf`
- Announced: 2024-01-01, sunset: 2027-06-01, severity: info
- Replacement: Use Web API v2 JSON endpoints when available for issues, hotspots, and rules
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/issue/IssueApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/hotspot/HotspotApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/hotspot/HotspotApi.java)
- [sonarlint-core] [backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/rules/RulesApi.java](https://github.com/SonarSource/sonarlint-core/blob/63f6dd6d9bd2fd7e699a7ea43399e27d42db75de/backend/server-api/src/main/java/org/sonarsource/sonarlint/core/serverapi/rules/RulesApi.java)
- [source notice](https://docs.sonarsource.com/sonarqube-server/extension-guide/web-api)

## Catalog Summary
| Platform | Endpoints | Repos |
|---|---|---|
| other | 2 | sonarlint-core |
| sonarqube_cloud | 5 | sonarlint-core |
| sonarqube_server | 16 | sonarlint-core |
| sonarsource_binaries | 2 | sonarlint-core |

## SDK/Library Versions
| Library | Version | Repos |
|---|---|---|
| com.squareup.okhttp3:okhttp | 5.3.2 | sonarlint-core |
| io.sentry:sentry | 8.38.0 | sonarlint-core |
| org.apache.httpcomponents.client5:httpclient5 | 5.6.1 | sonarlint-core |
| org.sonarsource.api.plugin:sonar-plugin-api | 13.4.2.4284 | sonarlint-core |
| org.sonarsource.sonarqube:sonar-scanner-protocol | 9.9.0.65466 | sonarlint-core |
