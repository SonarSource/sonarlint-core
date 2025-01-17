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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class CheckResolutionStatusChangePermittedMediumTests {

  private static final Path FILE_PATH = Path.of("file/path");
  @RegisterExtension
  public final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  @AfterEach
  void tearDown() {
    mockWebServerExtension.shutdown();
  }

  @SonarLintTest
  void it_should_fail_when_the_connection_is_unknown(SonarLintTestHarness harness) {
    var backend = harness.newBackend().build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("Connection 'connectionId' is gone");
  }

  @SonarLintTest
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarqube_103(SonarLintTestHarness harness) {
    fakeServerWithIssue("issueKey", List.of("wontfix", "falsepositive"));
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(ResolutionStatus.class))
      .containsExactly(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE);
  }

  @SonarLintTest
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarqube_104(SonarLintTestHarness harness) {
    fakeServerWithIssue("issueKey", List.of("accept", "falsepositive"));
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.4"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

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
      .withSonarCloudUrl(mockWebServerExtension.endpointParams().getBaseUrl())
      .withSonarCloudConnection("connectionId", "orgKey")
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

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
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.4"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", issueKey);

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
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

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
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

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
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

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
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl(), storage -> storage.withServerVersion("10.3"))
      .build();

    var response = checkStatusChangePermitted(backend, "connectionId", "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Unexpected body received");
      });
  }

  @SonarLintTest
  void it_should_not_permit_status_change_on_local_only_issues_for_sonarcloud(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarCloudConnection("connectionId", "orgKey", true, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(FILE_PATH, List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));
    Thread.sleep(2000);
    var localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(FILE_PATH).get(0).getRight();

    var response = checkStatusChangePermitted(backend, "connectionId", localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking a local-only issue as resolved requires SonarQube Server 10.2+", List.of());
  }

  @SonarLintTest
  void it_should_not_permit_status_change_on_local_only_issues_for_sonarqube_prior_to_10_2(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("10.1")
        .withProject("projectKey", project -> project.withMainBranch("main")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(FILE_PATH, List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));
    Thread.sleep(2000);
    var localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(FILE_PATH).get(0).getRight();

    var response = checkStatusChangePermitted(backend, "connectionId", localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking a local-only issue as resolved requires SonarQube Server 10.2+", List.of());
  }

  @SonarLintTest
  void it_should_permit_status_change_on_local_only_issues_for_sonarqube_10_2_plus(SonarLintTestHarness harness) throws ExecutionException, InterruptedException {
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withServerVersion("10.2")
        .withProject("projectKey", project -> project.withMainBranch("main")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(FILE_PATH, List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    var localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(FILE_PATH).get(0).getRight();

    var response = checkStatusChangePermitted(backend, "connectionId", localOnlyIssue.getId().toString());

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(true, null, List.of(ResolutionStatus.WONT_FIX, ResolutionStatus.FALSE_POSITIVE));
  }

  @SonarLintTest
  void it_should_permit_status_change_on_local_only_issues_for_sonarqube_10_4_plus(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("10.4").withProject("projectKey", project -> project.withMainBranch("main")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();
    var trackedIssues = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of(FILE_PATH, List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    LocalOnlyIssueDto localOnlyIssue = null;
    try {
      localOnlyIssue = trackedIssues.get().getIssuesByIdeRelativePath().get(FILE_PATH).get(0).getRight();
    } catch (Exception e) {
      fail();
    }

    var response = checkStatusChangePermitted(backend, "connectionId", localOnlyIssue.getId().toString());

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

  private CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(SonarLintTestRpcServer backend, String connectionId, String issueKey) {
    return backend.getIssueService().checkStatusChangePermitted(new CheckStatusChangePermittedParams(connectionId, issueKey));
  }
}
