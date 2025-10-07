/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.storage.InstantBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;
import org.sonarsource.sonarlint.core.serverconnection.storage.UuidBinding;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.commons.storage.XodusPurgeUtils.purgeOldTemporaryFiles;

public class XodusKnownFindingsStore {

  private static final String KNOWN_FINDINGS_STORE = "known-findings-store";
  private static final Integer PURGE_NUMBER_OF_DAYS = 3;
  private static final String CONFIGURATION_SCOPE_ID_ENTITY_TYPE = "Scope";
  private static final String CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME = "files";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String PATH_PROPERTY_NAME = "path";
  private static final String NAME_PROPERTY_NAME = "name";
  private static final String FINDING_ENTITY_TYPE = "Finding";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String FILE_TO_SECURITY_HOTSPOTS_LINK_NAME = "hotspots";
  private static final String UUID_PROPERTY_NAME = "uuid";
  private static final String SERVER_KEY_PROPERTY_NAME = "serverKey";
  private static final String FINDING_TO_FILE_LINK_NAME = "file";
  private static final String INTRODUCTION_DATE_PROPERTY_NAME = "introductionDate";
  private static final String RULE_KEY_PROPERTY_NAME = "ruleKey";
  private static final String RANGE_HASH_PROPERTY_NAME = "rangeHash";
  private static final String LINE_HASH_PROPERTY_NAME = "lineHash";
  private static final String START_LINE_PROPERTY_NAME = "startRow";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endRow";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String MESSAGE_BLOB_NAME = "message";
  private static final String BACKUP_TAR_GZ = "known_findings_backup.tar.gz";
  private final PersistentEntityStore entityStore;
  private final Path backupFile;
  private final Path xodusDbDir;
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public XodusKnownFindingsStore(Path backupDir, Path workDir) throws IOException {
    xodusDbDir = Files.createTempDirectory(workDir, KNOWN_FINDINGS_STORE);
    purgeOldTemporaryFiles(workDir, PURGE_NUMBER_OF_DAYS, KNOWN_FINDINGS_STORE + "*");
    backupFile = backupDir.resolve(BACKUP_TAR_GZ);
    if (Files.isRegularFile(backupFile)) {
      LOG.debug("Restoring previous known findings database from {}", backupFile);
      try {
        TarGzUtils.extractTarGz(backupFile, xodusDbDir);
      } catch (Exception e) {
        LOG.error("Unable to restore known findings backup {}", backupFile);
      }
    }
    LOG.debug("Starting known findings database from {}", xodusDbDir);
    this.entityStore = buildEntityStore();
    entityStore.executeInTransaction(txn -> {
      entityStore.registerCustomPropertyType(txn, Instant.class, new InstantBinding());
      entityStore.registerCustomPropertyType(txn, UUID.class, new UuidBinding());
    });
  }

  public List<KnownFinding> loadIssuesForFile(String configurationScopeId, Path filePath) {
    return loadFindingsForFile(configurationScopeId, filePath, FILE_TO_ISSUES_LINK_NAME);
  }

  public List<KnownFinding> loadSecurityHotspotsForFile(String configurationScopeId, Path filePath) {
    return loadFindingsForFile(configurationScopeId, filePath, FILE_TO_SECURITY_HOTSPOTS_LINK_NAME);
  }

  private List<KnownFinding> loadFindingsForFile(String configurationScopeId, Path filePath, String fileToFindingsLinkName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, CONFIGURATION_SCOPE_ID_ENTITY_TYPE, NAME_PROPERTY_NAME, configurationScopeId)
      .map(configScopeId -> configScopeId.getLinks(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME))
      .flatMap(files -> findUniqueAmong(files, PATH_PROPERTY_NAME, filePath.toString()))
      .map(fileToLoad -> fileToLoad.getLinks(fileToFindingsLinkName))
      .map(findingEntities -> StreamSupport.stream(findingEntities.spliterator(), false)
        .map(XodusKnownFindingsStore::adapt)
        .toList())
      .orElseGet(Collections::emptyList));
  }

  private static KnownFinding adapt(Entity storedFinding) {
    var uuid = (UUID) requireNonNull(storedFinding.getProperty(UUID_PROPERTY_NAME));
    var serverKey = (String) storedFinding.getProperty(SERVER_KEY_PROPERTY_NAME);
    var introductionDate = (Instant) requireNonNull(storedFinding.getProperty(INTRODUCTION_DATE_PROPERTY_NAME));
    var ruleKey = (String) requireNonNull(storedFinding.getProperty(RULE_KEY_PROPERTY_NAME));
    var msg = requireNonNull(storedFinding.getBlobString(MESSAGE_BLOB_NAME));
    var startLine = (Integer) storedFinding.getProperty(START_LINE_PROPERTY_NAME);

    TextRangeWithHash textRange = null;
    LineWithHash lineWithHash = null;
    if (startLine != null) {
      var rangeHash = (String) storedFinding.getProperty(RANGE_HASH_PROPERTY_NAME);
      if (rangeHash != null) {
        var startLineOffset = (Integer) storedFinding.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
        var endLine = (Integer) storedFinding.getProperty(END_LINE_PROPERTY_NAME);
        var endLineOffset = (Integer) storedFinding.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
        textRange = new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, rangeHash);
      }
      var lineHash = (String) storedFinding.getProperty(LINE_HASH_PROPERTY_NAME);
      if (lineHash != null) {
        lineWithHash = new LineWithHash(startLine, lineHash);
      }
    }
    return new KnownFinding(
      uuid,
      serverKey,
      textRange,
      lineWithHash,
      ruleKey,
      msg,
      introductionDate);
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

  private static Optional<Entity> findUnique(StoreTransaction transaction, String entityType, String propertyName, Comparable<?> caseSensitivePropertyValue) {
    // the find is case-insensitive but we need an exact match
    var entities = transaction.find(entityType, propertyName, caseSensitivePropertyValue);
    return findUniqueAmong(entities, propertyName, caseSensitivePropertyValue);
  }

  private static Optional<Entity> findUniqueAmong(EntityIterable iterable, String propertyName, Comparable<?> caseSensitivePropertyValue) {
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

  private static Entity getOrCreateFile(Entity moduleEntity, Path filePath, StoreTransaction txn) {
    return findUniqueAmong(moduleEntity.getLinks(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME), PATH_PROPERTY_NAME, filePath.toString())
      .orElseGet(() -> {
        var file = txn.newEntity(FILE_ENTITY_TYPE);
        file.setProperty(PATH_PROPERTY_NAME, filePath.toString());
        moduleEntity.addLink(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME, file);
        return file;
      });
  }

  private static void updateOrCreateFinding(Entity fileEntity, KnownFinding finding, String fileToFindingsLinkName, StoreTransaction transaction) {
    var findingEntity = findUnique(transaction, FINDING_ENTITY_TYPE, UUID_PROPERTY_NAME, finding.getId())
      .orElseGet(() -> transaction.newEntity(FINDING_ENTITY_TYPE));
    var oldFileEntity = findingEntity.getLink(FINDING_TO_FILE_LINK_NAME);
    if (oldFileEntity != null && !fileEntity.equals(oldFileEntity)) {
      // finding might have moved file
      oldFileEntity.deleteLink(fileToFindingsLinkName, findingEntity);
    }
    findingEntity.setLink(FINDING_TO_FILE_LINK_NAME, fileEntity);
    fileEntity.addLink(fileToFindingsLinkName, findingEntity);
    findingEntity.setProperty(UUID_PROPERTY_NAME, finding.getId());
    var serverKey = finding.getServerKey();
    if (serverKey != null) {
      findingEntity.setProperty(SERVER_KEY_PROPERTY_NAME, serverKey);
    } else {
      findingEntity.deleteProperty(SERVER_KEY_PROPERTY_NAME);
    }
    findingEntity.setProperty(RULE_KEY_PROPERTY_NAME, finding.getRuleKey());
    findingEntity.setBlobString(MESSAGE_BLOB_NAME, finding.getMessage());
    findingEntity.setProperty(INTRODUCTION_DATE_PROPERTY_NAME, finding.getIntroductionDate());
    var textRange = finding.getTextRangeWithHash();
    var lineWithHash = finding.getLineWithHash();

    if (textRange != null) {
      findingEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      findingEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      findingEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      findingEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      findingEntity.setProperty(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
    }

    if (lineWithHash != null) {
      findingEntity.setProperty(LINE_HASH_PROPERTY_NAME, lineWithHash.getHash());
      findingEntity.setProperty(START_LINE_PROPERTY_NAME, lineWithHash.getNumber());
    }
  }

  public void backup() {
    LOG.debug("Creating backup of known findings database in {}", backupFile);
    try {
      var backupTmp = CompressBackupUtil.backup(entityStore, backupFile.getParent().toFile(), "known_findings_backup", false);
      Files.move(backupTmp.toPath(), backupFile, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      LOG.error("Unable to backup known findings database", e);
    }
  }

  public void close() {
    backup();
    entityStore.close();
    FileUtils.deleteQuietly(xodusDbDir.toFile());
  }

  public void storeKnownIssues(String configurationScopeId, Path clientRelativePath, List<KnownFinding> newKnownIssues) {
    storeKnownFindings(configurationScopeId, clientRelativePath, newKnownIssues, FILE_TO_ISSUES_LINK_NAME);
  }

  public void storeKnownSecurityHotspots(String configurationScopeId, Path clientRelativePath, List<KnownFinding> newKnownSecurityHotspots) {
    storeKnownFindings(configurationScopeId, clientRelativePath, newKnownSecurityHotspots, FILE_TO_SECURITY_HOTSPOTS_LINK_NAME);
  }

  private void storeKnownFindings(String configurationScopeId, Path clientRelativePath, List<KnownFinding> newKnownFindings, String fileToFindingsLinkName) {
    entityStore.executeInTransaction(txn -> {
      var configurationScope = getOrCreateConfigurationScopeId(configurationScopeId, txn);
      var fileEntity = getOrCreateFile(configurationScope, clientRelativePath, txn);
      fileEntity.getLinks(fileToFindingsLinkName).forEach(Entity::delete);
      fileEntity.deleteLinks(fileToFindingsLinkName);
      newKnownFindings.forEach(finding -> updateOrCreateFinding(fileEntity, finding, fileToFindingsLinkName, txn));
    });
  }
}
