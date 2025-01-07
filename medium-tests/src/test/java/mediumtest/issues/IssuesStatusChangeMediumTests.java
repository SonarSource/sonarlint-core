/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import com.github.tomakehurst.wiremock.client.WireMock;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.LocalOnlyIssueFixtures.aLocalOnlyIssueResolved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerIssueFixtures.aServerIssue;

class IssuesStatusChangeMediumTests {

  @SonarLintTest
  void it_should_update_the_status_on_sonarqube_when_changing_the_status_on_a_server_matched_issue(SonarLintTestHarness harness) {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/do_transition"))
          .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
          .withRequestBody(equalTo("issue=myIssueKey&transition=wontfix")));
    });
  }

  @SonarLintTest
  void it_should_update_the_telemetry_when_changing_the_status_on_a_server_matched_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey",
          project -> project.withMainBranch("main",
            branch -> branch.withIssue(
              aServerIssue("myIssueKey")
                .withRuleKey("rule:key")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
                .withIntroductionDate(Instant.EPOCH.plusSeconds(1))
                .withType(RuleType.BUG))))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withTelemetryEnabled()
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"issueStatusChangedRuleKeys\":[\"rule:key\"]");
  }

  @SonarLintTest
  void it_should_fail_the_future_when_the_server_returns_an_error(SonarLintTestHarness harness) {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Error 404 on", "/api/issues/do_transition");
      });
  }

  @SonarLintTest
  void it_should_update_local_only_storage_when_the_issue_exists_locally(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey")
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    var localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(Path.of("file/path")).get(0).getRight();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var issueLoaded = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", Path.of("file/path"));
    assertThat(issueLoaded).hasSize(1);
    assertThat(issueLoaded.get(0).getId()).isEqualTo(localOnlyIssue.getId());
    assertThat(issueLoaded.get(0).getResolution().getStatus()).isEqualTo(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX);
  }

  @SonarLintTest
  void it_should_sync_anticipated_transitions_with_sonarqube_when_the_issue_exists_locally(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey")
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl())
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    var localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(Path.of("file/path")).get(0).getRight();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/anticipated_transitions?projectKey=projectKey"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(
            equalToJson("[{\"filePath\":\"file/path\",\"line\":1,\"hash\":\"linehash\",\"ruleKey\":\"ruleKey\",\"issueMessage\":\"message\",\"transition\":\"wontfix\"}]")));
    });
  }

  @SonarLintTest
  void it_should_update_telemetry_when_changing_status_of_a_local_only_issue(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey")
      .start();
    var client = harness.newFakeClient()
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .withTelemetryEnabled()
      .build(client);

    client.waitForSynchronization();

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    var localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(Path.of("file/path")).get(0).getRight();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"issueStatusChangedRuleKeys\":[\"ruleKey\"]");
  }

  @SonarLintTest
  void it_should_fail_when_the_issue_does_not_exists(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var params = new ChangeIssueStatusParams("configScopeId", "myIssueKey", ResolutionStatus.WONT_FIX, false);
    var issueService = backend.getIssueService();

    assertThat(issueService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Issue key myIssueKey was not found");
  }

  @SonarLintTest
  void it_should_add_new_comment_to_server_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue("myIssueKey")))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/add_comment"))
          .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
          .withRequestBody(equalTo("issue=myIssueKey&text=That%27s+serious+issue")));
    });
  }

  @SonarLintTest
  void it_should_add_new_comment_to_server_issue_with_uuid_key(SonarLintTestHarness harness) {
    var issueKey = UUID.randomUUID().toString();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue(issueKey)))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", issueKey, "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/add_comment"))
          .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
          .withRequestBody(equalTo("issue=" + issueKey + "&text=That%27s+serious+issue")));
    });
  }

  @SonarLintTest
  void it_should_add_new_comment_to_resolved_local_only_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", issueId.toString(), "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getResolution)
      .extracting(LocalOnlyIssueResolution::getComment)
      .containsOnly("That's serious issue");
  }

  @SonarLintTest
  void it_should_throw_if_server_response_is_not_OK_during_add_new_comment_to_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue("myIssueKey")))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Error 404 on", "/api/issues/add_comment");
      });
  }

  @SonarLintTest
  void it_should_throw_if_issue_is_unknown_when_adding_a_comment(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("myIssueKey");
      });
  }

  @SonarLintTest
  void it_should_throw_if_issue_with_uuid_key_is_unknown_when_adding_a_comment(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();
    var issueKey = UUID.randomUUID().toString();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", issueKey, "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains(issueKey);
      });
  }

  @SonarLintTest
  void it_should_reopen_issue_by_id(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", issueId.toString(), false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_load_issues(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId1 = UUID.randomUUID();
    var issueId2 = UUID.randomUUID();
    var otherFileIssueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey",
        storage -> storage
          .withLocalOnlyIssue(new LocalOnlyIssue(
            otherFileIssueId,
            Path.of("file/path1"),
            new TextRangeWithHash(1, 2, 3, 4, "ab12"),
            new LineWithHash(1, "linehash"),
            "ruleKey",
            "message",
            new LocalOnlyIssueResolution(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX, Instant.now().truncatedTo(ChronoUnit.MILLIS), "comment")))
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId1))
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId2)))
      .build();

    var issuesForFile = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", Path.of("file/path"));
    var issuesForOtherFile = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", Path.of("file/path1"));
    var allIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(issuesForFile).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2);
    assertThat(issuesForOtherFile).extracting(LocalOnlyIssue::getId).containsOnly(otherFileIssueId);
    assertThat(allIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2, otherFileIssueId);
  }

  @SonarLintTest
  void it_should_reopen_all_issues_for_file(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId1 = UUID.randomUUID();
    var issueId2 = UUID.randomUUID();
    var otherFileIssueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey",
        storage -> storage
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId1))
          .withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId2))
          .withLocalOnlyIssue(new LocalOnlyIssue(
            otherFileIssueId,
            Path.of("file/path1"),
            new TextRangeWithHash(1, 2, 3, 4, "ab12"),
            new LineWithHash(1, "linehash"),
            "ruleKey",
            "message",
            new LocalOnlyIssueResolution(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX, Instant.now().truncatedTo(ChronoUnit.MILLIS), "comment"))))
      .build();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2, otherFileIssueId);

    var response = backend.getIssueService()
      .reopenAllIssuesForFile(new ReopenAllIssuesForFileParams("configScopeId", Path.of("file/path")));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll("configScopeId");
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(otherFileIssueId);
  }

  @SonarLintTest
  void it_should_return_false_on_reopen_issue_with_invalid_id(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var invalidIssueId = "invalid-id";
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", invalidIssueId, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);
  }

  @SonarLintTest
  void it_should_return_false_on_reopen_non_existing_issue(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var nonExistingIssueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", nonExistingIssueId.toString(), false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile("configScopeId", Path.of("file/path"));
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getId)
      .containsOnly(issueId);
  }

  @SonarLintTest
  void it_should_return_true_on_reopening_server_issue(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG)
      .resolved();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var reopen_response = backend.getIssueService().reopenIssue(new ReopenIssueParams("configScopeId", "myIssueKey", false));

    assertThat(reopen_response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(reopen_response.get().isSuccess()).isTrue();
  }
}
