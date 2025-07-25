/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.util.ProtobufUtil;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerFinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.Flow;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.ServerIssueLocation;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.Location;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint.TextRange;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.sonarsource.sonarlint.core.commons.storage.XodusPurgeUtils.purgeOldTemporaryFiles;
import static org.sonarsource.sonarlint.core.serverconnection.storage.StorageUtils.deserializeLanguages;

public class XodusServerIssueStore implements ProjectServerIssueStore {

  static final int CURRENT_SCHEMA_VERSION = 2;

  private static final String BACKUP_TAR_GZ = "backup.tar.gz";

  private static final String HOTSPOTS = "hotspots";

  private static final String ISSUES = "issues";

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String SERVER_ISSUE_STORE = "xodus-issue-store";
  private static final Integer PURGE_NUMBER_OF_DAYS = 3;
  private static final String BRANCH_ENTITY_TYPE = "Branch";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String ISSUE_ENTITY_TYPE = "Issue";
  private static final String TAINT_ISSUE_ENTITY_TYPE = "TaintIssue";
  private static final String HOTSPOT_ENTITY_TYPE = "Hotspot";
  private static final String DEPENDENCY_RISK_ENTITY_TYPE = "DependencyRisk";
  private static final String SCHEMA_ENTITY_TYPE = "Schema";

  private static final String BRANCH_TO_FILES_LINK_NAME = "files";
  private static final String BRANCH_TO_TAINT_ISSUES_LINK_NAME = "taintIssues";
  private static final String BRANCH_TO_DEPENDENCY_RISKS_LINK_NAME = "dependencyRisks";
  private static final String TAINT_ISSUE_TO_BRANCH_LINK_NAME = "branch";
  private static final String DEPENDENCY_RISK_TO_BRANCH_LINK_NAME = "branch";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String FILE_TO_TAINT_ISSUES_LINK_NAME = "taintIssues";
  private static final String FILE_TO_HOTSPOTS_LINK_NAME = "hotspots";
  private static final String ISSUE_TO_FILE_LINK_NAME = "file";

  private static final String START_LINE_PROPERTY_NAME = "startLine";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endLine";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String ID_PROPERTY_NAME = "id";
  private static final String KEY_PROPERTY_NAME = "key";
  private static final String RESOLVED_PROPERTY_NAME = "resolved";
  private static final String REVIEW_STATUS_PROPERTY_NAME = "status";
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
  private static final String LAST_ISSUE_ENABLED_LANGUAGES = "lastIssueEnabledLanguages";
  private static final String LAST_TAINT_ENABLED_LANGUAGES = "lastTaintEnabledLanguages";
  private static final String LAST_HOTSPOT_ENABLED_LANGUAGES = "lastHotspotEnabledLanguages";
  private static final String LAST_TAINT_SYNC_PROPERTY_NAME = "lastTaintSync";
  private static final String LAST_HOTSPOT_SYNC_PROPERTY_NAME = "lastHotspotSync";
  private static final String VERSION_PROPERTY_NAME = "version";

  private static final String MESSAGE_BLOB_NAME = "message";
  private static final String FLOWS_BLOB_NAME = "flows";
  private static final String RULE_DESCRIPTION_CONTEXT_KEY_PROPERTY_NAME = "ruleDescriptionContextKey";
  private static final String CLEAN_CODE_ATTRIBUTE_PROPERTY_NAME = "cleanCodeAttribute";
  private static final String IMPACTS_BLOB_NAME = "impacts";
  private static final String ASSIGNEE_PROPERTY_NAME = "assignee";
  private static final String PACKAGE_NAME_PROPERTY_NAME = "packageName";
  private static final String PACKAGE_VERSION_PROPERTY_NAME = "packageVersion";
  private static final String TRANSITIONS_PROPERTY_NAME = "transitions";
  private static final String STATUS_PROPERTY_NAME = "status";
  private final PersistentEntityStore entityStore;

  private final Path backupFile;

  private final Path xodusDbDir;

  public XodusServerIssueStore(Path backupDir, Path workDir) throws IOException {
    this(backupDir, workDir, XodusServerIssueStore::migrate);
  }

  XodusServerIssueStore(Path backupDir, Path workDir, StoreTransactionalExecutable afterInit) throws IOException {
    xodusDbDir = Files.createTempDirectory(workDir, SERVER_ISSUE_STORE);
    purgeOldTemporaryFiles(workDir, PURGE_NUMBER_OF_DAYS, SERVER_ISSUE_STORE + "*");
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
      entityStore.registerCustomPropertyType(txn, HotspotReviewStatus.class, new HotspotReviewStatusBinding());
      entityStore.registerCustomPropertyType(txn, UUID.class, new UuidBinding());
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
    var impacts = readImpacts(storedIssue.getBlob(IMPACTS_BLOB_NAME));
    var effectiveFilePath = Path.of(filePath);
    if (startLine == null) {
      return new FileLevelServerIssue(key, resolved, ruleKey, msg, effectiveFilePath, creationDate, userSeverity, type, impacts);
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
          effectiveFilePath,
          creationDate,
          userSeverity,
          type,
          textRange,
          impacts);
      } else {
        return new LineLevelServerIssue(
          key,
          resolved,
          ruleKey,
          msg,
          storedIssue.getBlobString(LINE_HASH_PROPERTY_NAME),
          effectiveFilePath,
          creationDate,
          userSeverity,
          type,
          (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME),
          impacts);
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
    var cleanCodeAttribute = (String) storedIssue.getProperty(CLEAN_CODE_ATTRIBUTE_PROPERTY_NAME);
    return new ServerTaintIssue(
      (UUID) requireNonNull(storedIssue.getProperty(ID_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
      Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME)),
      requireNonNull(storedIssue.getBlobString(MESSAGE_BLOB_NAME)),
      Path.of(filePath),
      (Instant) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME)),
      (IssueSeverity) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)),
      (RuleType) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)),
      textRange, (String) storedIssue.getProperty(RULE_DESCRIPTION_CONTEXT_KEY_PROPERTY_NAME),
      Optional.ofNullable(cleanCodeAttribute).map(CleanCodeAttribute::valueOf).orElse(null),
      readImpacts(storedIssue.getBlob(IMPACTS_BLOB_NAME)))
        .setFlows(readFlows(storedIssue.getBlob(FLOWS_BLOB_NAME)));
  }

  private static ServerHotspot adaptHotspot(Entity storedHotspot) {
    var filePath = (String) requireNonNull(storedHotspot.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var startLine = (Integer) storedHotspot.getProperty(START_LINE_PROPERTY_NAME);
    var startLineOffset = (Integer) storedHotspot.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
    var endLine = (Integer) storedHotspot.getProperty(END_LINE_PROPERTY_NAME);
    var endLineOffset = (Integer) storedHotspot.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
    var hash = (String) storedHotspot.getProperty(RANGE_HASH_PROPERTY_NAME);
    org.sonarsource.sonarlint.core.commons.api.TextRange textRange;
    if (hash != null && !hash.isEmpty()) {
      textRange = new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, hash);
    } else {
      textRange = new org.sonarsource.sonarlint.core.commons.api.TextRange(startLine, startLineOffset, endLine, endLineOffset);
    }
    var vulnerabilityProbability = VulnerabilityProbability.valueOf((String) storedHotspot.getProperty(VULNERABILITY_PROBABILITY_PROPERTY_NAME));
    var assignee = (String) storedHotspot.getProperty(ASSIGNEE_PROPERTY_NAME);
    var status = (HotspotReviewStatus) storedHotspot.getProperty(REVIEW_STATUS_PROPERTY_NAME);
    if (status == null) {
      // backward compatibility. This should not happen as hotspots are all replaced during the sync
      var resolved = Boolean.TRUE.equals(storedHotspot.getProperty(RESOLVED_PROPERTY_NAME));
      status = resolved ? HotspotReviewStatus.SAFE : HotspotReviewStatus.TO_REVIEW;
    }
    return new ServerHotspot(
      (String) requireNonNull(storedHotspot.getProperty(KEY_PROPERTY_NAME)),
      (String) requireNonNull(storedHotspot.getProperty(RULE_KEY_PROPERTY_NAME)),
      requireNonNull(storedHotspot.getBlobString(MESSAGE_BLOB_NAME)),
      Path.of(filePath),
      textRange,
      (Instant) requireNonNull(storedHotspot.getProperty(CREATION_DATE_PROPERTY_NAME)),
      status,
      vulnerabilityProbability,
      assignee);
  }

  private static List<Flow> readFlows(@Nullable InputStream blob) {
    if (blob == null) {
      return List.of();
    }
    return ProtobufUtil.readMessages(blob, Sonarlint.Flow.parser()).stream().map(XodusServerIssueStore::toJavaFlow).toList();
  }

  private static Map<SoftwareQuality, ImpactSeverity> readImpacts(@Nullable InputStream blob) {
    if (blob == null) {
      return Map.of();
    }
    return ProtobufUtil.readMessages(blob, Sonarlint.Impact.parser())
      .stream()
      .map(XodusServerIssueStore::toJavaImpact)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public List<ServerIssue<?>> load(String branchName, Path filePath) {
    return loadIssue(branchName, filePath, FILE_TO_ISSUES_LINK_NAME, XodusServerIssueStore::adapt);
  }

  @Override
  public Collection<ServerHotspot> loadHotspots(String branchName, Path serverFilePath) {
    return loadIssue(branchName, serverFilePath, FILE_TO_HOTSPOTS_LINK_NAME, XodusServerIssueStore::adaptHotspot);
  }

  private <G> List<G> loadIssue(String branchName, Path filePath, String linkName, Function<Entity, G> adapter) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> branch.getLinks(BRANCH_TO_FILES_LINK_NAME))
      .flatMap(files -> findUniqueAmong(files, PATH_PROPERTY_NAME, filePath.toString()))
      .map(fileToLoad -> fileToLoad.getLinks(linkName))
      .map(issueEntities -> StreamSupport.stream(issueEntities.spliterator(), false)
        .map(adapter)
        .toList())
      .orElseGet(Collections::emptyList));
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> StreamSupport.stream(branch.getLinks(BRANCH_TO_TAINT_ISSUES_LINK_NAME).spliterator(), false)
        .map(XodusServerIssueStore::adaptTaint)
        .toList())
      .orElseGet(Collections::emptyList));
  }

  @Override
  public void replaceAllIssuesOfFile(String branchName, Path serverFilePath, List<ServerIssue<?>> issues) {
    timed(wroteMessage(issues.size(), ISSUES), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);
      replaceAllIssuesOfFile(issues, txn, fileEntity);
    }));
  }

  @Override
  public void mergeIssues(String branchName, List<ServerIssue<?>> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    var issuesByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
    timed(mergedMessage(issuesToMerge.size(), closedIssueKeysToDelete.size(), ISSUES), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      issuesByFilePath.forEach((filePath, issues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        issues.forEach(issue -> updateOrCreateIssue(fileEntity, issue, txn));
        txn.flush();
      });
      closedIssueKeysToDelete.forEach(issueKey -> remove(issueKey, txn));
      branch.setProperty(LAST_ISSUE_SYNC_PROPERTY_NAME, syncTimestamp);

      String serializedLanguages = getSerializedLanguages(enabledLanguages);
      branch.setProperty(LAST_ISSUE_ENABLED_LANGUAGES, serializedLanguages);
    }));
  }

  @Override
  public void mergeTaintIssues(String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete,
    Instant syncTimestamp, Set<SonarLanguage> enabledLanguages) {
    var issuesByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerTaintIssue::getFilePath));
    timed(mergedMessage(issuesToMerge.size(), closedIssueKeysToDelete.size(), "taint issues"), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      issuesByFilePath.forEach((filePath, issues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        issues.forEach(issue -> updateOrCreateTaintIssue(branch, fileEntity, issue, txn));
        txn.flush();
      });
      closedIssueKeysToDelete.forEach(issueKey -> removeTaint(issueKey, txn));
      branch.setProperty(LAST_TAINT_SYNC_PROPERTY_NAME, syncTimestamp);

      String serializedLanguages = getSerializedLanguages(enabledLanguages);
      branch.setProperty(LAST_TAINT_ENABLED_LANGUAGES, serializedLanguages);
    }));
  }

  @Override
  public void mergeHotspots(String branchName, List<ServerHotspot> hotspotsToMerge, Set<String> closedHotspotKeysToDelete, Instant syncTimestamp,
    Set<SonarLanguage> enabledLanguages) {
    var hotspotsByFilePath = hotspotsToMerge.stream().collect(Collectors.groupingBy(ServerHotspot::getFilePath));
    timed(mergedMessage(hotspotsToMerge.size(), closedHotspotKeysToDelete.size(), HOTSPOTS), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      hotspotsByFilePath.forEach((filePath, hotspots) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        hotspots.forEach(hotspot -> updateOrCreateHotspot(fileEntity, hotspot, txn));
        txn.flush();
      });
      closedHotspotKeysToDelete.forEach(hotspotKey -> removeHotspot(hotspotKey, txn));
      branch.setProperty(LAST_HOTSPOT_SYNC_PROPERTY_NAME, syncTimestamp);

      String serializedLanguages = getSerializedLanguages(enabledLanguages);
      branch.setProperty(LAST_HOTSPOT_ENABLED_LANGUAGES, serializedLanguages);
    }));
  }

  private static String wroteMessage(int wrote, String itemName) {
    return String.format("Wrote %d %s in store", wrote, itemName);
  }

  private static String mergedMessage(int merged, int closed, String itemName) {
    return String.format("Merged %d %s in store. Closed %d.", merged, itemName, closed);
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (Instant) branch.getProperty(LAST_ISSUE_SYNC_PROPERTY_NAME)));
  }

  @Override
  public Set<SonarLanguage> getLastIssueEnabledLanguages(String branchName) {
    var lastEnabledLanguages = entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (String) branch.getProperty(LAST_ISSUE_ENABLED_LANGUAGES)));

    return deserializeLanguages(lastEnabledLanguages);
  }

  @Override
  public Set<SonarLanguage> getLastTaintEnabledLanguages(String branchName) {
    var lastEnabledLanguages = entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (String) branch.getProperty(LAST_TAINT_ENABLED_LANGUAGES)));

    return deserializeLanguages(lastEnabledLanguages);
  }

  @Override
  public Set<SonarLanguage> getLastHotspotEnabledLanguages(String branchName) {
    var lastEnabledLanguages = entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (String) branch.getProperty(LAST_HOTSPOT_ENABLED_LANGUAGES)));
    return deserializeLanguages(lastEnabledLanguages);
  }

  @Override
  public Optional<Instant> getLastTaintSyncTimestamp(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (Instant) branch.getProperty(LAST_TAINT_SYNC_PROPERTY_NAME)));
  }

  @Override
  public Optional<Instant> getLastHotspotSyncTimestamp(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> (Instant) branch.getProperty(LAST_HOTSPOT_SYNC_PROPERTY_NAME)));
  }

  @Override
  public boolean wasEverUpdated() {
    return entityStore.computeInTransaction(txn -> !txn.getAll(BRANCH_ENTITY_TYPE).isEmpty());
  }

  @Override
  public void replaceAllIssuesOfBranch(String branchName, List<ServerIssue<?>> issues) {
    var issuesByFile = issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
    timed(wroteMessage(issues.size(), ISSUES), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      branch.getLinks(BRANCH_TO_FILES_LINK_NAME).forEach(fileEntity -> {
        var entityFilePathStr = ((String) fileEntity.getProperty(PATH_PROPERTY_NAME));
        var entityFilePath = entityFilePathStr != null ? Path.of(entityFilePathStr) : null;
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
    timed(wroteMessage(serverHotspots.size(), HOTSPOTS), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      branch.getLinks(BRANCH_TO_FILES_LINK_NAME).forEach(fileEntity -> {
        var entityFilePathStr = ((String) fileEntity.getProperty(PATH_PROPERTY_NAME));
        var entityFilePath = entityFilePathStr != null ? Path.of(entityFilePathStr) : null;
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
  public void replaceAllHotspotsOfFile(String branchName, Path serverFilePath, Collection<ServerHotspot> serverHotspots) {
    timed(wroteMessage(serverHotspots.size(), HOTSPOTS), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);
      replaceAllHotspotsOfFile(serverHotspots, txn, fileEntity);
    }));
  }

  @Override
  public boolean changeHotspotStatus(String hotspotKey, HotspotReviewStatus newStatus) {
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, HOTSPOT_ENTITY_TYPE, KEY_PROPERTY_NAME, hotspotKey);
      if (optionalEntity.isPresent()) {
        var hotspotEntity = optionalEntity.get();
        hotspotEntity.setProperty(REVIEW_STATUS_PROPERTY_NAME, newStatus);
        return true;
      }
      return false;
    });
  }

  private static void replaceAllHotspotsOfFile(Collection<ServerHotspot> hotspots, @NotNull StoreTransaction txn, Entity fileEntity) {
    fileEntity.getLinks(FILE_TO_HOTSPOTS_LINK_NAME).forEach(Entity::delete);
    fileEntity.deleteLinks(FILE_TO_HOTSPOTS_LINK_NAME);

    hotspots.forEach(hotspot -> updateOrCreateHotspot(fileEntity, hotspot, txn));
  }

  private static String getSerializedLanguages(Set<SonarLanguage> enabledLanguages) {
    return enabledLanguages.stream().map(SonarLanguage::getSonarLanguageKey).collect(joining(","));
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
    issueEntity.setProperty(REVIEW_STATUS_PROPERTY_NAME, hotspot.getStatus());
    issueEntity.setProperty(VULNERABILITY_PROBABILITY_PROPERTY_NAME, hotspot.getVulnerabilityProbability().toString());
    if (hotspot.getAssignee() != null) {
      issueEntity.setProperty(ASSIGNEE_PROPERTY_NAME, hotspot.getAssignee());
    }
  }

  private static void deleteAllHotspotsOfFile(@NotNull StoreTransaction txn, Entity fileEntity) {
    replaceAllHotspotsOfFile(List.of(), txn, fileEntity);
  }

  private static void deleteAllIssuesOfFile(@NotNull StoreTransaction txn, Entity fileEntity) {
    replaceAllIssuesOfFile(List.of(), txn, fileEntity);
  }

  private static void deleteAllTaintsOfBranch(Entity branchEntity, Set<Path> filePaths) {
    branchEntity.getLinks(BRANCH_TO_FILES_LINK_NAME).forEach(fileEntity -> {
      var entityFilePath = fileEntity.getProperty(PATH_PROPERTY_NAME);
      if (!filePaths.contains(entityFilePath)) {
        deleteAllTaintsOfFile(fileEntity);
      }
    });
    branchEntity.getLinks(BRANCH_TO_TAINT_ISSUES_LINK_NAME).forEach(Entity::delete);
    branchEntity.deleteLinks(BRANCH_TO_TAINT_ISSUES_LINK_NAME);
  }

  private static void deleteAllTaintsOfFile(Entity fileEntity) {
    fileEntity.getLinks(FILE_TO_TAINT_ISSUES_LINK_NAME).forEach(Entity::delete);
    fileEntity.deleteLinks(FILE_TO_TAINT_ISSUES_LINK_NAME);
  }

  private static void timed(String msg, Runnable transaction) {
    var startTime = Instant.now();
    transaction.run();
    var duration = Duration.between(startTime, Instant.now());
    LOG.debug("{} | took {}ms", msg, duration.toMillis());
  }

  private static void replaceAllIssuesOfFile(List<ServerIssue<?>> issues, @NotNull StoreTransaction txn, Entity fileEntity) {
    fileEntity.getLinks(FILE_TO_ISSUES_LINK_NAME).forEach(Entity::delete);
    fileEntity.deleteLinks(FILE_TO_ISSUES_LINK_NAME);

    issues.forEach(issue -> updateOrCreateIssue(fileEntity, issue, txn));
  }

  @Override
  public void replaceAllTaintsOfBranch(String branchName, List<ServerTaintIssue> taintIssues) {
    var taintsByFile = taintIssues.stream().collect(Collectors.groupingBy(ServerTaintIssue::getFilePath));
    timed(wroteMessage(taintIssues.size(), "taints"), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      deleteAllTaintsOfBranch(branch, taintsByFile.keySet());
      txn.flush();
      taintsByFile.forEach((filePath, fileIssues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        fileIssues.forEach(issue -> updateOrCreateTaintIssue(branch, fileEntity, issue, txn));
        txn.flush();
      });
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

  private static Entity getOrCreateFile(Entity branchEntity, Path filePath, StoreTransaction txn) {
    return findUniqueAmong(branchEntity.getLinks(BRANCH_TO_FILES_LINK_NAME), PATH_PROPERTY_NAME, filePath.toString())
      .orElseGet(() -> {
        var file = txn.newEntity(FILE_ENTITY_TYPE);
        file.setProperty(PATH_PROPERTY_NAME, filePath.toString());
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
    if (issue instanceof LineLevelServerIssue lineIssue) {
      issueEntity.setBlobString(LINE_HASH_PROPERTY_NAME, lineIssue.getLineHash());
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, lineIssue.getLine());
    } else if (issue instanceof RangeLevelServerIssue rangeIssue) {
      var textRange = rangeIssue.getTextRange();
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      issueEntity.setBlobString(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
    }
    issueEntity.setBlob(IMPACTS_BLOB_NAME, toProtoImpact(issue.getImpacts()));
  }

  private static void updateOrCreateTaintIssue(Entity branchEntity, Entity fileEntity, ServerTaintIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getSonarServerKey(), transaction, TAINT_ISSUE_ENTITY_TYPE, FILE_TO_TAINT_ISSUES_LINK_NAME);
    updateTaintIssueEntity(issue, issueEntity);
    branchEntity.addLink(BRANCH_TO_TAINT_ISSUES_LINK_NAME, issueEntity);
    issueEntity.setLink(TAINT_ISSUE_TO_BRANCH_LINK_NAME, branchEntity);
  }

  private static void updateTaintIssueEntity(ServerTaintIssue issue, Entity issueEntity) {
    issueEntity.setProperty(ID_PROPERTY_NAME, issue.getId());
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
    issue.getCleanCodeAttribute().ifPresent(attribute -> issueEntity.setProperty(CLEAN_CODE_ATTRIBUTE_PROPERTY_NAME, attribute.name()));
    issueEntity.setBlob(IMPACTS_BLOB_NAME, toProtoImpact(issue.getImpacts()));
  }

  public static InputStream toProtoFlow(List<Flow> flows) {
    var buffer = new ByteArrayOutputStream();
    ProtobufUtil.writeMessages(buffer, flows.stream().map(XodusServerIssueStore::toProtoFlow).toList());
    return new ByteArrayInputStream(buffer.toByteArray());
  }

  public static InputStream toProtoImpact(Map<SoftwareQuality, ImpactSeverity> impacts) {
    var buffer = new ByteArrayOutputStream();
    ProtobufUtil.writeMessages(buffer, impacts.entrySet().stream().map(XodusServerIssueStore::toProtoImpact).toList());
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

  private static Optional<UUID> removeTaint(String issueKey, @NotNull StoreTransaction txn) {
    return findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .map(issueEntity -> {
        var id = (UUID) issueEntity.getProperty(ID_PROPERTY_NAME);
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
        return id;
      });
  }

  private static void removeHotspot(String hotspotKey, @NotNull StoreTransaction txn) {
    findUnique(txn, HOTSPOT_ENTITY_TYPE, KEY_PROPERTY_NAME, hotspotKey)
      .ifPresent(hotspotEntity -> {
        var fileEntity = hotspotEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_HOTSPOTS_LINK_NAME, hotspotEntity);
        }
        hotspotEntity.deleteLinks(ISSUE_TO_FILE_LINK_NAME);
        hotspotEntity.delete();
      });
  }

  @Override
  public boolean updateIssue(String issueKey, Consumer<ServerIssue<?>> issueUpdater) {
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
  public ServerIssue<?> getIssue(String issueKey) {
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey);
      if (optionalEntity.isPresent()) {
        var issueEntity = optionalEntity.get();
        return adapt(issueEntity);
      }
      return null;
    });
  }

  @Override
  public ServerHotspot getHotspot(String hotspotKey) {
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, HOTSPOT_ENTITY_TYPE, KEY_PROPERTY_NAME, hotspotKey);
      if (optionalEntity.isPresent()) {
        var hotspotEntity = optionalEntity.get();
        return adaptHotspot(hotspotEntity);
      }
      return null;
    });
  }

  @Override
  public Optional<ServerFinding> updateIssueResolutionStatus(String issueKey, boolean isTaintIssue, boolean isResolved) {
    var entityIssueType = isTaintIssue ? TAINT_ISSUE_ENTITY_TYPE : ISSUE_ENTITY_TYPE;
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, entityIssueType, KEY_PROPERTY_NAME, issueKey);
      if (optionalEntity.isPresent()) {
        var issueEntity = optionalEntity.get();
        issueEntity.setProperty(RESOLVED_PROPERTY_NAME, isResolved);
        return Optional.of(isTaintIssue ? adaptTaint(issueEntity) : adapt(issueEntity));
      }
      return Optional.empty();
    });
  }

  @Override
  public boolean containsIssue(String issueKey) {
    return entityStore.computeInTransaction(txn -> findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .or(() -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey))
      .isPresent());
  }

  @Override
  public List<ServerDependencyRisk> loadDependencyRisks(String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName)
      .map(branch -> StreamSupport.stream(branch.getLinks(BRANCH_TO_DEPENDENCY_RISKS_LINK_NAME).spliterator(), false)
        .map(XodusServerIssueStore::adaptDependencyRisk)
        .toList())
      .orElseGet(Collections::emptyList));
  }

  @Override
  public void updateDependencyRiskStatus(UUID key, ServerDependencyRisk.Status newStatus) {
    entityStore.executeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, DEPENDENCY_RISK_ENTITY_TYPE, KEY_PROPERTY_NAME, key.toString());
      if (optionalEntity.isPresent()) {
        var issueEntity = optionalEntity.get();
        issueEntity.setProperty(STATUS_PROPERTY_NAME, newStatus.name());
      }
    });
  }

  @Override
  public void replaceAllDependencyRisksOfBranch(String branchName, List<ServerDependencyRisk> serverDependencyRisks) {
    timed(wroteMessage(serverDependencyRisks.size(), "Dependency risks"), () -> entityStore.executeInTransaction(txn -> {
      var branch = getOrCreateBranch(branchName, txn);
      deleteAllDependencyRisksOfBranch(branch);
      txn.flush();
      serverDependencyRisks.forEach(dependencyRisk -> updateOrCreateDependencyRisk(branch, dependencyRisk, txn));
      txn.flush();
    }));
  }

  private static ServerDependencyRisk adaptDependencyRisk(Entity storedIssue) {
    var key = UUID.fromString((String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)));
    var type = ServerDependencyRisk.Type.valueOf((String) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)));
    var severity = ServerDependencyRisk.Severity.valueOf((String) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)));
    var status = ServerDependencyRisk.Status.valueOf((String) requireNonNull(storedIssue.getProperty(STATUS_PROPERTY_NAME)));
    var packageName = (String) requireNonNull(storedIssue.getProperty(PACKAGE_NAME_PROPERTY_NAME));
    var packageVersion = (String) requireNonNull(storedIssue.getProperty(PACKAGE_VERSION_PROPERTY_NAME));
    var transitionsString = (String) requireNonNull(storedIssue.getProperty(TRANSITIONS_PROPERTY_NAME));
    var transitions = transitionsString.trim().isEmpty() ? List.<ServerDependencyRisk.Transition>of()
      : Stream.of(transitionsString.split(",")).map(ServerDependencyRisk.Transition::valueOf).toList();
    return new ServerDependencyRisk(key, type, severity, status, packageName, packageVersion, transitions);
  }

  private static void deleteAllDependencyRisksOfBranch(Entity branchEntity) {
    branchEntity.getLinks(BRANCH_TO_DEPENDENCY_RISKS_LINK_NAME).forEach(Entity::delete);
    branchEntity.deleteLinks(BRANCH_TO_DEPENDENCY_RISKS_LINK_NAME);
  }

  private static void updateOrCreateDependencyRisk(Entity branchEntity, ServerDependencyRisk dependencyRisk, StoreTransaction transaction) {
    var riskEntity = findUnique(transaction, DEPENDENCY_RISK_ENTITY_TYPE, KEY_PROPERTY_NAME, dependencyRisk.key().toString())
      .orElseGet(() -> transaction.newEntity(DEPENDENCY_RISK_ENTITY_TYPE));
    updateDependencyRiskEntity(dependencyRisk, riskEntity);
    branchEntity.addLink(BRANCH_TO_DEPENDENCY_RISKS_LINK_NAME, riskEntity);
    riskEntity.setLink(DEPENDENCY_RISK_TO_BRANCH_LINK_NAME, branchEntity);
  }

  private static void updateDependencyRiskEntity(ServerDependencyRisk issue, Entity issueEntity) {
    issueEntity.setProperty(KEY_PROPERTY_NAME, issue.key().toString());
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.type().name());
    issueEntity.setProperty(SEVERITY_PROPERTY_NAME, issue.severity().name());
    issueEntity.setProperty(STATUS_PROPERTY_NAME, issue.status().name());
    issueEntity.setProperty(PACKAGE_NAME_PROPERTY_NAME, issue.packageName());
    issueEntity.setProperty(PACKAGE_VERSION_PROPERTY_NAME, issue.packageVersion());
    issueEntity.setProperty(TRANSITIONS_PROPERTY_NAME, issue.transitions().stream()
      .map(Enum::name)
      .collect(Collectors.joining(",")));
  }

  @Override
  public Optional<ServerTaintIssue> updateTaintIssueBySonarServerKey(String issueKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey);
      if (optionalEntity.isPresent()) {
        var taintEntity = optionalEntity.get();
        var currentIssue = adaptTaint(taintEntity);
        taintIssueUpdater.accept(currentIssue);
        updateTaintIssueEntity(currentIssue, taintEntity);
        return Optional.of(currentIssue);
      }
      return Optional.empty();
    });
  }

  @Override
  public void insert(String branchName, ServerTaintIssue taintIssue) {
    entityStore.executeInTransaction(txn -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, taintIssue.getSonarServerKey())
      .ifPresentOrElse(issueEntity -> LOG.error("Trying to store a taint vulnerability that already exists"), () -> {
        var branch = getOrCreateBranch(branchName, txn);
        var fileEntity = getOrCreateFile(branch, taintIssue.getFilePath(), txn);
        updateOrCreateTaintIssue(branch, fileEntity, taintIssue, txn);
      }));
  }

  @Override
  public void insert(String branchName, ServerHotspot hotspot) {
    entityStore.executeInTransaction(txn -> findUnique(txn, HOTSPOT_ENTITY_TYPE, KEY_PROPERTY_NAME, hotspot.getKey())
      .ifPresentOrElse(hotspotEntity -> LOG.error("Trying to store a hotspot that already exists"), () -> {
        var branch = getOrCreateBranch(branchName, txn);
        var fileEntity = getOrCreateFile(branch, hotspot.getFilePath(), txn);
        updateOrCreateHotspot(fileEntity, hotspot, txn);
      }));
  }

  @Override
  public Optional<UUID> deleteTaintIssueBySonarServerKey(String issueKeyToDelete) {
    return entityStore.computeInTransaction(txn -> removeTaint(issueKeyToDelete, txn));
  }

  @Override
  public void deleteHotspot(String hotspotKey) {
    entityStore.executeInTransaction(txn -> findUnique(txn, HOTSPOT_ENTITY_TYPE, KEY_PROPERTY_NAME, hotspotKey)
      .ifPresent(hotspotEntity -> {
        var fileEntity = hotspotEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_HOTSPOTS_LINK_NAME, hotspotEntity);
        }
        hotspotEntity.deleteLinks(ISSUE_TO_FILE_LINK_NAME);
        hotspotEntity.delete();
      }));
  }

  @Override
  public void close() {
    backup();
    entityStore.close();
    FileUtils.deleteQuietly(xodusDbDir.toFile());
  }

  @Override
  public void updateHotspot(String hotspotKey, Consumer<ServerHotspot> hotspotUpdater) {
    entityStore.executeInTransaction(txn -> findUnique(txn, HOTSPOT_ENTITY_TYPE, KEY_PROPERTY_NAME, hotspotKey)
      .ifPresent(hotspotEntity -> {
        var currentHotspot = adaptHotspot(hotspotEntity);
        hotspotUpdater.accept(currentHotspot);
        updateHotspotEntity(hotspotEntity, currentHotspot);
      }));
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
    return new Flow(flowProto.getLocationList().stream().map(XodusServerIssueStore::toJavaLocation).toList());
  }

  private static Map.Entry<SoftwareQuality, ImpactSeverity> toJavaImpact(Sonarlint.Impact impactProto) {
    return Map.entry(SoftwareQuality.valueOf(impactProto.getSoftwareQuality()), ImpactSeverity.valueOf(impactProto.getSeverity()));
  }

  private static ServerIssueLocation toJavaLocation(Location locationProto) {
    return new ServerIssueLocation(locationProto.hasFilePath() ? Path.of(locationProto.getFilePath()) : null,
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

  private static Sonarlint.Impact toProtoImpact(Map.Entry<SoftwareQuality, ImpactSeverity> impact) {
    return Sonarlint.Impact.newBuilder()
      .setSoftwareQuality(impact.getKey().name())
      .setSeverity(impact.getValue().name())
      .build();
  }

  private static Location toProtoLocation(ServerIssueLocation l) {
    var location = Location.newBuilder();
    var filePath = l.getFilePath();
    if (filePath != null) {
      location.setFilePath(filePath.toString());
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

  static void migrate(StoreTransaction txn) {
    var currentSchemaVersion = getCurrentSchemaVersion(txn);
    if (currentSchemaVersion < 1) {
      // Migrate v0 to v1: force re-sync of taint vulnerabilities
      txn.getAll(BRANCH_ENTITY_TYPE).forEach(b -> b.setProperty(LAST_TAINT_SYNC_PROPERTY_NAME, Instant.EPOCH));
    }
    if (currentSchemaVersion < CURRENT_SCHEMA_VERSION) {
      // Migrate v1 to v2: assign a UUID to each taint
      txn.getAll(TAINT_ISSUE_ENTITY_TYPE).forEach(entity -> entity.setProperty(ID_PROPERTY_NAME, UUID.randomUUID()));

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
