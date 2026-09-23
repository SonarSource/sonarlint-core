/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import mockwebserver3.MockResponse;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.MockWebServerExtensionWithProtobuf;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static utils.AnalysisUtils.createFile;
import static utils.AnalysisUtils.waitForAnalysisReady;
import static utils.AnalysisUtils.waitForRaisedIssues;

class CheckResolutionStatusChangePermittedMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";

  @RegisterExtension
  public final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  @AfterEach
  void tearDown() {
    mockWebServerExtension.shutdown();
  }

  @SonarLintTest
  void it_should_fail_when_the_connection_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend().start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("Connection 'connectionId' is not valid");
  }

  @SonarLintTest
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarqube(SonarLintTestHarness harness) {
    fakeServerWithIssue("issueKey", List.of("accept", "falsepositive"));
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("2025.1"))
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE);
  }

  @SonarLintTest
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarcloud(SonarLintTestHarness harness) {
    fakeServerWithIssue("issueKey", "orgKey", List.of("wontfix", "falsepositive"));
    var backend = harness.newBackend()
      .withSonarQubeCloudEuRegionUri(mockWebServerExtension.endpointParams().getBaseUrl())
      .withSonarCloudConnection(CONNECTION_ID, "orgKey")
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE);
  }

  @SonarLintTest
  void it_should_fallback_to_server_check_if_the_issue_uuid_is_not_found_in_local_only_issues(SonarLintTestHarness harness) {
    var issueKey = UUID.randomUUID().toString();
    fakeServerWithIssue(issueKey, List.of("accept", "falsepositive"));
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("2025.1"))
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, issueKey);

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.ACCEPT, ResolutionStatus.FALSE_POSITIVE);
  }

  @SonarLintTest
  void it_should_not_permit_status_change_when_issue_misses_required_transitions(SonarLintTestHarness harness) {
    fakeServerWithIssue("issueKey", List.of("confirm"));
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("2025.1"))
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking an issue as resolved requires the 'Administer Issues' permission", List.of());
  }

  @SonarLintTest
  void it_should_fail_if_no_issue_is_returned_by_web_api(SonarLintTestHarness harness) {
    fakeServerWithResponse("issueKey", null, Issues.SearchWsResponse.newBuilder().build());
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("2025.1"))
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("No issue found with key 'issueKey'");
      });
  }

  @SonarLintTest
  void it_should_fail_if_web_api_returns_an_error(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("2025.1"))
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class);
  }

  @SonarLintTest
  void it_should_fail_if_web_api_returns_unexpected_body(SonarLintTestHarness harness) {
    fakeServerWithWrongBody("issueKey");
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("2025.1"))
      .start();

    var response = checkStatusChangePermitted(backend, CONNECTION_ID, "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Unexpected body received");
      });
  }

  @Disabled("SC is difficult to setup for this test")
  @SonarLintTest
  void it_should_not_permit_status_change_on_local_only_issues_for_sonarcloud(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.foo</groupId>
        <artifactId>bar</artifactId>
        <version>${pom.version}</version>
      </project>""");
    var fileUri = filePath.toUri();
    var branchName = "main";
    var projectKey = "projectKey";
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var server = harness.newFakeSonarCloudServer()
      .withOrganization("org", organization -> organization
        .withQualityProfile("qpKey", qualityProfile -> qualityProfile
          .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule
            .withSeverity(IssueSeverity.BLOCKER)))
        .withProject(projectKey,
          project -> project
            .withQualityProfile("qpKey")
            .withBranch(branchName)))
      .withPlugin(TestPlugin.XML)
      .start();
    var backend = harness.newBackend()
      .withSonarCloudConnection(CONNECTION_ID, server.baseUrl())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();
    waitForAnalysisReady(client, CONFIG_SCOPE_ID);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIG_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    var response = checkStatusChangePermitted(backend, CONNECTION_ID, localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking a local-only issue as resolved requires a SonarQube Server connection", List.of());
  }

  @Disabled("SLCORE-966")
  @SonarLintTest
  void it_should_permit_status_change_on_local_only_issues_for_sonarqube_server(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var filePath = createFile(baseDir, "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.foo</groupId>
        <artifactId>bar</artifactId>
        <version>${pom.version}</version>
      </project>""");
    var fileUri = filePath.toUri();
    var branchName = "main";
    var projectKey = "projectKey";
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var server = harness.newFakeSonarQubeServer("2025.1")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("xml").withActiveRule("xml:S3421", activeRule -> activeRule
          .withSeverity(IssueSeverity.MAJOR)))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.XML)
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();
    waitForAnalysisReady(client, CONFIG_SCOPE_ID);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(fileUri), Map.of(), false, 0)).join();

    waitForRaisedIssues(client, CONFIG_SCOPE_ID);
    var localOnlyIssue = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    var response = checkStatusChangePermitted(backend, CONNECTION_ID, localOnlyIssue.getId().toString());

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
    mockWebServerExtension.addResponse(apiIssueSearchPath(issueKey, null), new MockResponse.Builder().code(200).body("wrong body").build());
  }

  private static String apiIssueSearchPath(String issueKey, @Nullable String orgKey) {
    var orgParam = orgKey == null ? "" : "&organization=" + orgKey;
    return "/api/issues/search.protobuf?issues=" + issueKey + "&additionalFields=transitions" + orgParam + "&ps=1&p=1";
  }

  private CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(SonarLintTestRpcServer backend, String connectionId, String issueKey) {
    return backend.getIssueService().checkStatusChangePermitted(new CheckStatusChangePermittedParams(connectionId, issueKey));
  }
}
