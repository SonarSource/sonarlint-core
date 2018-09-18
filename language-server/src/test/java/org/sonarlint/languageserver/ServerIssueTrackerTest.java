/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.tracking.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ServerIssueTrackerTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static int counter = 1;

  @Test
  public void get_original_issues_when_there_are_no_server_issues() throws IOException {
    Path baseDir = temporaryFolder.newFolder().toPath();

    Issue issue = mockIssue();
    when(issue.getInputFile().getPath()).thenReturn(baseDir.resolve("dummy").toString());

    Collection<Issue> issues = Collections.singletonList(issue);
    ServerIssueTracker tracker = newTracker(baseDir);

    Collection<Issue> result = matchAndTrack(tracker, "dummy", issues);
    assertThat(result).extracting("issue").isEqualTo(issues);
  }

  @Test
  public void hide_resolved_server_issues() throws IOException {
    Path baseDir = temporaryFolder.newFolder().toPath();
    String dummyFilePath = baseDir.resolve("dummy").toString();

    Issue unresolved = mockIssue();
    when(unresolved.getInputFile().getPath()).thenReturn(dummyFilePath);
    Issue resolved = mockIssue();
    when(resolved.getInputFile().getPath()).thenReturn(dummyFilePath);

    Collection<Issue> issues = Arrays.asList(unresolved, resolved);
    ServerIssue resolvedServerIssue = mockServerIssue(resolved);
    List<ServerIssue> serverIssues = Arrays.asList(mockServerIssue(unresolved), resolvedServerIssue);

    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    when(engine.getServerIssues(any(), any())).thenReturn(serverIssues);

    ServerIssueTracker tracker = newTracker(baseDir, engine);
    Collection<Issue> trackedIssues = matchAndTrack(tracker, "dummy", issues);
    assertThat(trackedIssues).extracting("issue").containsOnlyElementsOf(issues);

    when(resolvedServerIssue.resolution()).thenReturn("CLOSED");
    Collection<Issue> trackedIssues2 = matchAndTrack(tracker, "dummy", issues);
    assertThat(trackedIssues2).extracting("issue").isEqualTo(Collections.singletonList(unresolved));
  }

  @Test
  public void get_severity_and_issue_type_from_matched_server_issue() throws IOException {
    Path baseDir = temporaryFolder.newFolder().toPath();
    String dummyFilePath = baseDir.resolve("dummy").toString();

    Issue unmatched = mockIssue();
    when(unmatched.getInputFile().getPath()).thenReturn(dummyFilePath);
    Issue matched = mockIssue();
    when(matched.getInputFile().getPath()).thenReturn(dummyFilePath);
    Collection<Issue> issues = Arrays.asList(unmatched, matched);

    String serverIssueSeverity = "BLOCKER*";
    String serverIssueType = "BUG*";
    ServerIssue matchedServerIssue = mockServerIssue(matched);
    when(matchedServerIssue.severity()).thenReturn(serverIssueSeverity);
    when(matchedServerIssue.type()).thenReturn(serverIssueType);
    List<ServerIssue> serverIssues = Arrays.asList(mockServerIssue(mockIssue()), matchedServerIssue);

    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    when(engine.getServerIssues(any(), any())).thenReturn(serverIssues);

    ServerIssueTracker tracker = newTracker(baseDir, engine);
    Collection<Issue> trackedIssues = matchAndTrack(tracker, "dummy", issues);

    assertThat(trackedIssues).extracting("ruleKey")
      .containsOnly(unmatched.getRuleKey(), matched.getRuleKey());

    Issue combined = trackedIssues.stream()
      .filter(t -> t.getRuleKey().equals(matched.getRuleKey()))
      .findAny()
      .get();
    assertThat(combined.getSeverity()).isEqualTo(serverIssueSeverity);
    assertThat(combined.getType()).isEqualTo(serverIssueType);
  }

  @Test
  public void do_not_get_server_issues_when_there_are_no_local_issues() throws IOException {
    Path baseDir = temporaryFolder.newFolder().toPath();

    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);

    ServerIssueTracker tracker = newTracker(baseDir, engine);
    matchAndTrack(tracker, "dummy", Collections.emptyList());
    verifyZeroInteractions(engine);
  }

  @Test
  public void fetch_server_issues_when_needed() throws IOException {
    Path baseDir = temporaryFolder.newFolder().toPath();
    String dummyFilePath = baseDir.resolve("dummy").toString();

    Issue issue = mockIssue();
    when(issue.getInputFile().getPath()).thenReturn(dummyFilePath);

    Collection<Issue> issues = Collections.singleton(issue);

    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    ServerIssueTracker tracker = newTracker(baseDir, engine);
    matchAndTrack(tracker, "dummy", issues, false);
    verify(engine).getServerIssues(any(), any());
    verifyNoMoreInteractions(engine);

    engine = mock(ConnectedSonarLintEngine.class);
    tracker = newTracker(baseDir, engine);
    matchAndTrack(tracker, "dummy", issues, true);
    verify(engine).downloadServerIssues(any(), any(), any());
    verifyNoMoreInteractions(engine);
  }

  private Collection<Issue> matchAndTrack(ServerIssueTracker tracker, String filePath, Collection<Issue> issues) {
    return matchAndTrack(tracker, filePath, issues, false);
  }

  private Collection<Issue> matchAndTrack(ServerIssueTracker tracker, String filePath, Collection<Issue> issues, boolean shouldFetchServerIssues) {
    List<Issue> recorded = new LinkedList<>();
    tracker.matchAndTrack(filePath, issues, recorded::add, shouldFetchServerIssues);
    return recorded;
  }

  private ServerIssueTracker newTracker(Path baseDir, ConnectedSonarLintEngine engine) {
    ServerConfiguration serverConfiguration = mock(ServerConfiguration.class);
    String projectKey = "project1";
    Logger logger = mock(Logger.class);
    ProjectBinding projectBinding = new ProjectBinding(projectKey, "", "");
    return new ServerIssueTracker(engine, serverConfiguration, projectBinding, logger);
  }

  private ServerIssueTracker newTracker(Path baseDir) {
    ConnectedSonarLintEngine engine = mock(ConnectedSonarLintEngine.class);
    return newTracker(baseDir, engine);
  }

  // create uniquely identifiable issue
  private Issue mockIssue() {
    Issue issue = mock(Issue.class);

    // basic setup to prevent NPEs
    when(issue.getInputFile()).thenReturn(mock(ClientInputFile.class));
    when(issue.getMessage()).thenReturn("dummy message " + (++counter));

    // make issue match by rule key + line + text range hash
    when(issue.getRuleKey()).thenReturn("dummy ruleKey" + (++counter));
    when(issue.getStartLine()).thenReturn(++counter);
    return issue;
  }

  // copy enough fields so that tracker finds a match
  private ServerIssue mockServerIssue(Issue issue) {
    ServerIssue serverIssue = mock(ServerIssue.class);

    // basic setup to prevent NPEs
    when(serverIssue.creationDate()).thenReturn(Instant.ofEpochMilli(++counter));
    when(serverIssue.resolution()).thenReturn("");
    when(serverIssue.checksum()).thenReturn("dummy checksum " + (++counter));

    // if issue itself is a mock, need to extract value to variable first
    // as Mockito doesn't handle nested mocking inside mocking

    String message = issue.getMessage();
    when(serverIssue.message()).thenReturn(message);

    // copy fields to match during tracking

    String ruleKey = issue.getRuleKey();
    when(serverIssue.ruleKey()).thenReturn(ruleKey);

    Integer startLine = issue.getStartLine();
    when(serverIssue.line()).thenReturn(startLine);

    return serverIssue;
  }

}
