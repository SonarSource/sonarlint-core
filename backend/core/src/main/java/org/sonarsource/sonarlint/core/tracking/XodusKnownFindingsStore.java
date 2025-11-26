/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.issues.Findings;
import org.sonarsource.sonarlint.core.serverconnection.storage.InstantBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.TarGzUtils;
import org.sonarsource.sonarlint.core.serverconnection.storage.UuidBinding;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

public class XodusKnownFindingsStore {

  static final String KNOWN_FINDINGS_STORE = "known-findings-store";
  private static final String CONFIGURATION_SCOPE_ID_ENTITY_TYPE = "Scope";
  private static final String CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME = "files";
  private static final String PATH_PROPERTY_NAME = "path";
  private static final String NAME_PROPERTY_NAME = "name";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String FILE_TO_SECURITY_HOTSPOTS_LINK_NAME = "hotspots";
  private static final String UUID_PROPERTY_NAME = "uuid";
  private static final String SERVER_KEY_PROPERTY_NAME = "serverKey";
  private static final String INTRODUCTION_DATE_PROPERTY_NAME = "introductionDate";
  private static final String RULE_KEY_PROPERTY_NAME = "ruleKey";
  private static final String RANGE_HASH_PROPERTY_NAME = "rangeHash";
  private static final String LINE_HASH_PROPERTY_NAME = "lineHash";
  private static final String START_LINE_PROPERTY_NAME = "startLine";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endLine";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String MESSAGE_BLOB_NAME = "message";
  static final String BACKUP_TAR_GZ = "known_findings_backup.tar.gz";
  private final PersistentEntityStore entityStore;
  private final Path xodusDbDir;
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public XodusKnownFindingsStore(Path backupDir, Path workDir) throws IOException {
    xodusDbDir = Files.createTempDirectory(workDir, KNOWN_FINDINGS_STORE);
    var backupFile = backupDir.resolve(BACKUP_TAR_GZ);
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

  public Map<String, Map<Path, Findings>> loadAll() {
    return entityStore.computeInReadonlyTransaction(txn -> StreamSupport.stream(txn.getAll(CONFIGURATION_SCOPE_ID_ENTITY_TYPE).spliterator(), false)
      .collect(groupingBy(
        e -> (String) requireNonNull(e.getProperty(NAME_PROPERTY_NAME)),
        flatMapping(e -> StreamSupport.stream(e.getLinks(CONFIGURATION_SCOPE_ID_TO_FILES_LINK_NAME).spliterator(), false),
          toMap(
            f -> Paths.get((String) requireNonNull(f.getProperty(PATH_PROPERTY_NAME))),
            f -> new Findings(
              StreamSupport.stream(f.getLinks(FILE_TO_ISSUES_LINK_NAME).spliterator(), false)
                .map(XodusKnownFindingsStore::adapt).toList(),
              StreamSupport.stream(f.getLinks(FILE_TO_SECURITY_HOTSPOTS_LINK_NAME).spliterator(), false)
                .map(XodusKnownFindingsStore::adapt).toList()),
            Findings::mergeWith)))));
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

  public void close() {
    entityStore.close();
    FileUtils.deleteQuietly(xodusDbDir.toFile());
  }
}
