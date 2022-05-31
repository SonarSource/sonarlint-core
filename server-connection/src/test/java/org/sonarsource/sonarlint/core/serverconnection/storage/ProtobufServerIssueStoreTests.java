/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue.TextRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;

class ProtobufServerIssueStoreTests {

  private final String path1 = "path1";

  @TempDir
  private Path root;
  private ProtobufServerIssueStore store;

  @BeforeEach
  void start() {
    store = new ProtobufServerIssueStore(root);
  }

  @Test
  void should_read_object_written() {
    var issue1 = aServerIssue().setFilePath(path1);
    String path2 = "path2";
    var issue2 = aServerIssue().setFilePath(path2);

    store.save(List.of(issue1, issue2));

    assertThat(store.load(path1))
      .extracting(ServerIssue::getFilePath)
      .containsOnly(path1);
    assertThat(store.load(path2))
      .extracting(ServerIssue::getFilePath)
      .containsOnly(path2);
    assertThat(store.load("nonexistent")).isEmpty();
  }

  @Test
  void should_read_object_replaced() {
    var issue1 = aServerIssue()
      .setFilePath(path1)
      .setRuleKey("repo:key")
      .setTextRange(new ServerIssue.TextRange(11));
    var issue2 = aServerIssue()
      .setFilePath(path1)
      .setRuleKey("repo:key2")
      .setTextRange(new TextRange(22));

    store.save(Collections.singletonList(issue1));
    assertThat(store.load(path1))
      .extracting(ServerIssue::getFilePath)
      .containsOnly(path1);

    store.save(Collections.singletonList(issue2));
    assertThat(store.load(path1))
      .extracting(ServerIssue::ruleKey)
      .containsOnly("repo:key2");
  }

  @Test
  void should_fail_to_save_object_if_cannot_write_to_filesystem() {
    // the sha1sum of path
    var sha1sum = "074aeb9c5551d3b52d26cf3d6568599adbff99f1";

    // Create a directory at the path where ServerIssueStore would want to create a file,
    // in order to obstruct the file creation
    var wouldBeFile = root.resolve("0").resolve("7").resolve(sha1sum);
    if (!wouldBeFile.toFile().mkdirs()) {
      fail("could not create dummy directory");
    }
    var issues = List.of(aServerIssue().setFilePath(path1));

    assertThrows(StorageException.class, () -> store.save(issues));
  }
}
