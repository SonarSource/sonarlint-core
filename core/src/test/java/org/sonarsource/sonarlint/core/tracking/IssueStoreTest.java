/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueStoreTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final AtomicInteger counter = new AtomicInteger();
  private final  String key = "filePath";

  @Test
  public void contains_should_find_issues_after_added() throws IOException {
    IssueStore issueStore = newIssueStore();

    assertThat(issueStore.contains(key)).isFalse();

    Collection<Trackable> issues = Arrays.asList(newMockTrackable(), newMockTrackable());
    issueStore.save(key, issues);
    assertThat(issueStore.contains(key)).isTrue();
  }

  @Test
  public void contains_should_find_issues_even_if_empty_list() throws IOException {
    IssueStore issueStore = newIssueStore();

    assertThat(issueStore.contains(key)).isFalse();

    issueStore.save(key, Collections.emptyList());
    assertThat(issueStore.contains(key)).isTrue();
  }

  @Test
  public void save_should_update() throws IOException {
    IssueStore issueStore = newIssueStore();

    Collection<Trackable> issues = Arrays.asList(newMockTrackable(), newMockTrackable());
    issueStore.save(key, issues);
    assertThat(issueStore.read(key).size()).isEqualTo(issues.size());

    issueStore.save(key, Collections.emptyList());
    assertThat(issueStore.read(key)).isEmpty();
  }

  @Test
  public void read_should_return_issues_with_matching_rule_keys() throws IOException {
    IssueStore issueStore = newIssueStore();

    Collection<Trackable> issues = Arrays.asList(newMockTrackable(), newMockTrackable());
    issueStore.save(key, issues);

    List<String> ruleKeys = issues.stream().map(Trackable::getRuleKey).collect(Collectors.toList());
    assertThat(issueStore.read(key)).extracting("ruleKey").containsOnlyElementsOf(ruleKeys);
  }

  @Test
  public void read_should_return_null_when_no_issues() throws IOException {
    IssueStore issueStore = newIssueStore();
    assertThat(issueStore.read("nonexistent")).isNull();
  }

  @Test
  public void clear_should_empty_the_store() throws IOException {
    IssueStore issueStore = newIssueStore();

    issueStore.save(key, Collections.emptyList());
    assertThat(issueStore.contains(key)).isTrue();

    issueStore.clear();
    assertThat(issueStore.contains(key)).isFalse();
  }

  @Test
  public void clean_should_remove_entries_without_valid_files() throws IOException {
    Path base = temporaryFolder.newFolder().toPath();
    Path projectPath = base.resolve("project");
    IssueStore issueStore = new IssueStore(base.resolve("store"), projectPath, mock(Logger.class));

    String nonexistentFileKey = "nonexistent";
    String validFileKey = "some/relative/path";
    Path validFile = projectPath.resolve(validFileKey);
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

  @Test(expected = IllegalStateException.class)
  public void should_fail_to_create_issue_store_if_cannot_write_to_filesystem() throws IOException {
    Path base = temporaryFolder.newFolder().toPath();
    Path storePath = base.resolve("store");
    // the presence of a file will effectively prevent writing to the store
    Files.createFile(storePath);

    new IssueStore(storePath, base.resolve("project"), mock(Logger.class));
  }

  @Test(expected = IllegalStateException.class)
  public void should_fail_to_save_issues_if_cannot_write_to_filesystem() throws IOException {
    Path base = temporaryFolder.newFolder().toPath();
    Path storePath = base.resolve("store");
    IssueStore issueStore = new IssueStore(storePath, base.resolve("project"), mock(Logger.class));

    Files.delete(storePath);
    // the presence of a file will effectively prevent writing to the store
    Files.createFile(storePath);
    issueStore.save(key, Collections.emptyList());
  }

  private Trackable newMockTrackable() {
    Trackable trackable = mock(Trackable.class);
    when(trackable.getRuleKey()).thenReturn("ruleKey" + counter.incrementAndGet());
    when(trackable.getMessage()).thenReturn("message" + counter.incrementAndGet());

    // just to cover more lines
    when(trackable.getAssignee()).thenReturn("assignee" + counter.incrementAndGet());
    when(trackable.getServerIssueKey()).thenReturn("serverIssueKey" + counter.incrementAndGet());

    return trackable;
  }

  private IssueStore newIssueStore() throws IOException {
    Path base = temporaryFolder.newFolder().toPath();
    Path storePath = base.resolve("store");
    Path projectPath = base.resolve("project");
    return new IssueStore(storePath, projectPath, mock(Logger.class));
  }

}
