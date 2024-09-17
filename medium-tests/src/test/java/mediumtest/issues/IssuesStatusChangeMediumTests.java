/*
 * SonarLint Core - Medium Tests
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
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.LineWithHash;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssueResolution;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenAllIssuesForFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.LocalOnlyIssueFixtures.aLocalOnlyIssueResolved;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.ServerFixture.ServerStatus.DOWN;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static testutils.AnalysisUtils.createFile;
import static testutils.AnalysisUtils.waitForRaisedIssues;

class IssuesStatusChangeMediumTests {

  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private SonarLintTestRpcServer backend;
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
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/do_transition"))
          .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
          .withRequestBody(equalTo("issue=myIssueKey&transition=wontfix")));
    });
  }

  @Test
  void it_should_update_the_telemetry_when_changing_the_status_on_a_server_matched_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey",
          project -> project.withMainBranch("main",
            branch -> branch.withIssue(
              aServerIssue("myIssueKey")
                .withRuleKey("rule:key")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
                .withIntroductionDate(Instant.EPOCH.plusSeconds(1))
                .withType(RuleType.BUG))))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withTelemetryEnabled()
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey",
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
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey",
      ResolutionStatus.WONT_FIX, false));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Error 404 on", "/api/issues/do_transition");
      });
  }

  @Test
  void it_should_update_local_only_storage_when_the_issue_exists_locally(@TempDir Path baseDir) throws ExecutionException, InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    server = newSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)
        ))
      .withProject("projectKey",
        project -> project.withQualityProfile("qpKey"))
      .start();
    var client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIGURATION_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeId(CONFIGURATION_SCOPE_ID).get(fileUri).get(0);

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var issueLoaded = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, baseDir.relativize(filePath));
    assertThat(issueLoaded).hasSize(1);
    assertThat(issueLoaded.get(0).getId()).isEqualTo(localOnlyIssue.getId());
    assertThat(issueLoaded.get(0).getResolution().getStatus()).isEqualTo(org.sonarsource.sonarlint.core.commons.IssueStatus.WONT_FIX);
  }

  @Test
  void it_should_sync_anticipated_transitions_with_sonarqube_when_the_issue_exists_locally(@TempDir Path baseDir) throws ExecutionException, InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    server = newSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)
        ))
      .withProject("projectKey",
        project -> project.withQualityProfile("qpKey"))
      .start();
    var client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), true, 0)).join();

    waitForRaisedIssues(client, CONFIGURATION_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeId(CONFIGURATION_SCOPE_ID).get(fileUri).get(0);

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/anticipated_transitions?projectKey=projectKey"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(
            equalToJson("[{\"filePath\":\"pom.xml\",\"line\":6,\"hash\":\"07bac3d9d23dc1b0d7156598e01d40b0\",\"ruleKey\":\"xml:S3421\",\"issueMessage\":\"Replace \\\"pom.version\\\" with \\\"project.version\\\".\",\"transition\":\"wontfix\"}]")));
    });
  }

  @Test
  void it_should_update_telemetry_when_changing_status_of_a_local_only_issue(@TempDir Path baseDir) throws ExecutionException, InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    server = newSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)
        ))
      .withProject("projectKey",
        project -> project.withQualityProfile("qpKey"))
      .start();
    var client = newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withFullSynchronization()
      .withTelemetryEnabled()
      .build(client);
    client.waitForSynchronization();

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIGURATION_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIGURATION_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeId(CONFIGURATION_SCOPE_ID).get(fileUri).get(0);

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, localOnlyIssue.getId().toString(),
      ResolutionStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(backend.telemetryFilePath())
      .content().asBase64Decoded().asString()
      .contains("\"issueStatusChangedRuleKeys\":[\"xml:S3421\"]");
  }

  @Test
  void it_should_fail_when_the_issue_does_not_exists() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var params = new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey", ResolutionStatus.WONT_FIX, false);
    var issueService = backend.getIssueService();

    assertThat(issueService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Issue key myIssueKey was not found");
  }

  @Test
  void it_should_add_new_comment_to_server_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue("myIssueKey")))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/add_comment"))
          .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
          .withRequestBody(equalTo("issue=myIssueKey&text=That%27s+serious+issue")));
    });
  }

  @Test
  void it_should_add_new_comment_to_server_issue_with_uuid_key() {
    var issueKey = UUID.randomUUID().toString();
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue(issueKey)))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, issueKey, "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/issues/add_comment"))
          .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
          .withRequestBody(equalTo("issue=" + issueKey + "&text=That%27s+serious+issue")));
    });
  }

  @Test
  void it_should_add_new_comment_to_resolved_local_only_issue() {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, issueId.toString(), "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getResolution)
      .extracting(LocalOnlyIssueResolution::getComment)
      .containsOnly("That's serious issue");
  }

  @Test
  void it_should_throw_if_server_response_is_not_OK_during_add_new_comment_to_issue() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue("myIssueKey")))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Error 404 on", "/api/issues/add_comment");
      });
  }

  @Test
  void it_should_throw_if_issue_is_unknown_when_adding_a_comment() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("myIssueKey");
      });
  }

  @Test
  void it_should_throw_if_issue_with_uuid_key_is_unknown_when_adding_a_comment() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();
    var issueKey = UUID.randomUUID().toString();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, issueKey, "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains(issueKey);
      });
  }

  @Test
  void it_should_reopen_issue_by_id() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, issueId.toString(), false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void it_should_load_issues() {
    server = newSonarQubeServer().start();
    var issueId1 = UUID.randomUUID();
    var issueId2 = UUID.randomUUID();
    var otherFileIssueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey",
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

    var issuesForFile = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    var issuesForOtherFile = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path1"));
    var allIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
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
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey",
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
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2, otherFileIssueId);

    var response = backend.getIssueService()
      .reopenAllIssuesForFile(new ReopenAllIssuesForFileParams(CONFIGURATION_SCOPE_ID, Path.of("file/path")));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(otherFileIssueId);
  }

  @Test
  void it_should_return_false_on_reopen_issue_with_invalid_id() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var invalidIssueId = "invalid-id";
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, invalidIssueId, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);
  }

  @Test
  void it_should_return_false_on_reopen_non_existing_issue() throws ExecutionException, InterruptedException {
    server = newSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var nonExistingIssueId = UUID.randomUUID();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .build();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, nonExistingIssueId.toString(), false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getId)
      .containsOnly(issueId);
  }

  @Test
  void it_should_return_true_on_reopening_server_issue() throws ExecutionException, InterruptedException {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG)
      .resolved();
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .build();

    var reopen_response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, "myIssueKey", false));

    assertThat(reopen_response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(reopen_response.get().isSuccess()).isTrue();
  }
}
