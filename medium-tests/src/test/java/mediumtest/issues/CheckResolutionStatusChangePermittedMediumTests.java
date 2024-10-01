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

import com.google.protobuf.Message;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.TestPlugin;
import mockwebserver3.MockResponse;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.scanner.protocol.Constants;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static testutils.AnalysisUtils.createFile;
import static testutils.AnalysisUtils.waitForAnalysisReady;
import static testutils.AnalysisUtils.waitForRaisedIssues;

class CheckResolutionStatusChangePermittedMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private SonarLintRpcServer backend;
  private ServerFixture.Server server;
  @RegisterExtension
  public final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }
    mockWebServerExtension.shutdown();
  }

  @Test
  void it_should_fail_when_the_connection_is_unknown() {
    backend = newBackend().build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("Connection 'connectionId' is gone");
  }

  @Test
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarqube_103() {
    fakeServerWithIssue("issueKey", List.of("wontfix", "falsepositive"));
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE);
  }

  @Test
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarqube_104() {
    fakeServerWithIssue("issueKey", List.of("accept", "falsepositive"));
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.4"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE);
  }

  @Test
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarcloud() {
    fakeServerWithIssue("issueKey", "orgKey", List.of("wontfix", "falsepositive"));
    backend = newBackend()
      .withSonarCloudUrl(mockWebServerExtension.endpointParams().getBaseUrl())
      .withSonarCloudConnection(CONNECTION_ID, "orgKey")
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE);
  }

  @Test
  void it_should_fallback_to_server_check_if_the_issue_uuid_is_not_found_in_local_only_issues() {
    var issueKey = UUID.randomUUID().toString();
    fakeServerWithIssue(issueKey, List.of("accept", "falsepositive"));
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.4"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, issueKey);

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE);
  }

  @Test
  void it_should_not_permit_status_change_when_issue_misses_required_transitions() {
    fakeServerWithIssue("issueKey", List.of("confirm"));
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking an issue as resolved requires the 'Administer Issues' permission", List.of());
  }

  @Test
  void it_should_fail_if_no_issue_is_returned_by_web_api() {
    fakeServerWithResponse("issueKey", null, Issues.SearchWsResponse.newBuilder().build());
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("No issue found with key 'issueKey'");
      });
  }

  @Test
  void it_should_fail_if_web_api_returns_an_error() {
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class);
  }

  @Test
  void it_should_fail_if_web_api_returns_unexpected_body() {
    fakeServerWithWrongBody("issueKey");
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Unexpected body received");
      });
  }

  @Disabled("SC is difficult to setup for this test")
  @Test
  void it_should_not_permit_status_change_on_local_only_issues_for_sonarcloud(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var branchName = "main";
    var projectKey = "projectKey";
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    server = newSonarCloudServer("org")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule
          .withSeverity(IssueSeverity.BLOCKER)
        ))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.XML)
      .start();
    backend = newBackend()
      .withSonarCloudConnection(CONNECTION_ID, server.baseUrl())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();
    waitForAnalysisReady(client, CONFIG_SCOPE_ID);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIG_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    var response = checkStatusChangePermitted(CONNECTION_ID, localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking a local-only issue as resolved requires SonarQube 10.2+", List.of());
  }

  @Test
  void it_should_not_permit_status_change_on_local_only_issues_for_sonarqube_prior_to_10_2(@TempDir Path testDir) throws IOException {
    var baseDir = testDir.resolve("it_should_not_permit_status_change_on_local_only_issues_for_sonarqube_prior_to_10_2");
    Files.createDirectory(baseDir);
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var branchName = "main";
    var projectKey = "projectKey";
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    server = newSonarQubeServer("10.1")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule
          .withSeverity(IssueSeverity.BLOCKER)
        ))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.XML)
      .start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();
    waitForAnalysisReady(client, CONFIG_SCOPE_ID);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIG_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    assertThat(localOnlyIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(localOnlyIssue.getRuleKey()).isEqualTo("xml:S3421");

    var response = checkStatusChangePermitted(CONNECTION_ID, localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking a local-only issue as resolved requires SonarQube 10.2+", List.of());
  }

  @Test
  void it_should_permit_status_change_on_local_only_issues_for_sonarqube_10_2_plus(@TempDir Path baseDir) throws ExecutionException, InterruptedException {
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var branchName = "branchName";
    var projectKey = "projectKey";
    var serverIssueKey = "myIssueKey";
    var introductionDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    when(client.matchSonarProjectBranch(eq(CONFIG_SCOPE_ID), eq("main"), eq(Set.of("main", branchName)), any()))
      .thenReturn(branchName);
    server = newSonarQubeServer("10.2")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule
        .withSeverity(IssueSeverity.MAJOR)
      ))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName,
            branch -> branch.withIssue(serverIssueKey, "xml:S3421", "message",
              "author", baseDir.relativize(filePath).toString(), "1356c67d7ad1638d816bfb822dd2c25d", Constants.Severity.MAJOR, RuleType.CODE_SMELL,
              "OPEN", null, introductionDate, new TextRange(1, 1, 1, 1))
          ))
      .withPlugin(TestPlugin.XML)
      .start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIG_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID).get(0);

    var response = checkStatusChangePermitted(CONNECTION_ID, localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE));
  }

  @Test
  void it_should_permit_status_change_on_local_only_issues_for_sonarqube_10_4_plus(@TempDir Path testDir) throws ExecutionException, InterruptedException, IOException {
    var baseDir = testDir.resolve("it_should_permit_status_change_on_local_only_issues_for_sonarqube_10_4_plus");
    Files.createDirectory(baseDir);
    var filePath = createFile(baseDir, "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<project>\n"
      + "  <modelVersion>4.0.0</modelVersion>\n"
      + "  <groupId>com.foo</groupId>\n"
      + "  <artifactId>bar</artifactId>\n"
      + "  <version>${pom.version}</version>\n"
      + "</project>");
    var fileUri = filePath.toUri();
    var branchName = "main";
    var projectKey = "projectKey";
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    server = newSonarQubeServer("10.4")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule
          .withSeverity(IssueSeverity.MAJOR)
        ))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.XML)
      .start();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withFullSynchronization()
      .build(client);
    client.waitForSynchronization();
    waitForAnalysisReady(client, CONFIG_SCOPE_ID);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIG_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    var response = checkStatusChangePermitted(CONNECTION_ID, localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE));
  }

  private void fakeServerWithIssue(String issueKey, List<String> transitions) {
    fakeServerWithIssue(issueKey, null, transitions);
  }

  private void fakeServerWithIssue(String issueKey, @Nullable String orgKey, List<String> transitions) {
    var pbTransitions = Issues.Transitions.newBuilder().addAllTransitions(transitions);
    fakeServerWithResponse(issueKey, orgKey,
      Issues.SearchWsResponse.newBuilder().addIssues(Issues.Issue.newBuilder().setKey(issueKey).setTransitions(pbTransitions.build()).build()).build());
  }

  private void fakeServerWithResponse(String issueKey, @Nullable String orgKey, Message response) {
    mockWebServerExtension.addProtobufResponse(apiIssueSearchPath(issueKey, orgKey), response);
  }

  private void fakeServerWithWrongBody(String issueKey) {
    mockWebServerExtension.addResponse(apiIssueSearchPath(issueKey, null), new MockResponse().setBody("wrong body"));
  }

  private static String apiIssueSearchPath(String issueKey, @Nullable String orgKey) {
    var orgParam = orgKey == null ? "" : "&organization=" + orgKey;
    return "/api/issues/search.protobuf?issues=" + issueKey + "&additionalFields=transitions" + orgParam + "&ps=1&p=1";
  }

  private CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(String connectionId, String issueKey) {
    return backend.getIssueService().checkStatusChangePermitted(new CheckStatusChangePermittedParams(connectionId, issueKey));
  }
}
