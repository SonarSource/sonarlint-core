/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.entitystore.StoreTransaction;
import jetbrains.exodus.entitystore.StoreTransactionalExecutable;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.util.CompressBackupUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.Flow;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.ServerIssueLocation;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.Location;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.TextRange;

import static java.util.Objects.requireNonNull;

public class XodusServerIssueStore implements ProjectServerIssueStore {

  static final int CURRENT_SCHEMA_VERSION = 1;

  private static final String BACKUP_TAR_GZ = "backup.tar.gz";

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String BRANCH_ENTITY_TYPE = "Branch";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String ISSUE_ENTITY_TYPE = "Issue";
  private static final String TAINT_ISSUE_ENTITY_TYPE = "TaintIssue";
  private static final String HOTSPOT_ENTITY_TYPE = "Hotspot";
  private static final String SCHEMA_ENTITY_TYPE = "Schema";

  private static final String BRANCH_TO_FILES_LINK_NAME = "files";
  private static final String BRANCH_TO_TAINT_ISSUES_LINK_NAME = "taintIssues";
  private static final String TAINT_ISSUE_TO_BRANCH_LINK_NAME = "branch";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String FILE_TO_TAINT_ISSUES_LINK_NAME = "taintIssues";
  private static final String FILE_TO_HOTSPOTS_LINK_NAME = "hotspots";
  private static final String ISSUE_TO_FILE_LINK_NAME = "file";

  private static final String START_LINE_PROPERTY_NAME = "startLine";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endLine";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String KEY_PROPERTY_NAME = "key";
  private static final String RESOLVED_PROPERTY_NAME = "resolved";
  private static final String RULE_KEY_PROPERTY_NAME = "ruleKey";
  private static final String LINE_HASH_PROPERTY_NAME = "lineHash";
  private static final String RANGE_HASH_PROPERTY_NAME = "rangeHash";
  private static final String CREATION_DATE_PROPERTY_NAME = "creationDate";
  private static final String USER_SEVERITY_PROPERTY_NAME = "userSeverity";
  private static final String SEVERITY_PROPERTY_NAME = "severity";
  private static final String VULNERABILITY_PROBABILITY_PROPERTY_NAME = "vulnerabilityProbability";
  private static final String TYPE_PROPERTY_NAME = "type";
  private static final String PATH_PROPERTY_NAME = "path";
  private static final String NAME_PROPERTY_NAME = "name";
  private static final String LAST_ISSUE_SYNC_PROPERTY_NAME = "lastIssueSync";
  private static final String LAST_TAINT_SYNC_PROPERTY_NAME = "lastTaintSync";
  private static final String VERSION_PROPERTY_NAME = "version";

  private static final String MESSAGE_BLOB_NAME = "message";
  private static final String FLOWS_BLOB_NAME = "flows";
  private static final String RULE_DESCRIPTION_CONTEXT_KEY_PROPERTY_NAME = "ruleDescriptionContextKey";
  private final PersistentEntityStore entityStore;

  private final Path backupFile;

  private final Path xodusDbDir;

  public XodusServerIssueStore(Path backupDir, Path workDir) throws IOException {
    this(backupDir, workDir, XodusServerIssueStore::checkCurrentSchemaVersion);
  }

  XodusServerIssueStore(Path backupDir, Path workDir, StoreTransactionalExecutable afterInit) throws IOException {
    xodusDbDir = Files.createTempDirectory(workDir, "xodus-issue-store");
    backupFile = backupDir.resolve(BACKUP_TAR_GZ);
    if (Files.isRegularFile(backupFile)) {
      LOG.debug("Restoring previous server issue database from {}", backupFile);
      try {
        TarGzUtils.extractTarGz(backupFile, xodusDbDir);
      } catch (Exception e) {
        LOG.error("Unable to restore backup {}", backupFile);
      }
    }
    LOG.debug("Starting server issue database from {}", xodusDbDir);
    this.entityStore = buildEntityStore();
    entityStore.executeInTransaction(txn -> {
      entityStore.registerCustomPropertyType(txn, IssueSeverity.class, new IssueSeverityBinding());
      entityStore.registerCustomPropertyType(txn, RuleType.class, new IssueTypeBinding());
      entityStore.registerCustomPropertyType(txn, Instant.class, new InstantBinding());
    });

    entityStore.executeInExclusiveTransaction(afterInit);
  }

  private PersistentEntityStore buildEntityStore() {
    var environment = Environments.newInstance(xodusDbDir.toAbsolutePath().toFile(), new EnvironmentConfig()
      .setLogAllowRemote(true)
      .setLogAllowRemovable(true)
      .setLogAllowRamDisk(true));
    var entityStoreImpl = PersistentEntityStores.newInstance(environment);
    entityStoreImpl.setCloseEnvironment(true);
    return entityStoreImpl;
  }

  private static ServerIssue adapt(Entity storedIssue) {
    var filePath = (String) requireNonNull(storedIssue.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var startLine = storedIssue.getProperty(START_LINE_PROPERTY_NAME);
    var key = (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME));
    var resolved = Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME));
    var ruleKey = (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME));
    var msg = requireNonNull(storedIssue.getBlobString(MESSAGE_BLOB_NAME));
    var creationDate = (Instant) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME));
    var userSeverity = (IssueSeverity) storedIssue.getProperty(USER_SEVERITY_PROPERTY_NAME);
    var type = (RuleType) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME));
    if (startLine == null) {
      return new FileLevelServerIssue(key, resolved, ruleKey, msg, filePath, creationDate, userSeverity, type);
    } else {
      var rangeHash = storedIssue.getBlobString(RANGE_HASH_PROPERTY_NAME);
      if (rangeHash != null) {
        var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
        var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
        var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
        var textRange = new TextRangeWithHash((int) startLine, startLineOffset, endLine, endLineOffset, rangeHash);
        return new RangeLevelServerIssue(
          key,
          resolved,
          ruleKey,
          msg,
          filePath,
          creationDate,
          userSeverity,
          type,
          textRange);
      } else {
        return new LineLevelServerIssue(
          key,
          resolved,
          ruleKey,
          msg,
          storedIssue.getBlobString(LINE_HASH_PROPERTY_NAME),
          filePath,
          creationDate,
          userSeverity,
          type,
          (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME));
      }
    }
  }

  private static ServerTaintIssue adaptTaint(Entity storedIssue) {
    var filePath = (String) requireNonNull(storedIssue.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var startLine = (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME);
    TextRangeWithHash textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      var hash = storedIssue.getBlobString(RANGE_HASH_PROPERTY_NAME);
      textRange = new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, hash);
    }
    return new ServerTaintIssue(
      (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
      Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME)),
      requireNonNull(storedIssue.getBlobString(MESSAGE_BLOB_NAME)),
      filePath,
      (Instant) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME)),
      (IssueSeverity) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)),
      (RuleType) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)),
      textRange, (String) storedIssue.getProperty(RULE_DESCRIPTION_CONTEXT_KEY_PROPERTY_NAME))
        .setFlows(readFlows(storedIssue.getBlob(FLOWS_BLOB_NAME)));
  }

  private static ServerHotspot adaptHotspot(Entity storedHotspot) {
    var filePath = (String) requireNonNull(storedHotspot.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var startLine = (Integer) storedHotspot.getProperty(START_LINE_PROPERTY_NAME);
    var startLineOffset = (Integer) storedHotspot.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
    var endLine = (Integer) storedHotspot.getProperty(END_LINE_PROPERTY_NAME);
    var endLineOffset = (Integer) storedHotspot.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
    var textRange = new org.sonarsource.sonarlint.core.commons.TextRange(startLine, startLineOffset, endLine, endLineOffset);
    var vulnerabilityProbability = VulnerabilityProbability.valueOf((String) storedHotspot.getProperty(VULNERABILITY_PROBABILITY_PROPERTY_NAME));
    return new ServerHotspot(
      (String) requireNonNull(storedHotspot.getProperty(KEY_PROPERTY_NAME)),
      (String) requireNonNull(storedHotspot.getProperty(RULE_KEY_PROPERTY_NAME)),
      requireNonNull(storedHotspot.getBlobString(MESSAGE_BLOB_NAME)),
      filePath,
      textRange,
      (Instant) requireNonNull(storedHotspot.getProperty(CREATION_DATE_PROPERTY_NAME)),
      Boolean.TRUE.equals(storedHotspot.getProperty(RESOLVED_PROPERTY_NAME)), vulnerabilityProbability);
  }

  private static List<Flow> readFlows(@Nullable InputStream blob) {
    if (blob == null) {
      return List.of();
    }
    return ProtobufUtil.readMessages(blob, Sonarlint.Flow.parser()).stream().map(XodusServerIssueStore::toJavaFlow).collect(Collectors.toList());
  }

  @Override
  public List<ServerIssue> load(String branchName, String filePath) {
    return loadIssue(branchName, filePath, FILE_TO_ISSUES_LINK_NAME, XodusServerIssueStore::adapt);
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String branchName, String filePath) {
    return loadIssue(branchName, filePath, FILE_TO_TAINT_ISSUES_LINK_NAME, XodusServerIssueStore::adaptTaint);
  }

  @Override
  public Collection<ServerHotspot> loadHotspots(String branchName, String serverFilePath) {
    return loadIssue(branchName, serverFilePath, FILE_TO_HOTSPOTS_LINK_NAME, XodusServerIssueStore::adaptHotspot);
  }

  private <G> List<G> loadIssue(String branchName, String filePath, String linkName, Function<Entity, G> adapter) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> branch.getLinks(BRANCH_TO_FILES_LINK_NAME))
      .flatMap(files -> findUniqueAmong(files, PATH_PROPERTY_NAME, filePath))
      .map(fileToLoad -> fileToLoad.getLinks(linkName))
      .map(issueEntities -> StreamSupport.stream(issueEntities.spliterator(), false)
        .map(adapter)
        .collect(Collectors.toList()))
      .orElseGet(Collections::emptyList));
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> StreamSupport.stream(branch.getLinks(BRANCH_TO_TAINT_ISSUES_LINK_NAME).spliterator(), false)
        .map(XodusServerIssueStore::adaptTaint)
        .collect(Collectors.toList()))
      .orElseGet(Collections::emptyList));
  }

  @Override
  public void replaceAllIssuesOfFile(String branchName, String serverFilePath, List<ServerIssue> issues) {
    timed("Wrote " + issues.size() + " issues in store", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);
      replaceAllIssuesOfFile(issues, txn, fileEntity);
    }));
  }

  @Override
  public void mergeIssues(String branchName, List<ServerIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    var issuesByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
    timed("Merged " + issuesToMerge.size() + " issues in store. Closed " + closedIssueKeysToDelete.size() + ".", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      issuesByFilePath.forEach((filePath, issues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        issues.forEach(issue -> updateOrCreateIssue(fileEntity, issue, txn));
        txn.flush();
      });
      closedIssueKeysToDelete.forEach(issueKey -> remove(issueKey, txn));
      branch.setProperty(LAST_ISSUE_SYNC_PROPERTY_NAME, syncTimestamp);
    }));
  }

  @Override
  public void mergeTaintIssues(String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    var issuesByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerTaintIssue::getFilePath));
    timed("Merged " + issuesToMerge.size() + " taint issues in store. Closed " + closedIssueKeysToDelete.size() + ".", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      issuesByFilePath.forEach((filePath, issues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        issues.forEach(issue -> updateOrCreateTaintIssue(branch, fileEntity, issue, txn));
        txn.flush();
      });
      closedIssueKeysToDelete.forEach(issueKey -> removeTaint(issueKey, txn));
      branch.setProperty(LAST_TAINT_SYNC_PROPERTY_NAME, syncTimestamp);
    }));
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (Instant) branch.getProperty(LAST_ISSUE_SYNC_PROPERTY_NAME)));
  }

  @Override
  public Optional<Instant> getLastTaintSyncTimestamp(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (Instant) branch.getProperty(LAST_TAINT_SYNC_PROPERTY_NAME)));
  }

  @Override
  public void replaceAllIssuesOfBranch(String branchName, List<ServerIssue> issues) {
    var issuesByFile = issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
    timed("Wrote " + issues.size() + " issues in store", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      branch.getLinks(BRANCH_TO_FILES_LINK_NAME).forEach(fileEntity -> {
        var entityFilePath = fileEntity.getProperty(PATH_PROPERTY_NAME);
        if (!issuesByFile.containsKey(entityFilePath)) {
          deleteAllIssuesOfFile(txn, fileEntity);
        }
      });
      txn.flush();
      issuesByFile.forEach((filePath, fileIssues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        replaceAllIssuesOfFile(fileIssues, txn, fileEntity);
        txn.flush();
      });
    }));
  }

  @Override
  public void replaceAllHotspotsOfBranch(String branchName, Collection<ServerHotspot> serverHotspots) {
    var hotspotsByFile = serverHotspots.stream().collect(Collectors.groupingBy(ServerHotspot::getFilePath));
    timed("Wrote " + serverHotspots.size() + " hotspots in store", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      branch.getLinks(BRANCH_TO_FILES_LINK_NAME).forEach(fileEntity -> {
        var entityFilePath = fileEntity.getProperty(PATH_PROPERTY_NAME);
        if (!hotspotsByFile.containsKey(entityFilePath)) {
          deleteAllHotspotsOfFile(txn, fileEntity);
        }
      });
      txn.flush();
      hotspotsByFile.forEach((filePath, fileIssues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        replaceAllHotspotsOfFile(fileIssues, txn, fileEntity);
        txn.flush();
      });
    }));
  }

  @Override
  public void replaceAllHotspotsOfFile(String branchName, String serverFilePath, Collection<ServerHotspot> serverHotspots) {
    timed("Wrote " + serverHotspots.size() + " hotspots in store", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);
      replaceAllHotspotsOfFile(serverHotspots, txn, fileEntity);
    }));
  }

  private static void replaceAllHotspotsOfFile(Collection<ServerHotspot> hotspots, @NotNull StoreTransaction txn, Entity fileEntity) {
    fileEntity.getLinks(FILE_TO_HOTSPOTS_LINK_NAME).forEach(Entity::delete);
    fileEntity.deleteLinks(FILE_TO_HOTSPOTS_LINK_NAME);

    hotspots.forEach(hotspot -> updateOrCreateHotspot(fileEntity, hotspot, txn));
  }

  private static void updateOrCreateHotspot(Entity fileEntity, ServerHotspot hotspot, StoreTransaction transaction) {
    var hotspotEntity = updateOrCreateIssueCommon(fileEntity, hotspot.getKey(), transaction, HOTSPOT_ENTITY_TYPE, FILE_TO_HOTSPOTS_LINK_NAME);
    updateHotspotEntity(hotspotEntity, hotspot);
  }

  private static void updateHotspotEntity(Entity issueEntity, ServerHotspot hotspot) {
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, hotspot.getRuleKey());
    issueEntity.setBlobString(MESSAGE_BLOB_NAME, hotspot.getMessage());
    var textRange = hotspot.getTextRange();
    issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
    issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
    issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
    issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, hotspot.getCreationDate());
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, hotspot.isResolved());
    issueEntity.setProperty(VULNERABILITY_PROBABILITY_PROPERTY_NAME, hotspot.getVulnerabilityProbability().toString());
  }

  private static void deleteAllHotspotsOfFile(@NotNull StoreTransaction txn, Entity fileEntity) {
    replaceAllHotspotsOfFile(List.of(), txn, fileEntity);
  }

  private static void deleteAllIssuesOfFile(@NotNull StoreTransaction txn, Entity fileEntity) {
    replaceAllIssuesOfFile(List.of(), txn, fileEntity);
  }

  private static void timed(String msg, Runnable transaction) {
    var startTime = Instant.now();
    transaction.run();
    var duration = Duration.between(startTime, Instant.now());
    LOG.debug("{} | took {}ms", msg, duration.toMillis());
  }

  private static void replaceAllIssuesOfFile(List<ServerIssue> issues, @NotNull StoreTransaction txn, Entity fileEntity) {
    fileEntity.getLinks(FILE_TO_ISSUES_LINK_NAME).forEach(Entity::delete);
    fileEntity.deleteLinks(FILE_TO_ISSUES_LINK_NAME);

    issues.forEach(issue -> updateOrCreateIssue(fileEntity, issue, txn));
  }

  @Override
  public void replaceAllTaintOfFile(String branchName, String serverFilePath, List<ServerTaintIssue> issues) {
    timed("Wrote " + issues.size() + " taint issues in store", () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);

      fileEntity.getLinks(FILE_TO_TAINT_ISSUES_LINK_NAME).forEach(Entity::delete);
      fileEntity.deleteLinks(FILE_TO_TAINT_ISSUES_LINK_NAME);

      issues.forEach(issue -> updateOrCreateTaintIssue(branch, fileEntity, issue, txn));
    }));
  }

  private static Entity getOrCreateBranch(String branchName, StoreTransaction txn) {
    return findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .orElseGet(() -> {
        var branch = txn.newEntity(BRANCH_ENTITY_TYPE);
        branch.setProperty(NAME_PROPERTY_NAME, branchName);
        return branch;
      });
  }

  private static Entity getOrCreateFile(Entity branchEntity, String filePath, StoreTransaction txn) {
    return findUniqueAmong(branchEntity.getLinks(BRANCH_TO_FILES_LINK_NAME), PATH_PROPERTY_NAME, filePath)
      .orElseGet(() -> {
        var file = txn.newEntity(FILE_ENTITY_TYPE);
        file.setProperty(PATH_PROPERTY_NAME, filePath);
        branchEntity.addLink(BRANCH_TO_FILES_LINK_NAME, file);
        return file;
      });
  }

  private static void updateOrCreateIssue(Entity fileEntity, ServerIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getKey(), transaction, ISSUE_ENTITY_TYPE, FILE_TO_ISSUES_LINK_NAME);
    updateIssueEntity(issueEntity, issue);
  }

  private static void updateIssueEntity(Entity issueEntity, ServerIssue issue) {
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, issue.isResolved());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.getRuleKey());
    issueEntity.setBlobString(MESSAGE_BLOB_NAME, issue.getMessage());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, issue.getCreationDate());
    var userSeverity = issue.getUserSeverity();
    if (userSeverity != null) {
      issueEntity.setProperty(USER_SEVERITY_PROPERTY_NAME, userSeverity);
    }
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.getType());
    if (issue instanceof LineLevelServerIssue) {
      var lineIssue = (LineLevelServerIssue) issue;
      issueEntity.setBlobString(LINE_HASH_PROPERTY_NAME, lineIssue.getLineHash());
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, lineIssue.getLine());
    } else if (issue instanceof RangeLevelServerIssue) {
      var rangeIssue = (RangeLevelServerIssue) issue;
      var textRange = rangeIssue.getTextRange();
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      issueEntity.setBlobString(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
    }
  }

  private static void updateOrCreateTaintIssue(Entity branchEntity, Entity fileEntity, ServerTaintIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getKey(), transaction, TAINT_ISSUE_ENTITY_TYPE, FILE_TO_TAINT_ISSUES_LINK_NAME);
    updateTaintIssueEntity(issue, issueEntity);
    branchEntity.addLink(BRANCH_TO_TAINT_ISSUES_LINK_NAME, issueEntity);
    issueEntity.setLink(TAINT_ISSUE_TO_BRANCH_LINK_NAME, branchEntity);
  }

  private static void updateTaintIssueEntity(ServerTaintIssue issue, Entity issueEntity) {
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, issue.isResolved());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.getRuleKey());
    issueEntity.setBlobString(MESSAGE_BLOB_NAME, issue.getMessage());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, issue.getCreationDate());
    issueEntity.setProperty(SEVERITY_PROPERTY_NAME, issue.getSeverity());
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.getType());
    var textRange = issue.getTextRange();
    if (textRange != null) {
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      issueEntity.setBlobString(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
    }
    issueEntity.setBlob(FLOWS_BLOB_NAME, toProtoFlow(issue.getFlows()));
    var ruleDescriptionContextKey = issue.getRuleDescriptionContextKey();
    if (ruleDescriptionContextKey != null) {
      issueEntity.setProperty(RULE_DESCRIPTION_CONTEXT_KEY_PROPERTY_NAME, ruleDescriptionContextKey);
    }
  }

  private static InputStream toProtoFlow(List<Flow> flows) {
    var buffer = new ByteArrayOutputStream();
    ProtobufUtil.writeMessages(buffer, flows.stream().map(XodusServerIssueStore::toProtoFlow).collect(Collectors.toList()));
    return new ByteArrayInputStream(buffer.toByteArray());
  }

  private static Entity updateOrCreateIssueCommon(Entity fileEntity, String issueKey, StoreTransaction transaction, String entityType, String fileToIssueLink) {
    var issueEntity = findUnique(transaction, entityType, KEY_PROPERTY_NAME, issueKey)
      .orElseGet(() -> transaction.newEntity(entityType));
    var oldFileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
    if (oldFileEntity != null && !fileEntity.equals(oldFileEntity)) {
      // issue might have moved file
      oldFileEntity.deleteLink(fileToIssueLink, issueEntity);
    }
    issueEntity.setLink(ISSUE_TO_FILE_LINK_NAME, fileEntity);
    fileEntity.addLink(fileToIssueLink, issueEntity);
    issueEntity.setProperty(KEY_PROPERTY_NAME, issueKey);
    return issueEntity;
  }

  private static Optional<Entity> findUnique(StoreTransaction transaction, String entityType, String propertyName, String caseSensitivePropertyValue) {
    // the find is case-insensitive but we need an exact match
    var entities = transaction.find(entityType, propertyName, caseSensitivePropertyValue);
    return findUniqueAmong(entities, propertyName, caseSensitivePropertyValue);
  }

  private static Optional<Entity> findUniqueAmong(EntityIterable iterable, String propertyName, String caseSensitivePropertyValue) {
    return StreamSupport.stream(iterable.spliterator(), false)
      .filter(e -> caseSensitivePropertyValue.equals(e.getProperty(propertyName)))
      .findFirst();
  }

  private static void remove(String issueKey, @NotNull StoreTransaction txn) {
    findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .ifPresent(issueEntity -> {
        var fileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_ISSUES_LINK_NAME, issueEntity);
        }
        issueEntity.deleteLinks(ISSUE_TO_FILE_LINK_NAME);
        issueEntity.delete();
      });
  }

  private static void removeTaint(String issueKey, @NotNull StoreTransaction txn) {
    findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .ifPresent(issueEntity -> {
        var fileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_TAINT_ISSUES_LINK_NAME, issueEntity);
        }
        issueEntity.deleteLinks(ISSUE_TO_FILE_LINK_NAME);
        var branchEntity = issueEntity.getLink(TAINT_ISSUE_TO_BRANCH_LINK_NAME);
        if (branchEntity != null) {
          branchEntity.deleteLink(BRANCH_TO_TAINT_ISSUES_LINK_NAME, issueEntity);
        }
        issueEntity.deleteLinks(TAINT_ISSUE_TO_BRANCH_LINK_NAME);
        issueEntity.delete();
      });
  }

  @Override
  public boolean updateIssue(String issueKey, Consumer<ServerIssue> issueUpdater) {
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey);
      if (optionalEntity.isPresent()) {
        var issueEntity = optionalEntity.get();
        var currentIssue = adapt(issueEntity);
        issueUpdater.accept(currentIssue);
        updateIssueEntity(issueEntity, currentIssue);
        return true;
      }
      return false;
    });
  }

  @Override
  public void updateTaintIssue(String issueKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    entityStore.executeInTransaction(txn -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .ifPresent(issueEntity -> {
        var currentIssue = adaptTaint(issueEntity);
        taintIssueUpdater.accept(currentIssue);
        updateTaintIssueEntity(currentIssue, issueEntity);
      }));
  }

  @Override
  public void insert(String branchName, ServerTaintIssue taintIssue) {
    entityStore.executeInTransaction(txn -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, taintIssue.getKey())
      .ifPresentOrElse(issueEntity -> LOG.error("Trying to store a taint vulnerability that already exists"), () -> {
        var branch = getOrCreateBranch(branchName, txn);
        var fileEntity = getOrCreateFile(branch, taintIssue.getFilePath(), txn);
        updateOrCreateTaintIssue(branch, fileEntity, taintIssue, txn);
      }));
  }

  @Override
  public void deleteTaintIssue(String issueKeyToDelete) {
    entityStore.executeInTransaction(txn -> removeTaint(issueKeyToDelete, txn));
  }

  @Override
  public void close() {
    backup();
    entityStore.close();
    FileUtils.deleteQuietly(xodusDbDir.toFile());
  }

  public void backup() {
    LOG.debug("Creating backup of server issue database in {}", backupFile);
    try {
      var backupTmp = CompressBackupUtil.backup(entityStore, backupFile.getParent().toFile(), "backup", false);
      Files.move(backupTmp.toPath(), backupFile, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      LOG.error("Unable to backup server issue database", e);
    }
  }

  private static Flow toJavaFlow(Sonarlint.Flow flowProto) {
    return new Flow(flowProto.getLocationList().stream().map(XodusServerIssueStore::toJavaLocation).collect(Collectors.toList()));
  }

  private static ServerIssueLocation toJavaLocation(Location locationProto) {
    return new ServerIssueLocation(locationProto.hasFilePath() ? locationProto.getFilePath() : null,
      locationProto.hasTextRange() ? toTextRangeJava(locationProto.getTextRange()) : null, locationProto.getMessage());
  }

  private static TextRangeWithHash toTextRangeJava(TextRange textRange) {
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset(), textRange.getHash());
  }

  private static Sonarlint.Flow toProtoFlow(Flow javaFlow) {
    var flowBuilder = Sonarlint.Flow.newBuilder();
    javaFlow.locations().forEach(l -> flowBuilder.addLocation(toProtoLocation(l)));
    return flowBuilder.build();
  }

  private static Location toProtoLocation(ServerIssueLocation l) {
    var location = Location.newBuilder();
    String filePath = l.getFilePath();
    if (filePath != null) {
      location.setFilePath(filePath);
    }
    location.setMessage(l.getMessage());
    var textRange = l.getTextRange();
    if (textRange != null) {
      location.setTextRange(TextRange.newBuilder()
        .setStartLine(textRange.getStartLine())
        .setStartLineOffset(textRange.getStartLineOffset())
        .setEndLine(textRange.getEndLine())
        .setEndLineOffset(textRange.getEndLineOffset())
        .setHash(textRange.getHash()));
    }
    return location.build();
  }

  static void checkCurrentSchemaVersion(StoreTransaction txn) {
    var currentSchemaVersion = getCurrentSchemaVersion(txn);
    if (currentSchemaVersion < CURRENT_SCHEMA_VERSION) {
      // Migrate v0 to v1: force re-sync of taint vulnerabilities
      txn.getAll(BRANCH_ENTITY_TYPE).forEach(b -> b.setProperty(LAST_TAINT_SYNC_PROPERTY_NAME, Instant.EPOCH));

      // Set schema version to current after migration(s)
      txn.getAll(SCHEMA_ENTITY_TYPE).forEach(Entity::delete);
      var newSchema = txn.newEntity(SCHEMA_ENTITY_TYPE);
      newSchema.setProperty(VERSION_PROPERTY_NAME, CURRENT_SCHEMA_VERSION);
      txn.saveEntity(newSchema);
      txn.flush();
    }
  }

  static int getCurrentSchemaVersion(StoreTransaction txn) {
    var schemaEntities = txn.getAll(SCHEMA_ENTITY_TYPE);
    var schemaEntitiesCount = schemaEntities.size();
    if (schemaEntitiesCount == 1) {
      var schemaEntity = schemaEntities.getFirst();
      var schemaVersion = schemaEntity.getProperty(VERSION_PROPERTY_NAME);
      if (schemaVersion == null) {
        return 0;
      }
      return (Integer) schemaVersion;
    } else {
      // If there are 0 or more than 1 entries, then we need to wipe
      return 0;
    }
  }

  int getCurrentSchemaVersion() {
    return entityStore.computeInTransaction(XodusServerIssueStore::getCurrentSchemaVersion);
  }
}
