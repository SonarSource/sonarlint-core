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
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.LocalOnlyIssueFixtures.aLocalOnlyIssueResolved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerIssueFixtures.aServerIssue;
import static utils.AnalysisUtils.createFile;
import static utils.AnalysisUtils.waitForRaisedIssues;

class IssuesStatusChangeMediumTests {

  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";

  @SonarLintTest
  void it_should_update_the_status_on_sonarqube_when_changing_the_status_on_a_server_matched_issue(SonarLintTestHarness harness) {
    var serverIssue = aServerIssue("myIssueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

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

  @SonarLintTest
  void it_should_throw_on_update_the_status_on_sonarcloud_if_issue_dont_exist_on_server_and_is_not_synchronized(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg", organization -> organization
        .withProject("projectKey",
          project -> project.withBranch("main")))
      .withResponseCodes(responseCodes -> responseCodes.withIssueTransitionStatusCode(404))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(CONNECTION_ID, "myOrg", true, storageBuilder -> storageBuilder
        .withProject("projectKey", projectStorageBuilder -> projectStorageBuilder.withMainBranch("main")))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start(client);

    var issueService = backend.getIssueService();
    var params = new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey", ResolutionStatus.WONT_FIX, false);

    var changeStatusFuture = issueService.changeStatus(params);
    assertThrows(ExecutionException.class, changeStatusFuture::get);
  }

  @SonarLintTest
  void it_should_update_the_status_on_sonarcloud_if_issue_exist_on_server_but_is_not_synchronized(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg", organization -> organization
        .withProject("projectKey",
          project -> project.withBranch("main")))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(CONNECTION_ID, "myOrg", true, storageBuilder -> storageBuilder
        .withProject("projectKey", projectStorageBuilder -> projectStorageBuilder.withMainBranch("main")))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start(client);

    var issueService = backend.getIssueService();
    var params = new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey", ResolutionStatus.WONT_FIX, false);

    var changeStatusFuture = issueService.changeStatus(params);
    assertDoesNotThrow(() -> changeStatusFuture.get());
  }

  @SonarLintTest
  void it_should_throw_on_add_issue_comment_on_sonarcloud_if_issue_dont_exist_on_server_and_is_not_synchronized(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg", organization -> organization
        .withProject("projectKey",
          project -> project.withBranch("main")))
      .withResponseCodes(responseCodes -> responseCodes.withAddCommentStatusCode(404))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(CONNECTION_ID, "myOrg", true, storageBuilder -> storageBuilder
        .withProject("projectKey", projectStorageBuilder -> projectStorageBuilder.withMainBranch("main")))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start(client);

    var issueService = backend.getIssueService();
    var params = new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, "myIssueKey", "comment");
    var addCommentFuture = issueService.addComment(params);
    assertThrows(ExecutionException.class, addCommentFuture::get);
  }

  @SonarLintTest
  void it_should_add_issue_comment_on_sonarcloud_if_issue_exist_on_server_but_is_not_synchronized(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("myOrg", organization -> organization
        .withProject("projectKey",
          project -> project.withBranch("main")))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(server.baseUrl())
      .withSonarCloudConnection(CONNECTION_ID, "myOrg", true, storageBuilder -> storageBuilder
        .withProject("projectKey", projectStorageBuilder -> projectStorageBuilder.withMainBranch("main")))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start(client);

    var issueService = backend.getIssueService();
    var params = new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, "myIssueKey", "comment");

    var addCommentFuture = issueService.addComment(params);
    assertDoesNotThrow(() -> addCommentFuture.get());
  }

  @SonarLintTest
  void it_should_update_the_telemetry_when_changing_the_status_on_a_server_matched_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
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
      .start();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey",
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
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

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

  @SonarLintTest
  void it_should_update_local_only_storage_when_the_issue_exists_locally(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.foo</groupId>
        <artifactId>bar</artifactId>
        <version>${pom.version}</version>
      </project>""");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)))
      .withProject("projectKey",
        project -> project.withQualityProfile("qpKey"))
      .start();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);
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

  @SonarLintTest
  void it_should_sync_anticipated_transitions_with_sonarqube_when_the_issue_exists_locally(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.foo</groupId>
        <artifactId>bar</artifactId>
        <version>${pom.version}</version>
      </project>""");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)))
      .withProject("projectKey",
        project -> project.withQualityProfile("qpKey"))
      .start();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);
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
            equalToJson(
              "[{\"filePath\":\"pom.xml\",\"line\":6,\"hash\":\"07bac3d9d23dc1b0d7156598e01d40b0\",\"ruleKey\":\"xml:S3421\",\"issueMessage\":\"Replace \\\"pom.version\\\" with \\\"project.version\\\".\",\"transition\":\"wontfix\"}]")));
    });
  }

  @SonarLintTest
  void it_should_update_telemetry_when_changing_status_of_a_local_only_issue(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.foo</groupId>
        <artifactId>bar</artifactId>
        <version>${pom.version}</version>
      </project>""");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)))
      .withProject("projectKey",
        project -> project.withQualityProfile("qpKey"))
      .start();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIGURATION_SCOPE_ID, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIGURATION_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .withTelemetryEnabled()
      .start(client);
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

  @SonarLintTest
  void it_should_fail_when_the_issue_does_not_exists(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

    var params = new ChangeIssueStatusParams(CONFIGURATION_SCOPE_ID, "myIssueKey", ResolutionStatus.WONT_FIX, false);
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
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue("myIssueKey")))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

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

  @SonarLintTest
  void it_should_add_new_comment_to_server_issue_with_uuid_key(SonarLintTestHarness harness) {
    var issueKey = UUID.randomUUID().toString();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue(issueKey)))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

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

  @SonarLintTest
  void it_should_add_new_comment_to_resolved_local_only_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .start();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams(CONFIGURATION_SCOPE_ID, issueId.toString(), "That's " +
      "serious issue"));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    assertThat(storedIssues)
      .extracting(LocalOnlyIssue::getResolution)
      .extracting(LocalOnlyIssueResolution::getComment)
      .containsOnly("That's serious issue");
  }

  @SonarLintTest
  void it_should_throw_if_server_response_is_not_OK_during_add_new_comment_to_issue(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(aServerIssue("myIssueKey")))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

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

  @SonarLintTest
  void it_should_throw_if_issue_is_unknown_when_adding_a_comment(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withResponseCodes(responseCodes -> responseCodes.withAddCommentStatusCode(404))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

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

  @SonarLintTest
  void it_should_throw_if_issue_with_uuid_key_is_unknown_when_adding_a_comment(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();
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

  @SonarLintTest
  void it_should_reopen_issue_by_id(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .start();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, issueId.toString(), false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_load_issues(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId1 = UUID.randomUUID();
    var issueId2 = UUID.randomUUID();
    var otherFileIssueId = UUID.randomUUID();
    var backend = harness.newBackend()
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
      .start();

    var issuesForFile = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    var issuesForOtherFile = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path1"));
    var allIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
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
      .start();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId1, issueId2, otherFileIssueId);

    var response = backend.getIssueService()
      .reopenAllIssuesForFile(new ReopenAllIssuesForFileParams(CONFIGURATION_SCOPE_ID, Path.of("file/path")));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isTrue();
    storedIssues = backend.getLocalOnlyIssueStorageService().get().loadAll(CONFIGURATION_SCOPE_ID);
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(otherFileIssueId);
  }

  @SonarLintTest
  void it_should_return_false_on_reopen_issue_with_invalid_id(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var invalidIssueId = "invalid-id";
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .start();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, invalidIssueId, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
    assertThat(storedIssues).extracting(LocalOnlyIssue::getId).containsOnly(issueId);
  }

  @SonarLintTest
  void it_should_return_false_on_reopen_non_existing_issue(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var server = harness.newFakeSonarQubeServer().start();
    var issueId = UUID.randomUUID();
    var nonExistingIssueId = UUID.randomUUID();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey", storage -> storage.withLocalOnlyIssue(aLocalOnlyIssueResolved(issueId)))
      .start();

    var response = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, nonExistingIssueId.toString(), false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    assertThat(response.get().isSuccess()).isFalse();
    var storedIssues = backend.getLocalOnlyIssueStorageService().get().loadForFile(CONFIGURATION_SCOPE_ID, Path.of("file/path"));
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
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue)))
        .withServerVersion("9.8"))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, "projectKey")
      .start();

    var reopenResponse = backend.getIssueService().reopenIssue(new ReopenIssueParams(CONFIGURATION_SCOPE_ID, "myIssueKey", false));

    assertThat(reopenResponse).succeedsWithin(Duration.ofSeconds(2));
    assertThat(reopenResponse.get().isSuccess()).isTrue();
  }
}
