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
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jooq.Configuration;
import org.jooq.JSON;
import org.jooq.TableField;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.commons.storage.model.Tables;
import org.sonarsource.sonarlint.core.commons.storage.model.tables.records.ServerBranchesRecord;
import org.sonarsource.sonarlint.core.commons.storage.model.tables.records.ServerFindingsRecord;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerFinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FINDINGS;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_BRANCHES;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_DEPENDENCY_RISKS;

public class ServerFindingRepository implements ProjectServerIssueStore {

  private final JsonMapper mapper = new JsonMapper();
  private final SonarLintDatabase database;
  private final String connectionId;
  private final String sonarProjectKey;

  public ServerFindingRepository(SonarLintDatabase database, String connectionId, String sonarProjectKey) {
    this.database = database;
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
  }

  @Override
  public boolean wasEverUpdated() {
    // Limit to current connection/project scope
    var rec = database.dsl().selectCount().from(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId)
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    return rec != null && rec.value1() > 0;
  }

  @Override
  public void replaceAllIssuesOfBranch(String branchName, List<ServerIssue<?>> issues) {
    database.withTransaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      var serializer = new JsonMapper();
      issues.forEach(issue -> insertIssue(branchName, connectionId, sonarProjectKey, trx, issue, serializer));
    });
  }

  private static void insertIssue(String branchName, String connectionId, String sonarProjectKey, Configuration trx, ServerIssue<?> issue, JsonMapper serializer) {
    var impactsJson = serializer.serializeImpacts(issue.getImpacts());
    var creationDate = LocalDateTime.ofInstant(issue.getCreationDate(), ZoneId.systemDefault());
    trx.dsl()
      .deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.ID.eq(issue.getId()))
      .execute();
    var query = trx.dsl().insertInto(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.ID, issue.getId())
      .set(SERVER_FINDINGS.CONNECTION_ID, connectionId)
      .set(SERVER_FINDINGS.SONAR_PROJECT_KEY, sonarProjectKey)
      .set(SERVER_FINDINGS.SERVER_KEY, issue.getKey())
      .set(SERVER_FINDINGS.RULE_KEY, issue.getRuleKey())
      .set(SERVER_FINDINGS.MESSAGE, issue.getMessage())
      .set(SERVER_FINDINGS.FILE_PATH, issue.getFilePath().toString())
      .set(SERVER_FINDINGS.CREATION_DATE, creationDate)
      .set(SERVER_FINDINGS.USER_SEVERITY, issue.getUserSeverity() != null ? issue.getUserSeverity().name() : null)
      .set(SERVER_FINDINGS.RULE_TYPE, issue.getType() != null ? issue.getType().name() : null)
      .set(SERVER_FINDINGS.FINDING_TYPE, ServerFindingType.ISSUE.name())
      .set(SERVER_FINDINGS.BRANCH_NAME, branchName)
      // Resolution
      .set(SERVER_FINDINGS.RESOLVED, issue.isResolved())
      .set(SERVER_FINDINGS.ISSUE_RESOLUTION_STATUS, issue.getResolutionStatus() != null ? issue.getResolutionStatus().name() : null)
      // Impacts
      .set(SERVER_FINDINGS.IMPACTS, JSON.valueOf(impactsJson));
    if (issue instanceof LineLevelServerIssue lineIssue) {
      query.set(SERVER_FINDINGS.LINE, lineIssue.getLine())
        .set(SERVER_FINDINGS.LINE_HASH, lineIssue.getLineHash())
        .execute();
    } else if (issue instanceof RangeLevelServerIssue textRangeIssue) {
      query.set(SERVER_FINDINGS.START_LINE, textRangeIssue.getTextRange().getStartLine())
        .set(SERVER_FINDINGS.START_LINE_OFFSET, textRangeIssue.getTextRange().getStartLineOffset())
        .set(SERVER_FINDINGS.END_LINE, textRangeIssue.getTextRange().getEndLine())
        .set(SERVER_FINDINGS.END_LINE_OFFSET, textRangeIssue.getTextRange().getEndLineOffset())
        .set(SERVER_FINDINGS.TEXT_RANGE_HASH, textRangeIssue.getTextRange().getHash())
        .execute();
    } else {
      // File level issue
      query.execute();
    }
  }

  @Override
  public void replaceAllHotspotsOfBranch(String branchName, Collection<ServerHotspot> serverHotspots) {
    database.withTransaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      serverHotspots.forEach(hotspot -> insertHotspot(branchName, connectionId, sonarProjectKey, trx, hotspot));
    });
  }

  @Override
  public void replaceAllHotspotsOfFile(String branchName, Path serverFilePath, Collection<ServerHotspot> serverHotspots) {
    database.withTransaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FILE_PATH.eq(serverFilePath.toString()))
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey))
        )
        .execute();
      serverHotspots.forEach(hotspot -> insertHotspot(branchName, connectionId, sonarProjectKey, trx, hotspot));
    });
  }

  // we don't consume return value for now, probably should be void
  @Override
  public boolean changeHotspotStatus(String hotspotKey, HotspotReviewStatus newStatus) {
    database.dsl().update(SERVER_FINDINGS)
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
    database.withTransaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FILE_PATH.eq(serverFilePath.toString()))
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey))
        )
        .execute();
      var jsonMapper = new JsonMapper();
      issues.forEach(issue -> insertIssue(branchName, connectionId, sonarProjectKey, trx, issue, jsonMapper));
    });
  }

  @Override
  public void mergeIssues(String branchName, List<ServerIssue<?>> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    database.withTransaction(trx -> {
      if (!closedIssueKeysToDelete.isEmpty()) {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
            .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
            .and(SERVER_FINDINGS.SERVER_KEY.in(closedIssueKeysToDelete))
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
      }
      var serializer = new JsonMapper();
      issuesToMerge.forEach(issue -> {
        // remove any existing row with same id to mimic upsert behavior
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.ID.eq(issue.getId())
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
        insertIssue(branchName, connectionId, sonarProjectKey, trx, issue, serializer);
      });
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_ISSUE_SYNC_TS,
        SERVER_BRANCHES.LAST_ISSUE_ENABLED_LANGS,
        syncTimestamp, enabledLanguages);
    });
  }

  @Override
  public void mergeTaintIssues(String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp,
    Set<SonarLanguage> enabledLanguages) {
    database.withTransaction(trx -> {
      if (!closedIssueKeysToDelete.isEmpty()) {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
            .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name()))
            .and(SERVER_FINDINGS.SERVER_KEY.in(closedIssueKeysToDelete))
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
      }
      issuesToMerge.forEach(taint -> {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.SERVER_KEY.eq(taint.getSonarServerKey()))
          .execute();
        insertTaint(branchName, taint, trx);
      });
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_TAINT_SYNC_TS,
        SERVER_BRANCHES.LAST_TAINT_ENABLED_LANGS,
        syncTimestamp, enabledLanguages);
    });
  }

  @Override
  public void mergeHotspots(String branchName, List<ServerHotspot> hotspotsToMerge, Set<String> closedHotspotKeysToDelete, Instant syncTimestamp,
    Set<SonarLanguage> enabledLanguages) {
    database.withTransaction(trx -> {
      if (!closedHotspotKeysToDelete.isEmpty()) {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
            .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
            .and(SERVER_FINDINGS.SERVER_KEY.in(closedHotspotKeysToDelete))
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
      }
      hotspotsToMerge.forEach(hotspot -> {
        trx.dsl().deleteFrom(SERVER_FINDINGS)
          .where(SERVER_FINDINGS.ID.eq(hotspot.getId())
            .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
            .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
          .execute();
        insertHotspot(branchName, connectionId, sonarProjectKey, trx, hotspot);
      });
      upsertBranchMetadata(branchName,
        SERVER_BRANCHES.LAST_HOTSPOT_SYNC_TS,
        SERVER_BRANCHES.LAST_HOTSPOT_ENABLED_LANGS,
        syncTimestamp, enabledLanguages);
    });
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String branchName) {
    var rec = database.dsl().select(SERVER_BRANCHES.LAST_ISSUE_SYNC_TS)
      .from(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var ldt = rec.value1();
    return Optional.of(toInstant(ldt));
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
    var rec = database.dsl().select(SERVER_BRANCHES.LAST_TAINT_SYNC_TS)
      .from(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var ldt = rec.value1();
    return Optional.of(toInstant(ldt));
  }

  @Override
  public Optional<Instant> getLastHotspotSyncTimestamp(String branchName) {
    var rec = database.dsl().select(SERVER_BRANCHES.LAST_HOTSPOT_SYNC_TS)
      .from(SERVER_BRANCHES)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var ldt = rec.get(0, LocalDateTime.class);
    return Optional.of(toInstant(ldt));
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt.atZone(ZoneId.systemDefault()).toInstant();
  }

  private void upsertBranchMetadata(String branchName,
    TableField<ServerBranchesRecord, LocalDateTime> tsField,
    TableField<ServerBranchesRecord, String[]> langsField,
    Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {

    var ldt = LocalDateTime.ofInstant(syncTimestamp, ZoneId.systemDefault());
    var langsJson = mapper.serializeLanguages(enabledLanguages);

    int updated = database.dsl().update(SERVER_BRANCHES)
      .set(tsField, ldt)
      .set(langsField, langsJson)
      .where(SERVER_BRANCHES.BRANCH_NAME.eq(branchName)
        .and(SERVER_BRANCHES.CONNECTION_ID.eq(connectionId))
        .and(SERVER_BRANCHES.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .execute();
    if (updated == 0) {
      database.dsl().insertInto(SERVER_BRANCHES)
        .columns(SERVER_BRANCHES.BRANCH_NAME, SERVER_BRANCHES.CONNECTION_ID, SERVER_BRANCHES.SONAR_PROJECT_KEY, tsField, langsField)
        .values(branchName, connectionId, sonarProjectKey, ldt, langsJson)
        .execute();
    }
  }

  private Set<SonarLanguage> readLanguages(String branchName,
    TableField<ServerBranchesRecord, String[]> langsField) {
    var table = SERVER_BRANCHES;
    var rec = database.dsl().select(langsField)
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

  private ServerIssue<?> adaptIssue(ServerFindingsRecord rec) {
    var id = rec.getId();
    var serverKey = rec.getServerKey();
    var ruleKey = rec.getRuleKey();
    var message = rec.getMessage();
    var filePath = Path.of((rec.getFilePath()));
    var creationDate = toInstant(rec.getCreationDate());
    var userSeverity = rec.getUserSeverity() != null ? IssueSeverity.valueOf(rec.getUserSeverity()) : null;
    var type = rec.getRuleType() != null ? RuleType.valueOf(rec.getRuleType()) : RuleType.CODE_SMELL;
    var resolved = Boolean.TRUE.equals(rec.getResolved());
    var resolutionStatus = rec.getIssueResolutionStatus() != null ? IssueStatus.valueOf(rec.getIssueResolutionStatus()) : null;
    var impactsJson = rec.getImpacts();
    var impacts = mapper.deserializeImpacts(impactsJson);
    if (rec.getLine() != null) {
      return new LineLevelServerIssue(id, serverKey, resolved, resolutionStatus, ruleKey, message, rec.getLineHash(), filePath, creationDate, userSeverity, type,
        rec.getLine(), impacts);
    }
    if (rec.getStartLine() != null) {
      var textRangeWithHash = new TextRangeWithHash(rec.getStartLine(), rec.getStartLineOffset(), rec.getEndLine(), rec.getEndLineOffset(), rec.getTextRangeHash());
      return new RangeLevelServerIssue(id, serverKey, resolved, resolutionStatus, ruleKey, message, filePath, creationDate, userSeverity, type, textRangeWithHash, impacts);
    }
    return new FileLevelServerIssue(id, serverKey, resolved, resolutionStatus, ruleKey, message, filePath, creationDate, userSeverity, type, impacts);
  }

  private static ServerHotspot adaptHotspot(ServerFindingsRecord rec) {
    var id = rec.getId();
    var key = rec.getServerKey();
    var ruleKey = rec.getRuleKey();
    var message = rec.getMessage();
    var filePath = Path.of((rec.getFilePath()));
    var textRange = new TextRange(rec.getStartLine(), rec.getStartLineOffset(), rec.getEndLine(), rec.getEndLineOffset());
    var creationDate = toInstant(rec.getCreationDate());
    var status = HotspotReviewStatus.valueOf(rec.getHotspotReviewStatus());
    var prob = rec.getVulnerabilityProbability() != null ? VulnerabilityProbability.valueOf(rec.getVulnerabilityProbability()) : VulnerabilityProbability.MEDIUM;
    var assignee = rec.getAssignee();
    return new ServerHotspot(id, key, ruleKey, message, filePath, textRange, creationDate, status, prob, assignee);
  }

  private ServerTaintIssue adaptTaint(ServerFindingsRecord rec) {
    var id = rec.getId();
    var key = rec.getServerKey();
    var resolved = Boolean.TRUE.equals(rec.getResolved());
    var resolutionStatus = rec.getIssueResolutionStatus() != null ? IssueStatus.valueOf(rec.getIssueResolutionStatus()) : null;
    var ruleKey = rec.getRuleKey();
    var message = rec.getMessage();
    var filePath = Path.of((rec.getFilePath()));
    var creationDate = toInstant(rec.getCreationDate());
    var severity = rec.getUserSeverity() != null ? IssueSeverity.valueOf(rec.getUserSeverity()) : IssueSeverity.MAJOR;
    var type = rec.getRuleType() != null ? RuleType.valueOf(rec.getRuleType()) : RuleType.CODE_SMELL;
    TextRangeWithHash textRangeWithHash = null;
    if (rec.getStartLine() != null) {
      textRangeWithHash = new TextRangeWithHash(rec.getStartLine(), rec.getStartLineOffset(), rec.getEndLine(), rec.getEndLineOffset(), rec.getTextRangeHash());
    }
    var ruleDescCtx = rec.getRuleDescriptionContextKey();
    var cleanCodeAttr = rec.getCleanCodeAttribute() != null ? CleanCodeAttribute.valueOf(rec.getCleanCodeAttribute()) : null;
    var impactsJson = rec.getImpacts();
    var impacts = mapper.deserializeImpacts(impactsJson);
    return new ServerTaintIssue(id, key, resolved, resolutionStatus, ruleKey, message, filePath, creationDate,
      severity, type, textRangeWithHash, ruleDescCtx, cleanCodeAttr, impacts);
  }

  @Override
  public List<ServerIssue<?>> load(String branchName, Path sqFilePath) {
    return database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
        .and(SERVER_FINDINGS.FILE_PATH.eq(sqFilePath.toString()))
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch().stream()
      .<ServerIssue<?>>map(this::adaptIssue)
      .toList();
  }

  @Override
  public void replaceAllTaintsOfBranch(String branchName, List<ServerTaintIssue> taintIssues) {
    database.withTransaction(trx -> {
      trx.dsl().deleteFrom(SERVER_FINDINGS)
        .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
          .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name()))
          .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
          .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      taintIssues.forEach(t -> insertTaint(branchName, t, trx));
    });
  }

  @Override
  public Collection<ServerHotspot> loadHotspots(String branchName, Path serverFilePath) {
    return database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
        .and(SERVER_FINDINGS.FILE_PATH.eq(serverFilePath.toString()))
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch().stream()
      .map(ServerFindingRepository::adaptHotspot)
      .toList();
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String branchName) {
    return database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.BRANCH_NAME.eq(branchName)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetch().stream()
      .map(this::adaptTaint)
      .toList();
  }

  @Override
  public boolean updateIssue(String issueKey, Consumer<ServerIssue<?>> issueUpdater) {
    // For compatibility only; we will persist a subset of fields after applying the updater
    var rec = database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    if (rec == null) {
      return false;
    }
    var current = adaptIssue(rec);
    issueUpdater.accept(current);
    // Persist supported fields from the possibly-updated object
    database.dsl().update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.RESOLVED, current.isResolved())
      .set(SERVER_FINDINGS.USER_SEVERITY, current.getUserSeverity() != null ? current.getUserSeverity().name() : null)
      .set(SERVER_FINDINGS.RULE_TYPE, current.getType() != null ? current.getType().name() : null)
      // Impacts JSON is kept as-is until full serialization support; do not update here
      .where(SERVER_FINDINGS.ID.eq(rec.getId()))
      .execute();
    return true;
  }

  @Override
  public ServerIssue<?> getIssue(String issueKey) {
    var rec = database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.ISSUE.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    return rec != null ? adaptIssue(rec) : null;
  }

  @Override
  public ServerHotspot getHotspot(String hotspotKey) {
    var rec = database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name()))
        .and(SERVER_FINDINGS.CONNECTION_ID.eq(connectionId))
        .and(SERVER_FINDINGS.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
      .fetchOne();
    return rec != null ? adaptHotspot(rec) : null;
  }

  @Override
  public Optional<ServerFinding> updateIssueResolutionStatus(String issueKey, boolean isTaintIssue, boolean isResolved) {
    var type = isTaintIssue ? ServerFindingType.TAINT.name() : ServerFindingType.ISSUE.name();
    database.dsl().update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.RESOLVED, isResolved)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(type)))
      .execute();
    var rec = database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(type)))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    return Optional.of(isTaintIssue ? adaptTaint(rec) : adaptIssue(rec));
  }

  @Override
  public Optional<ServerTaintIssue> updateTaintIssueBySonarServerKey(String sonarServerKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    var rec = database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(sonarServerKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name())))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var current = adaptTaint(rec);
    taintIssueUpdater.accept(current);
    // persist a couple of fields if modified through separate APIs elsewhere; here we keep record unchanged
    return Optional.of(current);
  }

  @Override
  public void insert(String branchName, ServerTaintIssue taintIssue) {
    database.withTransaction(trx -> insertTaint(branchName, taintIssue, trx));
  }

  private void insertTaint(String branchName, ServerTaintIssue taintIssue, Configuration trx) {
    trx.dsl().deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(taintIssue.getSonarServerKey()))
      .execute();
    var creationDate = LocalDateTime.ofInstant(taintIssue.getCreationDate(), ZoneId.systemDefault());
    var impactsJson = mapper.serializeImpacts(taintIssue.getImpacts());
    var flowsJson = mapper.serializeFlows(taintIssue);
    var cleanCodeAttribute = taintIssue.getCleanCodeAttribute();
    var insert = trx.dsl().insertInto(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.ID, taintIssue.getId())
      .set(SERVER_FINDINGS.CONNECTION_ID, connectionId)
      .set(SERVER_FINDINGS.SONAR_PROJECT_KEY, sonarProjectKey)
      .set(SERVER_FINDINGS.SERVER_KEY, taintIssue.getSonarServerKey())
      .set(SERVER_FINDINGS.RULE_KEY, taintIssue.getRuleKey())
      .set(SERVER_FINDINGS.MESSAGE, taintIssue.getMessage())
      .set(SERVER_FINDINGS.FILE_PATH, taintIssue.getFilePath().toString())
      .set(SERVER_FINDINGS.CREATION_DATE, creationDate)
      .set(SERVER_FINDINGS.USER_SEVERITY, taintIssue.getSeverity() != null ? taintIssue.getSeverity().name() : null)
      .set(SERVER_FINDINGS.RULE_TYPE, taintIssue.getType() != null ? taintIssue.getType().name() : null)
      .set(SERVER_FINDINGS.RULE_DESCRIPTION_CONTEXT_KEY, taintIssue.getRuleDescriptionContextKey())
      .set(SERVER_FINDINGS.CLEAN_CODE_ATTRIBUTE, cleanCodeAttribute.map(Enum::name).orElse(null))
      .set(SERVER_FINDINGS.FINDING_TYPE, ServerFindingType.TAINT.name())
      .set(SERVER_FINDINGS.BRANCH_NAME, branchName)
      .set(SERVER_FINDINGS.RESOLVED, taintIssue.isResolved())
      .set(SERVER_FINDINGS.ISSUE_RESOLUTION_STATUS, taintIssue.getResolutionStatus() != null ? taintIssue.getResolutionStatus().name() : null)
      .set(SERVER_FINDINGS.IMPACTS, JSON.valueOf(impactsJson))
      .set(SERVER_FINDINGS.FLOWS, JSON.valueOf(flowsJson));
    if (taintIssue.getTextRange() != null) {
      insert = insert.set(SERVER_FINDINGS.START_LINE, taintIssue.getTextRange().getStartLine())
        .set(SERVER_FINDINGS.START_LINE_OFFSET, taintIssue.getTextRange().getStartLineOffset())
        .set(SERVER_FINDINGS.END_LINE, taintIssue.getTextRange().getEndLine())
        .set(SERVER_FINDINGS.END_LINE_OFFSET, taintIssue.getTextRange().getEndLineOffset())
        .set(SERVER_FINDINGS.TEXT_RANGE_HASH, taintIssue.getTextRange().getHash());
    }
    insert.execute();
  }

  @Override
  public void insert(String branchName, ServerHotspot hotspot) {
    database.withTransaction(trx -> insertHotspot(branchName, connectionId, sonarProjectKey, trx, hotspot));
  }

  @Override
  public Optional<UUID> deleteTaintIssueBySonarServerKey(String sonarServerKeyToDelete) {
    var rec = database.dsl().select(SERVER_FINDINGS.ID)
      .from(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(sonarServerKeyToDelete)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.TAINT.name())))
      .fetchOne();
    if (rec == null) {
      return Optional.empty();
    }
    var idStr = rec.get(0, UUID.class);
    database.dsl().deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.ID.eq(idStr))
      .execute();
    return Optional.of(idStr);
  }

  @Override
  public void deleteHotspot(String hotspotKey) {
    database.dsl().deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name())))
      .execute();
  }

  @Override
  public void close() {
    database.shutdown();
  }

  @Override
  public void updateHotspot(String hotspotKey, Consumer<ServerHotspot> hotspotUpdater) {
    var rec = database.dsl().selectFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name())))
      .fetchOne();
    if (rec == null) {
      return;
    }
    var current = adaptHotspot(rec);
    hotspotUpdater.accept(current);
    database.dsl().update(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.HOTSPOT_REVIEW_STATUS, current.getStatus().name())
      .set(SERVER_FINDINGS.ASSIGNEE, current.getAssignee())
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspotKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.eq(ServerFindingType.HOTSPOT.name())))
      .execute();
  }

  @Override
  public boolean containsIssue(String issueKey) {
    var cnt = database.dsl().selectCount().from(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(issueKey)
        .and(SERVER_FINDINGS.FINDING_TYPE.in(ServerFindingType.ISSUE.name(), ServerFindingType.TAINT.name())))
      .fetchOne();
    return cnt != null && cnt.value1() > 0;
  }

  @Override
  public void replaceAllDependencyRisksOfBranch(String branchName, List<ServerDependencyRisk> serverDependencyRisks) {
    var table = SERVER_DEPENDENCY_RISKS;

    database.withTransaction(trx -> {
      trx.dsl().deleteFrom(table)
        .where(table.BRANCH_NAME.eq(branchName)
          .and(table.CONNECTION_ID.eq(connectionId))
          .and(table.SONAR_PROJECT_KEY.eq(sonarProjectKey)))
        .execute();
      serverDependencyRisks.forEach(dependencyRisk -> {
        trx.dsl().deleteFrom(table)
          .where(table.ID.eq(dependencyRisk.key()))
          .execute();
        var transitionsJson = mapper.serializeTransitions(dependencyRisk.transitions());
        trx.dsl().insertInto(table)
          .set(table.ID, dependencyRisk.key())
          .set(table.CONNECTION_ID, connectionId)
          .set(table.SONAR_PROJECT_KEY, sonarProjectKey)
          .set(table.BRANCH_NAME, branchName)
          .set(table.TYPE, dependencyRisk.type().name())
          .set(table.SEVERITY, dependencyRisk.severity().name())
          .set(table.SOFTWARE_QUALITY, dependencyRisk.quality().name())
          .set(table.STATUS, dependencyRisk.status().name())
          .set(table.PACKAGE_NAME, dependencyRisk.packageName())
          .set(table.PACKAGE_VERSION, dependencyRisk.packageVersion())
          .set(table.VULNERABILITY_ID, dependencyRisk.vulnerabilityId())
          .set(table.CVSS_SCORE, dependencyRisk.cvssScore())
          .set(table.TRANSITIONS, transitionsJson)
          .execute();
      });
    });
  }

  @Override
  public List<ServerDependencyRisk> loadDependencyRisks(String branchName) {
    var table = SERVER_DEPENDENCY_RISKS;

    return database.dsl().select(table.ID, table.TYPE, table.SEVERITY, table.SOFTWARE_QUALITY, table.STATUS,
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
    database.dsl().update(table)
      .set(table.STATUS, newStatus.name())
      .set(table.TRANSITIONS, mapper.serializeTransitions(transitions))
      .where(table.ID.eq(key))
      .execute();
  }

  private static void insertHotspot(String branchName, String connectionId, String sonarProjectKey, Configuration trx, ServerHotspot hotspot) {
    var creationDate = LocalDateTime.ofInstant(hotspot.getCreationDate(), ZoneId.systemDefault());
    trx.dsl().deleteFrom(SERVER_FINDINGS)
      .where(SERVER_FINDINGS.SERVER_KEY.eq(hotspot.getKey()))
      .execute();
    trx.dsl().insertInto(SERVER_FINDINGS)
      .set(SERVER_FINDINGS.ID, hotspot.getId())
      .set(SERVER_FINDINGS.CONNECTION_ID, connectionId)
      .set(SERVER_FINDINGS.SONAR_PROJECT_KEY, sonarProjectKey)
      .set(SERVER_FINDINGS.SERVER_KEY, hotspot.getKey())
      .set(SERVER_FINDINGS.RULE_KEY, hotspot.getRuleKey())
      .set(SERVER_FINDINGS.MESSAGE, hotspot.getMessage())
      .set(SERVER_FINDINGS.FILE_PATH, hotspot.getFilePath().toString())
      .set(SERVER_FINDINGS.CREATION_DATE, creationDate)
      .set(SERVER_FINDINGS.VULNERABILITY_PROBABILITY, hotspot.getVulnerabilityProbability().name())
      .set(SERVER_FINDINGS.ASSIGNEE, hotspot.getAssignee())
      .set(SERVER_FINDINGS.FINDING_TYPE, ServerFindingType.HOTSPOT.name())
      .set(SERVER_FINDINGS.BRANCH_NAME, branchName)
      // Resolution
      .set(SERVER_FINDINGS.HOTSPOT_REVIEW_STATUS, hotspot.getStatus().name())
      // Text range
      .set(SERVER_FINDINGS.START_LINE, hotspot.getTextRange().getStartLine())
        .set(SERVER_FINDINGS.START_LINE_OFFSET, hotspot.getTextRange().getStartLineOffset())
        .set(SERVER_FINDINGS.END_LINE, hotspot.getTextRange().getEndLine())
        .set(SERVER_FINDINGS.END_LINE_OFFSET, hotspot.getTextRange().getEndLineOffset())
      .execute();
  }
}
