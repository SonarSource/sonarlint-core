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
package org.sonarsource.sonarlint.core.serverconnection.events.issue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStoresManager;
import testutils.InMemoryIssueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerTaintIssue;

class UpdateStorageOnIssueChangedTests {

  private static final String PROJECT_KEY = "projectKey";
  private InMemoryIssueStore serverIssueStore;
  private UpdateStorageOnIssueChanged handler;

  @BeforeEach
  void setUp() {
    serverIssueStore = new InMemoryIssueStore();
    ServerIssueStoresManager manager = mock(ServerIssueStoresManager.class);
    when(manager.get(PROJECT_KEY)).thenReturn(serverIssueStore);
    handler = new UpdateStorageOnIssueChanged(manager);
  }

  @Test
  void should_store_resolved_issue() {
    serverIssueStore.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setResolved(false)));

    handler.handle(new IssueChangedEvent(PROJECT_KEY, List.of("key1"), null, null, true));

    assertThat(serverIssueStore.load("branch", "file/path"))
      .extracting(ServerIssue::isResolved)
      .containsOnly(true);
  }

  @Test
  void should_store_issue_with_updated_severity() {
    serverIssueStore.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setUserSeverity(IssueSeverity.MAJOR)));

    handler.handle(new IssueChangedEvent(PROJECT_KEY, List.of("key1"), IssueSeverity.MINOR, null, null));

    assertThat(serverIssueStore.load("branch", "file/path"))
      .extracting(ServerIssue::getUserSeverity)
      .containsOnly(IssueSeverity.MINOR);
  }

  @Test
  void should_store_issue_with_updated_type() {
    serverIssueStore.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setType(RuleType.VULNERABILITY)));

    handler.handle(new IssueChangedEvent(PROJECT_KEY, List.of("key1"), null, RuleType.BUG, null));

    assertThat(serverIssueStore.load("branch", "file/path"))
      .extracting(ServerIssue::getType)
      .containsOnly(RuleType.BUG);
  }

  @Test
  void should_store_resolved_taint_issue() {
    serverIssueStore.replaceAllTaintOfFile("branch", "file/path", List.of(aServerTaintIssue().setKey("key1").setResolved(false)));

    handler.handle(new IssueChangedEvent(PROJECT_KEY, List.of("key1"), null, null, true));

    assertThat(serverIssueStore.loadTaint("branch", "file/path"))
      .extracting(ServerTaintIssue::isResolved)
      .containsOnly(true);
  }

  @Test
  void should_store_taint_issue_with_updated_severity() {
    serverIssueStore.replaceAllTaintOfFile("branch", "file/path", List.of(aServerTaintIssue().setKey("key1").setSeverity(IssueSeverity.MAJOR)));

    handler.handle(new IssueChangedEvent(PROJECT_KEY, List.of("key1"), IssueSeverity.MINOR, null, null));

    assertThat(serverIssueStore.loadTaint("branch", "file/path"))
      .extracting(ServerTaintIssue::getSeverity)
      .containsOnly(IssueSeverity.MINOR);
  }

  @Test
  void should_store_taint_issue_with_updated_type() {
    serverIssueStore.replaceAllTaintOfFile("branch", "file/path", List.of(aServerTaintIssue().setKey("key1").setType(RuleType.VULNERABILITY)));

    handler.handle(new IssueChangedEvent(PROJECT_KEY, List.of("key1"), null, RuleType.BUG, null));

    assertThat(serverIssueStore.loadTaint("branch", "file/path"))
      .extracting(ServerTaintIssue::getType)
      .containsOnly(RuleType.BUG);
  }

}
