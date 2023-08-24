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
package mediumtest.issues;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenIssueParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ClientTrackedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issue.AddIssueCommentException;
import org.sonarsource.sonarlint.core.issue.IssueStatusChangeException;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.ServerFixture.ServerStatus.DOWN;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueFixtures.aLocalOnlyIssueResolved;

class IssuesStatusChangeMediumTests {

  private SonarLintTestBackend backend;
  private ServerFixture.Server server;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  @Test
  void it_should_update_the_status_on_sonarqube_when_changing_the_status_on_a_server_matched_issue() {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/issues/do_transition");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("issue=myIssueKey&transition=wontfix");
  }

  @Test
  void it_should_update_the_telemetry_when_changing_the_status_on_a_server_matched_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey",
          project -> project.withBranch("main",
            branch -> branch.withIssue(
              aServerIssue("myIssueKey")
                .withRuleKey("rule:key")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
                .withIntroductionDate(Instant.EPOCH.plusSeconds(1))
                .withType(RuleType.BUG))))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"issueStatusChangedRuleKeys\":[\"rule:key\"]");
  }

  @Test
  void it_should_fail_the_future_when_the_server_returns_an_error() {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(IssueStatusChangeException.class)
      .withMessage("Cannot change status on the issue");
  }

  @Test
  void it_should_update_local_only_storage_when_the_issue_exists_locally() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .withActiveBranch("configScopeId", "branch")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    LocalOnlyIssueDto localOnlyIssue = null;
    try {
      localOnlyIssue = trackedIssues.get().getIssuesByServerRelativePath().get("file/path").get(0).getRight();
    } catch (Exception e) {
      fail();
    }

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var issueLoaded = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", "file/path");
    assertThat(issueLoaded).hasSize(1);
    assertThat(issueLoaded.get(0).getId()).isEqualTo(localOnlyIssue.getId());
    assertThat(issueLoaded.get(0).getResolution().getStatus()).isEqualTo(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX);
  }

  @Test
  void it_should_sync_anticipated_transitions_with_sonarqube_when_the_issue_exists_locally() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .withActiveBranch("configScopeId", "branch")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    LocalOnlyIssueDto localOnlyIssue = null;
    try {
      localOnlyIssue = trackedIssues.get().getIssuesByServerRelativePath().get("file/path").get(0).getRight();
    } catch (Exception e) {
      fail();
    }

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/issues/anticipated_transitions?projectKey=projectKey");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(
      "[{\"filePath\":\"file/path\",\"line\":1,\"hash\":\"linehash\",\"ruleKey\":\"ruleKey\",\"issueMessage\":\"message\",\"transition\":\"wontfix\"}]");
  }

  @Test
  void it_should_update_telemetry_when_changing_status_of_a_local_only_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withActiveBranch("configScopeId", "branch")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();
    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    LocalOnlyIssueDto localOnlyIssue = null;
    try {
      localOnlyIssue = trackedIssues.get().getIssuesByServerRelativePath().get("file/path").get(0).getRight();
    } catch (Exception e) {
      fail();
    }

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"issueStatusChangedRuleKeys\":[\"ruleKey\"]");
  }

  @Test
  void it_should_fail_when_the_issue_does_not_exists() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var params = new ChangeIssueStatusParams("configScopeId", "myIssueKey", ResolutionStatus.WONT_FIX, false);
    var issueService = backend.getIssueService();
    var thrown = assertThrows(IssueStatusChangeException.class, () -> issueService.changeStatus(params));

    assertThat(thrown).hasMessageStartingWith("Cannot change status on the issue: Issue key myIssueKey was not found");
  }

  @Test
  void it_should_add_new_comment_to_server_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/issues/add_comment");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("issue=myIssueKey&text=That%27s+serious+issue");
  }

  @Test
  void it_should_add_new_comment_to_resolved_local_only_issue() {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", issueId.toString(), "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", "file/path");
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getResolution)
      .extracting(LocalOnlyIssueResolution::getComment)
      .containsOnly("That's serious issue");
  }

  @Test
  void it_should_throw_if_server_response_is_not_OK_during_add_new_comment_to_issue() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(AddIssueCommentException.class)
      .withMessage("Cannot add comment to the issue");
  }

  @Test
  void it_should_reopen_issue_by_id() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", issueId.toString()));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isIssueReopened()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void it_should_load_issues() {
    server = newSonarQubeServer().start();
    var issueId1 = UUID.randomUUID();
    var issueId2 = UUID.randomUUID();
    var otherFileIssueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey",
        storage -> storage
          .withLocalOnlyIssue(new LocalOnlyIssue(
            otherFileIssueId,
            "file/path1",
            new TextRangeWithHash(1, 2, 3, 4, "ab12"),
            new LineWithHash(1, "linehash"),
            "ruleKey",
            "message",
            new LocalOnlyIssueResolution(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX, Instant.now().truncatedTo(ChronoUnit.MILLIS), "comment")
          ))
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId1))
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId2)))
      .build();

    var issuesForFile = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", "file/path");
    var issuesForOtherFile = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", "file/path1");
    var allIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(issuesForFile).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2);
    assertThat(issuesForOtherFile).extracting(LocalOnlyIssue::getId).containsOnly(otherFileIssueId);
    assertThat(allIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2, otherFileIssueId);
  }


  @Test
  void it_should_reopen_all_issues_for_file() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId1 = UUID.randomUUID();
    var issueId2 = UUID.randomUUID();
    var otherFileIssueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey",
        storage -> storage
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId1))
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId2))
          .withLocalOnlyIssue(new LocalOnlyIssue(
            otherFileIssueId,
            "file/path1",
            new TextRangeWithHash(1, 2, 3, 4, "ab12"),
            new LineWithHash(1, "linehash"),
            "ruleKey",
            "message",
            new LocalOnlyIssueResolution(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX, Instant.now().truncatedTo(ChronoUnit.MILLIS), "comment")
          )))
      .build();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2, otherFileIssueId);

    var response = backend.getIssueService()
      .reopenAllIssuesForFile(new ReopenAllIssuesForFileParams("configScopeId", "file/path"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isIssueReopened()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(otherFileIssueId);
  }

  @Test
  void it_should_return_false_on_reopen_issue_with_invalid_id() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var invalidIssueId = "invalid-id";
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", invalidIssueId));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isIssueReopened()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", "file/path");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);
  }

  @Test
  void it_should_return_false_on_reopen_non_existing_issue() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var nonExistingIssueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", nonExistingIssueId.toString()));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isIssueReopened()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", "file/path");
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getId)
      .containsOnly(issueId);
  }

  @Test
  void it_should_return_true_on_reopening_server_issue() throws ExecutionException, InterruptedException {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG).resolved();
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var reopen_response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", "myIssueKey"));

    assertThat(reopen_response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(reopen_response.get().isIssueReopened()).isTrue();
  }
}
