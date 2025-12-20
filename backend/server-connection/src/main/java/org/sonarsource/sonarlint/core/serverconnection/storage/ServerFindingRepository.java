/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.TableField;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.storage.model.Tables;
import org.sonarsource.sonarlint.core.commons.storage.model.tables.records.ServerBranchesRecord;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerFinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_BRANCHES;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_DEPENDENCY_RISKS;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FINDINGS;

public class ServerFindingRepository implements ProjectServerIssueStore {

  private final EntityMapper mapper = new EntityMapper();
  private final DSLContext database;
  private final String connectionId;
  private final String sonarProjectKey;

  public ServerFindingRepository(DSLContext database, String connectionId, String sonarProjectKey) {
    this.database = database;
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
  }

  @Override
  public boolean wasEverUpdated() {
    return database.fetchExists(
      database.selectFrom(SERVER_BRANCHES)
        .where(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId)
          .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey))
          .and(SERVER_BRANCHES.LAST_ISSUE_SYNC_TS.isNotNull().or(SERVER_BRANCHES.LAST_HOTSPOT_SYNC_TS.isNotNull()).or(SERVER_BRANCHES.LAST_TAINT_SYNC_TS.isNotNull()))));
  }

  @Override
  public void replaceAllIssuesOfBranch(String branchName, List<ServerIssue<?>> issues, Set<SonarLanguage> enabledLanguages) {
    database.transaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      batchMergeIssues(branchName, connectionId, sonarProjectKey, trx, issues);
    });
    upsertBranchMetadata(branchName,
      SERVER_BRANCHES.LAST_ISSUE_SYNC_TS,
      SERVER_BRANCHES.LAST_ISSUE_ENABLED_LANGS,
      Instant.now(), enabledLanguages);
  }

  @Override
  public void replaceAllHotspotsOfBranch(String branchName, Collection<ServerHotspot> serverHotspots, Set<SonarLanguage> enabledLanguages) {
    database.transaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      batchMergeHotspots(branchName, connectionId, sonarProjectKey, trx, serverHotspots);
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_HOTSPOT_SYNC_TS,
        SERVER_BRANCHES.LAST_HOTSPOT_ENABLED_LANGS,
        Instant.now(), enabledLanguages);
    });
  }

  @Override
  public void replaceAllHotspotsOfFile(String branchName, Path serverFilePath, Collection<ServerHotspot> serverHotspots) {
    database.transaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FILE_PATH.eq(serverFilePath.toString()))
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      batchMergeHotspots(branchName, connectionId, sonarProjectKey, trx, serverHotspots);
    });
  }

  // we don't consume return value for now, probably should be void
  @Override
  public boolean changeHotspotStatus(String hotspotKey, HotspotReviewStatus newStatus) {
    database.update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.HOTSPOT_REVIEW_STATUS, newStatus.name())
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .execute();
    return true;
  }

  @Override
  public void replaceAllIssuesOfFile(String branchName, Path serverFilePath, List<ServerIssue<?>> issues) {
    database.transaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FILE_PATH.eq(serverFilePath.toString()))
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      batchMergeIssues(branchName, connectionId, sonarProjectKey, trx, issues);
    });
  }

  @Override
  public void mergeIssues(String branchName, List<ServerIssue<?>> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    database.transaction(trx -> {
      if (!closedIssueKeysToDelete.isEmpty()) {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
            .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
            .and(SERVER_FINDINGS.SERVER_KEY.in(closedIssueKeysToDelete))
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
      }
      var issueIds = issuesToMerge.stream().map(ServerIssue::getId).collect(Collectors.toSet());
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.ID.in(issueIds))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey))
        .execute();
      batchMergeIssues(branchName, connectionId, sonarProjectKey, trx, issuesToMerge);
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_ISSUE_SYNC_TS,
        SERVER_BRANCHES.LAST_ISSUE_ENABLED_LANGS,
        syncTimestamp, enabledLanguages);
    });
  }

  @Override
  public void mergeTaintIssues(String branchName, List<ServerTaintIssue> taintsToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp,
    Set<SonarLanguage> enabledLanguages) {
    database.transaction(trx -> {
      if (!closedIssueKeysToDelete.isEmpty()) {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
            .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name()))
            .and(SERVER_FINDINGS.SERVER_KEY.in(closedIssueKeysToDelete))
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
      }
      batchMergeTaints(branchName, connectionId, sonarProjectKey, trx, taintsToMerge);
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_TAINT_SYNC_TS,
        SERVER_BRANCHES.LAST_TAINT_ENABLED_LANGS,
        syncTimestamp, enabledLanguages);
    });
  }

  @Override
  public void mergeHotspots(String branchName, List<ServerHotspot> hotspotsToMerge, Set<String> closedHotspotKeysToDelete, Instant syncTimestamp,
    Set<SonarLanguage> enabledLanguages) {
    database.transaction(trx -> {
      if (!closedHotspotKeysToDelete.isEmpty()) {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
            .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
            .and(SERVER_FINDINGS.SERVER_KEY.in(closedHotspotKeysToDelete))
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
      }
      batchMergeHotspots(branchName, connectionId, sonarProjectKey, trx, hotspotsToMerge);
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_HOTSPOT_SYNC_TS,
        SERVER_BRANCHES.LAST_HOTSPOT_ENABLED_LANGS,
        syncTimestamp, enabledLanguages);
    });
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String branchName) {
    return database.select(SERVER_BRANCHES.LAST_ISSUE_SYNC_TS)
      .from(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOptional()
      .map(Record1::value1)
      .map(ServerFindingRepository::toInstant);
  }

  @Override
  public Set<SonarLanguage> getLastIssueEnabledLanguages(String branchName) {
    return readLanguages(branchName, SERVER_BRANCHES.LAST_ISSUE_ENABLED_LANGS);
  }

  @Override
  public Set<SonarLanguage> getLastTaintEnabledLanguages(String branchName) {
    return readLanguages(branchName, SERVER_BRANCHES.LAST_TAINT_ENABLED_LANGS);
  }

  @Override
  public Set<SonarLanguage> getLastHotspotEnabledLanguages(String branchName) {
    return readLanguages(branchName, SERVER_BRANCHES.LAST_HOTSPOT_ENABLED_LANGS);
  }

  @Override
  public Optional<Instant> getLastTaintSyncTimestamp(String branchName) {
    return database.select(SERVER_BRANCHES.LAST_TAINT_SYNC_TS)
      .from(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOptional()
      .map(Record1::value1)
      .map(ServerFindingRepository::toInstant);
  }

  @Override
  public Optional<Instant> getLastHotspotSyncTimestamp(String branchName) {
    return database.select(SERVER_BRANCHES.LAST_HOTSPOT_SYNC_TS)
      .from(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOptional()
      .map(Record1::value1)
      .map(ServerFindingRepository::toInstant);
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt.toInstant(ZoneOffset.UTC);
  }

  private void upsertBranchMetadata(String branchName, TableField<ServerBranchesRecord, LocalDateTime> lastSyncField,
    TableField<ServerBranchesRecord, String[]> enabledLanguagesField, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    var lastSyncTime = LocalDateTime.ofInstant(syncTimestamp, ZoneOffset.UTC);
    var enabledLanguagesAsJson = mapper.serializeLanguages(enabledLanguages);

    database.insertInto(SERVER_BRANCHES, SERVER_BRANCHES.BRANCH_NAME, SERVER_BRANCHES.CONNECTION_ID, SERVER_BRANCHES.SONAR_PROJECT_KEY, lastSyncField, enabledLanguagesField)
      .values(branchName, connectionId, sonarProjectKey, lastSyncTime, enabledLanguagesAsJson)
      .onDuplicateKeyUpdate()
      .set(lastSyncField, lastSyncTime)
      .set(enabledLanguagesField, enabledLanguagesAsJson)
      .execute();
  }

  private Set<SonarLanguage> readLanguages(String branchName, TableField<ServerBranchesRecord, String[]> langsField) {
    var table = SERVER_BRANCHES;
    var rec = database.select(langsField)
      .from(table)
      .where(table.BRANCH_NAME.eq(branchName)
        .and(table.CONNECTION_ID.eq(connectionId))
        .and(table.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    if (rec == null) {
      return Set.of();
    }
    return mapper.deserializeLanguages(rec.value1());
  }

  @Override
  public List<ServerIssue<?>> load(String branchName, Path sqFilePath) {
    return database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
        .and(SERVER_FINDINGS.FILE_PATH.eq(sqFilePath.toString()))
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch().stream()
      .<ServerIssue<?>>map(mapper::adaptIssue)
      .toList();
  }

  @Override
  public void replaceAllTaintsOfBranch(String branchName, List<ServerTaintIssue> taintIssues, Set<SonarLanguage> enabledLanguages) {
    database.transaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      batchMergeTaints(branchName, connectionId, sonarProjectKey, trx, taintIssues);
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_TAINT_SYNC_TS,
        SERVER_BRANCHES.LAST_TAINT_ENABLED_LANGS,
        Instant.now(), enabledLanguages);
    });
  }

  @Override
  public Collection<ServerHotspot> loadHotspots(String branchName, Path serverFilePath) {
    return database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
        .and(SERVER_FINDINGS.FILE_PATH.eq(serverFilePath.toString()))
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch().stream()
      .map(mapper::adaptHotspot)
      .toList();
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String branchName) {
    return database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch().stream()
      .map(mapper::adaptTaint)
      .toList();
  }

  @Override
  public boolean updateIssue(String issueKey, Consumer<ServerIssue<?>> issueUpdater) {
    var rec = database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    if (rec == null) {
      return false;
    }
    var current = mapper.adaptIssue(rec);
    issueUpdater.accept(current);
    database.update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.RESOLVED, current.isResolved())
      .set(SERVER_FINDINGS.USER_SEVERITY, current.getUserSeverity() != null ? current.getUserSeverity().name() : null)
      .set(SERVER_FINDINGS.RULE_TYPE, current.getType() != null ? current.getType().name() : null)
      .set(SERVER_FINDINGS.IMPACTS, mapper.serializeImpacts(current.getImpacts()))
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .execute();
    return true;
  }

  @Override
  public ServerIssue<?> getIssue(String issueKey) {
    var rec = database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    return rec != null ? mapper.adaptIssue(rec) : null;
  }

  @Override
  public ServerHotspot getHotspot(String hotspotKey) {
    var rec = database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    return rec != null ? mapper.adaptHotspot(rec) : null;
  }

  @Override
  public Optional<ServerFinding> updateIssueResolutionStatus(String issueKey, boolean isTaintIssue, boolean isResolved) {
    var type = isTaintIssue ? ServerFindingType.TAINT.name() : ServerFindingType.ISSUE.name();
    database.update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.RESOLVED, isResolved)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(type)))
      .execute();
    var rec = database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(type)))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    return Optional.of(isTaintIssue ? mapper.adaptTaint(rec) : mapper.adaptIssue(rec));
  }

  @Override
  public Optional<ServerTaintIssue> updateTaintIssueBySonarServerKey(String sonarServerKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    var rec = database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(sonarServerKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name())))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var current = mapper.adaptTaint(rec);
    taintIssueUpdater.accept(current);
    database.update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.USER_SEVERITY, current.getSeverity().name())
      .set(SERVER_FINDINGS.RULE_TYPE, current.getType().name())
      .set(SERVER_FINDINGS.RESOLVED, current.isResolved())
      .set(SERVER_FINDINGS.IMPACTS, mapper.serializeImpacts(current.getImpacts()))
      .where(SERVER_FINDINGS.SERVER_KEY.eq(sonarServerKey))
      .execute();
    return Optional.of(current);
  }

  @Override
  public void insert(String branchName, ServerTaintIssue taintIssue) {
    database.transaction(trx -> batchMergeTaints(branchName, connectionId, sonarProjectKey, trx, List.of(taintIssue)));
  }

  @Override
  public void insert(String branchName, ServerHotspot hotspot) {
    database.transaction(trx -> batchMergeHotspots(branchName, connectionId, sonarProjectKey, trx, List.of(hotspot)));
  }

  @Override
  public Optional<UUID> deleteTaintIssueBySonarServerKey(String sonarServerKeyToDelete) {
    var rec = database.select(SERVER_FINDINGS.ID)
      .from(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(sonarServerKeyToDelete)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name())))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var idStr = rec.get(0, UUID.class);
    database.deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.ID.eq(idStr))
      .execute();
    return Optional.of(idStr);
  }

  @Override
  public void deleteHotspot(String hotspotKey) {
    database.deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name())))
      .execute();
  }

  @Override
  public void updateHotspot(String hotspotKey, Consumer<ServerHotspot> hotspotUpdater) {
    var rec = database.selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name())))
      .fetchOne();
    if (rec == null) {
      return;
    }
    var current = mapper.adaptHotspot(rec);
    hotspotUpdater.accept(current);
    database.update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.HOTSPOT_REVIEW_STATUS, current.getStatus().name())
      .set(SERVER_FINDINGS.ASSIGNEE, current.getAssignee())
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name())))
      .execute();
  }

  @Override
  public boolean containsIssue(String issueKey) {
    var cnt = database.selectCount().from(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.in(ServerFindingType.ISSUE.name(), ServerFindingType.TAINT.name())))
      .fetchOne();
    return cnt != null && cnt.value1() > 0;
  }

  @Override
  public void replaceAllDependencyRisksOfBranch(String branchName, List<ServerDependencyRisk> serverDependencyRisks) {
    var table = SERVER_DEPENDENCY_RISKS;

    database.transaction(trx -> {
      trx.dsl().deleteFrom(table)
        .where(table.BRANCH_NAME.eq(branchName)
          .and(table.CONNECTION_ID.eq(connectionId))
          .and(table.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      batchMergeDependencyRisks(branchName, connectionId, sonarProjectKey, trx, serverDependencyRisks);
    });
  }

  @Override
  public List<ServerDependencyRisk> loadDependencyRisks(String branchName) {
    var table = SERVER_DEPENDENCY_RISKS;

    return database.select(table.ID, table.TYPE, table.SEVERITY, table.SOFTWARE_QUALITY, table.STATUS,
      table.PACKAGE_NAME, table.PACKAGE_VERSION, table.VULNERABILITY_ID, table.CVSS_SCORE, table.TRANSITIONS)
      .from(table)
      .where(table.BRANCH_NAME.eq(branchName)
        .and(table.CONNECTION_ID.eq(connectionId))
        .and(table.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch()
      .stream()
      .map(rec -> {
        var id = rec.get(table.ID);
        var type = ServerDependencyRisk.Type.valueOf(rec.get(table.TYPE));
        var severity = ServerDependencyRisk.Severity.valueOf(rec.get(table.SEVERITY));
        var quality = ServerDependencyRisk.SoftwareQuality.valueOf(rec.get(table.SOFTWARE_QUALITY));
        var status = ServerDependencyRisk.Status.valueOf(rec.get(table.STATUS));
        var pkg = rec.get(table.PACKAGE_NAME);
        var ver = rec.get(table.PACKAGE_VERSION);
        var vuln = rec.get(table.VULNERABILITY_ID);
        var cvss = rec.get(table.CVSS_SCORE);
        var transitions = mapper.deserializeTransitions(rec.get(table.TRANSITIONS));
        return new ServerDependencyRisk(id, type, severity, quality, status, pkg, ver, vuln, cvss, transitions);
      })
      .toList();
  }

  @Override
  public void updateDependencyRiskStatus(UUID key, ServerDependencyRisk.Status newStatus, List<ServerDependencyRisk.Transition> transitions) {
    var table = Tables.SERVER_DEPENDENCY_RISKS;
    database.update(table)
      .set(table.STATUS, newStatus.name())
      .set(table.TRANSITIONS, mapper.serializeTransitions(transitions))
      .where(table.ID.eq(key))
      .execute();
  }

  @Override
  public void removeFindingsForConnection(String connectionId) {
    database.deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
      .execute();
    database.deleteFrom(SERVER_DEPENDENCY_RISKS)
      .where(SERVER_DEPENDENCY_RISKS.CONNECTION_ID.eq(connectionId))
      .execute();
    database.deleteFrom(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
      .execute();
  }

  private void batchMergeHotspots(String branchName, String connectionId, String sonarProjectKey, Configuration trx, Collection<ServerHotspot> hotspots) {
    trx.dsl().batchMerge(hotspots.stream()
      .map(hotspot -> mapper.serverHotspotToRecord(hotspot, branchName, connectionId, sonarProjectKey)).toList())
      .execute();
  }

  private void batchMergeIssues(String branchName, String connectionId, String sonarProjectKey, Configuration trx, Collection<ServerIssue<?>> issues) {
    trx.dsl().batchMerge(issues.stream()
      .map(issue -> mapper.serverIssueToRecord(issue, branchName, connectionId, sonarProjectKey)).toList())
      .execute();
  }

  private void batchMergeTaints(String branchName, String connectionId, String sonarProjectKey, Configuration trx, Collection<ServerTaintIssue> taints) {
    trx.dsl().batchMerge(taints.stream()
      .map(taint -> mapper.serverTaintToRecord(taint, branchName, connectionId, sonarProjectKey)).toList())
      .execute();
  }

  private void batchMergeDependencyRisks(String branchName, String connectionId, String sonarProjectKey, Configuration trx, Collection<ServerDependencyRisk> risks) {
    trx.dsl().batchMerge(risks.stream()
      .map(risk -> mapper.serverDependencyRiskToRecord(risk, branchName, connectionId, sonarProjectKey)).toList())
      .execute();
  }
}
