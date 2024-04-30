/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import org.sonarsource.sonarlint.core.commons.KnownIssue;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.tracking.XodusKnownIssuesStore;

import static org.assertj.core.api.Assertions.assertThat;

class XodusKnownIssuesStoreTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path workDir;
  @TempDir
  Path backupDir;
  private XodusKnownIssuesStore store;

  @BeforeEach
  void setUp() throws IOException {
    store = new XodusKnownIssuesStore(backupDir, workDir);
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void should_return_empty_when_file_path_unknown() {
    var issues = store.loadForFile("configScopeId", Path.of("path"));

    assertThat(issues).isEmpty();
  }

  @Test
  void should_save_a_known_issue() {
    var knownIssue = aKnownIssue("serverKey");
    store.storeKnownIssues("configScopeId", Path.of("file/path"), List.of(knownIssue));

    var storedIssues = store.loadForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(knownIssue);
  }

  @Test
  void should_save_a_known_issue_without_server_key() {
    var knownIssue = aKnownIssue(null);
    store.storeKnownIssues("configScopeId", Path.of("file/path"), List.of(knownIssue));

    var storedIssues = store.loadForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(knownIssue);
  }

  @Test
  void should_save_multiple_local_only_issue_to_same_file() {
    var knownIssue1 = aKnownIssue(null);
    var knownIssue2 = aKnownIssue(null);
    var knownIssue3 = aKnownIssue(null);
    store.storeKnownIssues("configScopeId", Path.of("file/path"), List.of(knownIssue1, knownIssue2, knownIssue3));

    var storedIssues = store.loadForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsExactly(knownIssue1, knownIssue2, knownIssue3);
  }

  @Test
  void should_not_load_a_local_only_issue_if_file_path_is_wrong() {
    var knownIssue = aKnownIssue("serverKey");
    store.storeKnownIssues("configScopeId", Path.of("file"), List.of(knownIssue));

    var storedIssues = store.loadForFile("configScopeId", Path.of("wrong/path"));
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void should_not_load_a_local_only_issue_if_config_scope_id_is_wrong() {
    var knownIssue = aKnownIssue("serverKey");
    store.storeKnownIssues("configScopeId", Path.of("file"), List.of(knownIssue));

    var storedIssues = store.loadForFile("wrongConfigScopeId", Path.of("file/path"));
    assertThat(storedIssues).isEmpty();
  }

  @NotNull
  private static KnownIssue aKnownIssue(String serverKey) {
    return new KnownIssue(UUID.randomUUID(), serverKey, new TextRangeWithHash(1, 2, 3, 4, "hash"), new LineWithHash(1, "lineHash"), "ruleKey", "message",
      Instant.now().truncatedTo(ChronoUnit.MILLIS));
  }
}
