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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtobufIssueStoreTests {

  private final AtomicInteger counter = new AtomicInteger();
  private final String key = "filePath";

  @TempDir
  private Path base;

  @Test
  void contains_should_find_issues_after_added() throws IOException {
    var issueStore = newIssueStore();

    assertThat(issueStore.contains(key)).isFalse();

    Collection<Trackable> issues = Arrays.asList(newMockTrackable(), newMockTrackable());
    issueStore.save(key, issues);
    assertThat(issueStore.contains(key)).isTrue();
  }

  @Test
  void contains_should_find_issues_even_if_empty_list() throws IOException {
    var issueStore = newIssueStore();

    assertThat(issueStore.contains(key)).isFalse();

    issueStore.save(key, Collections.emptyList());
    assertThat(issueStore.contains(key)).isTrue();
  }

  @Test
  void save_should_update() throws IOException {
    var issueStore = newIssueStore();

    Collection<Trackable> issues = Arrays.asList(newMockTrackable(), newMockTrackable());
    issueStore.save(key, issues);
    assertThat(issueStore.read(key)).hasSameSizeAs(issues);

    issueStore.save(key, Collections.emptyList());
    assertThat(issueStore.read(key)).isEmpty();
  }

  @Test
  void read_should_return_issues_with_matching_rule_keys() throws IOException {
    var issueStore = newIssueStore();

    Collection<Trackable> issues = Arrays.asList(newMockTrackable(), newMockTrackable());
    issueStore.save(key, issues);

    List<String> ruleKeys = issues.stream().map(Trackable::getRuleKey).collect(Collectors.toList());
    assertThat(issueStore.read(key)).extracting("ruleKey").containsOnlyElementsOf(ruleKeys);
  }

  @Test
  void read_should_return_null_when_no_issues() throws IOException {
    var issueStore = newIssueStore();
    assertThat(issueStore.read("nonexistent")).isNull();
  }

  @Test
  void clear_should_empty_the_store() throws IOException {
    var issueStore = newIssueStore();

    issueStore.save(key, Collections.emptyList());
    assertThat(issueStore.contains(key)).isTrue();

    issueStore.clear();
    assertThat(issueStore.contains(key)).isFalse();
  }

  @Test
  void clean_should_remove_entries_without_valid_files(@TempDir Path base) throws IOException {
    var projectPath = base.resolve("project");
    var issueStore = new ProtobufIssueStore(base.resolve("store"), projectPath);

    var nonexistentFileKey = "nonexistent";
    var validFileKey = "some/relative/path";
    var validFile = projectPath.resolve(validFileKey);
    Files.createDirectories(validFile.getParent());
    Files.createFile(validFile);

    issueStore.save(nonexistentFileKey, Collections.emptyList());
    assertThat(issueStore.contains(nonexistentFileKey)).isTrue();

    issueStore.save(validFileKey, Collections.emptyList());
    assertThat(issueStore.contains(validFileKey)).isTrue();

    issueStore.clean();
    assertThat(issueStore.contains(nonexistentFileKey)).isFalse();
    assertThat(issueStore.contains(validFileKey)).isTrue();
  }

  @Test
  void should_fail_to_create_issue_store_if_cannot_write_to_filesystem(@TempDir Path base) throws IOException {
    var storePath = base.resolve("store");
    // the presence of a file will effectively prevent writing to the store
    Files.createFile(storePath);

    assertThrows(IllegalStateException.class, () -> new ProtobufIssueStore(storePath, base.resolve("project")));
  }

  @Test
  void should_fail_to_save_issues_if_cannot_write_to_filesystem(@TempDir Path base) throws IOException {
    var storePath = base.resolve("store");
    var issueStore = new ProtobufIssueStore(storePath, base.resolve("project"));

    Files.delete(storePath);
    // the presence of a file will effectively prevent writing to the store
    Files.createFile(storePath);

    assertThrows(IllegalStateException.class, () -> issueStore.save(key, Collections.emptyList()));
  }

  private Trackable newMockTrackable() {
    var trackable = mock(Trackable.class);
    when(trackable.getRuleKey()).thenReturn("ruleKey" + counter.incrementAndGet());
    when(trackable.getMessage()).thenReturn("message" + counter.incrementAndGet());

    // just to cover more lines
    when(trackable.getServerIssueKey()).thenReturn("serverIssueKey" + counter.incrementAndGet());

    return trackable;
  }

  private ProtobufIssueStore newIssueStore() {
    var storePath = base.resolve("store");
    var projectPath = base.resolve("project");
    return new ProtobufIssueStore(storePath, projectPath);
  }

}
