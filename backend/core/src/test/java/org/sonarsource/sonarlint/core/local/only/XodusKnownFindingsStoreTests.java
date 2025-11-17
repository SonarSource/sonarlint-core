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
package org.sonarsource.sonarlint.core.local.only;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.tracking.XodusKnownFindingsStore;

import static org.assertj.core.api.Assertions.assertThat;

class XodusKnownFindingsStoreTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path workDir;
  @TempDir
  Path backupDir;
  private XodusKnownFindingsStore store;

  @BeforeEach
  void setUp() throws IOException {
    store = new XodusKnownFindingsStore(backupDir, workDir);
  }

  @AfterEach
  void tearDown() {
    store.backupAndClose();
  }

  @Test
  void should_return_empty_issues_when_file_path_unknown() {
    var issues = store.loadIssuesForFile("configScopeId", Path.of("path"));

    assertThat(issues).isEmpty();
  }

  @Test
  void should_return_empty_hotspots_when_file_path_unknown() {
    var hotspots = store.loadSecurityHotspotsForFile("configScopeId", Path.of("path"));

    assertThat(hotspots).isEmpty();
  }

  @Test
  void should_save_a_known_issue() {
    var knownIssue = aKnownFinding("serverKey");
    store.storeKnownIssues("configScopeId", Path.of("file/path"), List.of(knownIssue));

    var storedIssues = store.loadIssuesForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(knownIssue);
  }

  @Test
  void should_save_a_known_hotspot() {
    var knownSecurityHotspot = aKnownFinding("serverKey");
    store.storeKnownSecurityHotspots("configScopeId", Path.of("file/path"), List.of(knownSecurityHotspot));

    var storedSecurityHotspots = store.loadSecurityHotspotsForFile("configScopeId", Path.of("file/path"));
    assertThat(storedSecurityHotspots).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(knownSecurityHotspot);
  }

  @Test
  void should_save_a_known_issue_without_server_key() {
    var knownIssue = aKnownFinding(null);
    store.storeKnownIssues("configScopeId", Path.of("file/path"), List.of(knownIssue));

    var storedIssues = store.loadIssuesForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(knownIssue);
  }

  @Test
  void should_save_a_known_hotspot_without_server_key() {
    var knownSecurityHotspot = aKnownFinding(null);
    store.storeKnownSecurityHotspots("configScopeId", Path.of("file/path"), List.of(knownSecurityHotspot));

    var storedSecurityHotspots = store.loadSecurityHotspotsForFile("configScopeId", Path.of("file/path"));
    assertThat(storedSecurityHotspots).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(knownSecurityHotspot);
  }

  @Test
  void should_save_multiple_known_issues_to_same_file() {
    var knownIssue1 = aKnownFinding(null);
    var knownIssue2 = aKnownFinding(null);
    var knownIssue3 = aKnownFinding(null);
    store.storeKnownIssues("configScopeId", Path.of("file/path"), List.of(knownIssue1, knownIssue2, knownIssue3));

    var storedIssues = store.loadIssuesForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsExactly(knownIssue1, knownIssue2, knownIssue3);
  }

  @Test
  void should_save_multiple_known_hotspots_to_same_file() {
    var knownSecurityHotspot1 = aKnownFinding(null);
    var knownSecurityHotspot2 = aKnownFinding(null);
    var knownSecurityHotspot3 = aKnownFinding(null);
    store.storeKnownSecurityHotspots("configScopeId", Path.of("file/path"), List.of(knownSecurityHotspot1, knownSecurityHotspot2, knownSecurityHotspot3));

    var storedSecurityHotspots = store.loadSecurityHotspotsForFile("configScopeId", Path.of("file/path"));
    assertThat(storedSecurityHotspots).usingRecursiveFieldByFieldElementComparator()
      .containsExactly(knownSecurityHotspot1, knownSecurityHotspot2, knownSecurityHotspot3);
  }

  @Test
  void should_not_load_a_known_issue_if_file_path_is_wrong() {
    var knownIssue = aKnownFinding("serverKey");
    store.storeKnownIssues("configScopeId", Path.of("file"), List.of(knownIssue));

    var storedIssues = store.loadIssuesForFile("configScopeId", Path.of("wrong/path"));
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void should_not_load_a_known_hotspot_if_file_path_is_wrong() {
    var knownSecurityHotspot = aKnownFinding("serverKey");
    store.storeKnownSecurityHotspots("configScopeId", Path.of("file"), List.of(knownSecurityHotspot));

    var storedSecurityHotspots = store.loadSecurityHotspotsForFile("configScopeId", Path.of("wrong/path"));
    assertThat(storedSecurityHotspots).isEmpty();
  }

  @Test
  void should_not_load_a_known_issue_if_config_scope_id_is_wrong() {
    var knownIssue = aKnownFinding("serverKey");
    store.storeKnownIssues("configScopeId", Path.of("file"), List.of(knownIssue));

    var storedIssues = store.loadIssuesForFile("wrongConfigScopeId", Path.of("file/path"));
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void should_not_load_a_known_hotspot_if_config_scope_id_is_wrong() {
    var knownSecurityHotspot = aKnownFinding("serverKey");
    store.storeKnownSecurityHotspots("configScopeId", Path.of("file"), List.of(knownSecurityHotspot));

    var storedSecurityHotspot = store.loadSecurityHotspotsForFile("wrongConfigScopeId", Path.of("file/path"));
    assertThat(storedSecurityHotspot).isEmpty();
  }

  @Test
  void should_purge_old_folders() throws IOException {
    store.backupAndClose();
    var oldFile = Files.createTempFile(workDir, "known-findings-store", UUID.randomUUID().toString());
    var file = oldFile.toFile();
    var oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
    file.setLastModified(oneWeekAgo);

    store = new XodusKnownFindingsStore(backupDir, workDir);

    assertThat(Files.exists(oldFile)).isFalse();
  }

  @NotNull
  private static KnownFinding aKnownFinding(String serverKey) {
    return new KnownFinding(UUID.randomUUID(), serverKey, new TextRangeWithHash(1, 2, 3, 4, "hash"), new LineWithHash(1, "lineHash"), "ruleKey", "message",
      Instant.now().truncatedTo(ChronoUnit.MILLIS));
  }
}
