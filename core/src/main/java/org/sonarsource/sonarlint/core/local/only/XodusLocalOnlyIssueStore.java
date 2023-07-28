/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.local.only;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.entitystore.StoreTransaction;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.util.CompressBackupUtil;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.storage.InstantBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;

import static java.util.Objects.requireNonNull;

public class XodusLocalOnlyIssueStore {

  private static final String CONFIGURATION_SCOPE_ID_ENTITY_TYPE = "Scope";
  private static final String CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME = "files";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String PATH_PROPERTY_NAME = "path";
  private static final String NAME_PROPERTY_NAME = "name";
  private static final String ISSUE_ENTITY_TYPE = "Issue";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String UUID_PROPERTY_NAME = "uuid";
  private static final String ISSUE_TO_FILE_LINK_NAME = "file";
  private static final String COMMENT_PROPERTY_NAME = "comment";
  private static final String RESOLVED_STATUS_PROPERTY_NAME = "resolvedStatus";
  private static final String RESOLUTION_DATE_PROPERTY_NAME = "resolvedDate";
  private static final String RULE_KEY_PROPERTY_NAME = "ruleKey";
  private static final String RANGE_HASH_PROPERTY_NAME = "rangeHash";
  private static final String LINE_HASH_PROPERTY_NAME = "lineHash";
  private static final String START_LINE_PROPERTY_NAME = "startLine";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endLine";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String MESSAGE_BLOB_NAME = "message";
  private static final String BACKUP_TAR_GZ = "local_only_issue_backup.tar.gz";
  private final PersistentEntityStore entityStore;
  private final Path backupFile;
  private final Path xodusDbDir;
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public XodusLocalOnlyIssueStore(Path backupDir, Path workDir) throws IOException {
    xodusDbDir = Files.createTempDirectory(workDir, "xodus-local-only-issue-store");
    backupFile = backupDir.resolve(BACKUP_TAR_GZ);
    if (Files.isRegularFile(backupFile)) {
      LOG.debug("Restoring previous local-only issue database from {}", backupFile);
      try {
        TarGzUtils.extractTarGz(backupFile, xodusDbDir);
      } catch (Exception e) {
        LOG.error("Unable to restore local-only issue backup {}", backupFile);
      }
    }
    LOG.debug("Starting local-only issue database from {}", xodusDbDir);
    this.entityStore = buildEntityStore();
    entityStore.executeInTransaction(txn -> {
      entityStore.registerCustomPropertyType(txn, Instant.class, new InstantBinding());
      entityStore.registerCustomPropertyType(txn, UUID.class, new UuidBinding());
      entityStore.registerCustomPropertyType(txn, IssueStatus.class, new IssueStatusBinding());
    });
  }

  public List<LocalOnlyIssue> loadForFile(String configurationScopeId, String filePath) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, CONFIGURATION_SCOPE_ID_ENTITY_TYPE, NAME_PROPERTY_NAME, configurationScopeId)
      .map(configScopeId -> configScopeId.getLinks(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME))
      .flatMap(files -> findUniqueAmong(files, PATH_PROPERTY_NAME, filePath))
      .map(fileToLoad -> fileToLoad.getLinks(XodusLocalOnlyIssueStore.FILE_TO_ISSUES_LINK_NAME))
      .map(issueEntities -> StreamSupport.stream(issueEntities.spliterator(), false)
        .map(XodusLocalOnlyIssueStore::adapt)
        .collect(Collectors.toList()))
      .orElseGet(Collections::emptyList));
  }

  public List<LocalOnlyIssue> loadAll(String configurationScopeId) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, CONFIGURATION_SCOPE_ID_ENTITY_TYPE, NAME_PROPERTY_NAME, configurationScopeId)
      .map(configScopeId -> configScopeId.getLinks(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME))
      .flatMap(filesIterable -> {
        List<LocalOnlyIssue> allIssues = new ArrayList<>();
        var files = StreamSupport.stream(filesIterable.spliterator(), false)
          .collect(Collectors.toList());
        files.forEach(file -> {
          EntityIterable issueEntitiesForFile = file.getLinks(XodusLocalOnlyIssueStore.FILE_TO_ISSUES_LINK_NAME);
          List<LocalOnlyIssue> localIssuesForFile = StreamSupport.stream(issueEntitiesForFile.spliterator(), false)
            .map(XodusLocalOnlyIssueStore::adapt)
            .collect(Collectors.toList());
          allIssues.addAll(localIssuesForFile);
        });
        return Optional.of(allIssues);
      })
      .orElseGet(Collections::emptyList));
  }

  public void storeLocalOnlyIssue(String configurationScopeId, LocalOnlyIssue issue) {
    entityStore.computeInTransaction(txn -> {
      var configurationScope = getOrCreateConfigurationScopeId(configurationScopeId, txn);
      var fileEntity = getOrCreateFile(configurationScope, issue.getServerRelativePath(), txn);
      updateOrCreateIssue(fileEntity, issue, txn);
      return true;
    });
  }

  public boolean removeIssue(UUID issueId) {
    return entityStore.computeInTransaction(txn -> {
      var entities = txn.find(XodusLocalOnlyIssueStore.ISSUE_ENTITY_TYPE, UUID_PROPERTY_NAME, issueId);
      var entity = entities.getFirst();
      if (entity != null) {
        var link = entity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (link != null) {
          link.deleteLink(FILE_TO_ISSUES_LINK_NAME, entity);
        }
        return entity.delete();
      }
      return false;
    });
  }

  public boolean removeAllIssuesForFile(String configurationScopeId, String filePath) {
    return entityStore.computeInTransaction(txn -> {
      var configurationScope = getOrCreateConfigurationScopeId(configurationScopeId, txn);
      var fileEntity = getOrCreateFile(configurationScope, filePath, txn);
      fileEntity.getLinks(FILE_TO_ISSUES_LINK_NAME).forEach(Entity::delete);
      return fileEntity.delete();
    });
  }

  private static LocalOnlyIssue adapt(Entity storedIssue) {
    var filePath = (String) requireNonNull(storedIssue.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var uuid = (UUID) requireNonNull(storedIssue.getProperty(UUID_PROPERTY_NAME));
    var status = (IssueStatus) requireNonNull(storedIssue.getProperty(RESOLVED_STATUS_PROPERTY_NAME));
    var resolvedDate = (Instant) requireNonNull(storedIssue.getProperty(RESOLUTION_DATE_PROPERTY_NAME));
    var ruleKey = (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME));
    var msg = requireNonNull(storedIssue.getBlobString(MESSAGE_BLOB_NAME));
    var comment = storedIssue.getBlobString(COMMENT_PROPERTY_NAME);
    var startLine = (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME);

    TextRangeWithHash textRange = null;
    LineWithHash lineWithHash = null;
    if (startLine != null) {
      var rangeHash = (String) storedIssue.getProperty(RANGE_HASH_PROPERTY_NAME);
      if (rangeHash != null) {
        var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
        var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
        var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
        textRange = new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, rangeHash);
      }
      var lineHash = (String) storedIssue.getProperty(LINE_HASH_PROPERTY_NAME);
      if (lineHash != null) {
        lineWithHash = new LineWithHash(startLine, lineHash);
      }
    }
    return new LocalOnlyIssue(
      uuid,
      filePath,
      textRange,
      lineWithHash,
      ruleKey,
      msg,
      new LocalOnlyIssueResolution(status, resolvedDate, comment));
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

  private static Optional<Entity> findUnique(StoreTransaction transaction, String entityType, String propertyName, Comparable caseSensitivePropertyValue) {
    // the find is case-insensitive but we need an exact match
    var entities = transaction.find(entityType, propertyName, caseSensitivePropertyValue);
    return findUniqueAmong(entities, propertyName, caseSensitivePropertyValue);
  }

  private static Optional<Entity> findUniqueAmong(EntityIterable iterable, String propertyName, Comparable caseSensitivePropertyValue) {
    return StreamSupport.stream(iterable.spliterator(), false)
      .filter(e -> caseSensitivePropertyValue.equals(e.getProperty(propertyName)))
      .findFirst();
  }

  private static Entity getOrCreateConfigurationScopeId(String configurationScopeId, StoreTransaction txn) {
    return findUnique(txn, CONFIGURATION_SCOPE_ID_ENTITY_TYPE, NAME_PROPERTY_NAME, configurationScopeId)
      .orElseGet(() -> {
        var configurationScope = txn.newEntity(CONFIGURATION_SCOPE_ID_ENTITY_TYPE);
        configurationScope.setProperty(NAME_PROPERTY_NAME, configurationScopeId);
        return configurationScope;
      });
  }

  private static Entity getOrCreateFile(Entity moduleEntity, String filePath, StoreTransaction txn) {
    return findUniqueAmong(moduleEntity.getLinks(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME), PATH_PROPERTY_NAME, filePath)
      .orElseGet(() -> {
        var file = txn.newEntity(FILE_ENTITY_TYPE);
        file.setProperty(PATH_PROPERTY_NAME, filePath);
        moduleEntity.addLink(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME, file);
        return file;
      });
  }

  private static void updateOrCreateIssue(Entity fileEntity, LocalOnlyIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getId(), transaction);
    updateIssueEntity(issueEntity, issue);
  }

  private static Entity updateOrCreateIssueCommon(Entity fileEntity, UUID issueUuid, StoreTransaction transaction) {
    var issueEntity = findUnique(transaction, XodusLocalOnlyIssueStore.ISSUE_ENTITY_TYPE, UUID_PROPERTY_NAME, issueUuid)
      .orElseGet(() -> transaction.newEntity(XodusLocalOnlyIssueStore.ISSUE_ENTITY_TYPE));
    var oldFileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
    if (oldFileEntity != null && !fileEntity.equals(oldFileEntity)) {
      // issue might have moved file
      oldFileEntity.deleteLink(XodusLocalOnlyIssueStore.FILE_TO_ISSUES_LINK_NAME, issueEntity);
    }
    issueEntity.setLink(ISSUE_TO_FILE_LINK_NAME, fileEntity);
    fileEntity.addLink(XodusLocalOnlyIssueStore.FILE_TO_ISSUES_LINK_NAME, issueEntity);
    issueEntity.setProperty(UUID_PROPERTY_NAME, issueUuid);
    return issueEntity;
  }

  private static void updateIssueEntity(Entity issueEntity, LocalOnlyIssue issue) {
    issueEntity.setProperty(UUID_PROPERTY_NAME, issue.getId());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.getRuleKey());
    issueEntity.setBlobString(MESSAGE_BLOB_NAME, issue.getMessage());
    var resolution = requireNonNull(issue.getResolution());
    issueEntity.setProperty(RESOLVED_STATUS_PROPERTY_NAME, resolution.getStatus());
    issueEntity.setProperty(RESOLUTION_DATE_PROPERTY_NAME, resolution.getResolutionDate());
    var comment = resolution.getComment();
    if (comment != null) {
      issueEntity.setBlobString(COMMENT_PROPERTY_NAME, comment);
    }
    var textRange = issue.getTextRangeWithHash();
    var lineWithHash = issue.getLineWithHash();

    if (textRange != null) {
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      issueEntity.setProperty(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
    }

    if (lineWithHash != null) {
      issueEntity.setProperty(LINE_HASH_PROPERTY_NAME, lineWithHash.getHash());
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, lineWithHash.getNumber());
    }
  }

  public Optional<LocalOnlyIssue> find(UUID issueId) {
    return entityStore.computeInTransaction(txn -> findUnique(txn, ISSUE_ENTITY_TYPE, UUID_PROPERTY_NAME, issueId)
      .map(XodusLocalOnlyIssueStore::adapt));
  }

  public void purgeIssuesOlderThan(Instant limit) {
    entityStore.executeInTransaction(txn -> txn.find(ISSUE_ENTITY_TYPE, RESOLUTION_DATE_PROPERTY_NAME, Instant.EPOCH, limit)
      .forEach(issueEntity -> {
        var fileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_ISSUES_LINK_NAME, issueEntity);
        }
        issueEntity.delete();
      }));
  }

  public void backup() {
    LOG.debug("Creating backup of local-only issue database in {}", backupFile);
    try {
      var backupTmp = CompressBackupUtil.backup(entityStore, backupFile.getParent().toFile(), "local_only_issue_backup", false);
      Files.move(backupTmp.toPath(), backupFile, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      LOG.error("Unable to backup local-only issue database", e);
    }
  }

  public void close() {
    backup();
    entityStore.close();
    FileUtils.deleteQuietly(xodusDbDir.toFile());
  }
}
