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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class XodusServerIssueStoreMigrationTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  @TempDir
  Path workDir;
  @TempDir
  Path backupDir;

  @Test
  void should_perform_migrations_on_empty_store() throws IOException {
    // Initialize without migration
    XodusServerIssueStore previousVersionStore = null;
    try {
      previousVersionStore = new XodusServerIssueStore(backupDir, workDir, txn -> {});
      assertThat(previousVersionStore.getCurrentSchemaVersion()).isZero();
    } finally {
      if (previousVersionStore != null) {
        previousVersionStore.close();
      }
    }

    // Initialize with migration
    XodusServerIssueStore migratedStore = null;
    try {
      migratedStore = new XodusServerIssueStore(backupDir, workDir);
      assertThat(migratedStore.getCurrentSchemaVersion()).isEqualTo(XodusServerIssueStore.CURRENT_SCHEMA_VERSION);
    } finally {
      if (migratedStore != null) {
        migratedStore.close();
      }
    }
  }

  @Test
  void should_no_nothing_on_already_migrated_store() throws IOException {
    XodusServerIssueStore newStore = null;
    try {
      newStore = new XodusServerIssueStore(backupDir, workDir);
      assertThat(newStore.getCurrentSchemaVersion()).isEqualTo(XodusServerIssueStore.CURRENT_SCHEMA_VERSION);
    } finally {
      if (newStore != null) {
        newStore.close();
      }
    }

    XodusServerIssueStore sameStore = null;
    try {
      sameStore = new XodusServerIssueStore(backupDir, workDir);
      assertThat(sameStore.getCurrentSchemaVersion()).isEqualTo(XodusServerIssueStore.CURRENT_SCHEMA_VERSION);
    } finally {
      if (sameStore != null) {
        sameStore.close();
      }
    }
  }

  @Test
  void should_migrate_v0_to_v1() throws IOException {
    XodusServerIssueStore storeV0 = null;
    try {
      storeV0 = new XodusServerIssueStore(backupDir, workDir, txn -> {});
      // Emulate previous synchronization
      storeV0.mergeTaintIssues("somebranch", List.of(ServerIssueFixtures.aServerTaintIssue()), Set.of(), Instant.now());
      assertThat(storeV0.getCurrentSchemaVersion()).isZero();
      assertThat(storeV0.getLastTaintSyncTimestamp("somebranch"))
        .hasValueSatisfying(instant -> assertThat(instant).isAfter(Instant.EPOCH));
    } finally {
      if (storeV0 != null) {
        storeV0.close();
      }
    }

    XodusServerIssueStore storeV1 = null;
    try {
      storeV1 = new XodusServerIssueStore(backupDir, workDir);
      assertThat(storeV1.getCurrentSchemaVersion()).isEqualTo(XodusServerIssueStore.CURRENT_SCHEMA_VERSION);
      assertThat(storeV1.getLastTaintSyncTimestamp("somebranch"))
        .hasValueSatisfying(instant -> assertThat(instant).isEqualTo(Instant.EPOCH));
    } finally {
      if (storeV1 != null) {
        storeV1.close();
      }
    }
  }
}
