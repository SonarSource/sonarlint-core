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

import com.google.protobuf.Message;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import mockwebserver3.MockResponse;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus;
import org.sonarsource.sonarlint.core.serverapi.exception.NotFoundException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CheckIssueStatusChangePermittedMediumTests {

  private SonarLintBackendImpl backend;
  @RegisterExtension
  public final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();
  private String oldSonarCloudUrl;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    mockWebServerExtension.shutdown();

    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_fail_when_the_connection_is_unknown() {
    backend = newBackend().build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(IllegalArgumentException.class)
      .withMessage("Connection with ID 'connectionId' does not exist");
  }

  @Test
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarqube() {
    fakeServerWithIssue("issueKey", List.of("wontfix", "falsepositive"));
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(IssueStatus.class))
      .extracting(IssueStatus::getTitle, IssueStatus::getDescription)
      .containsExactly(
        tuple("Won't Fix", "The issue is valid but does not need fixing. It represents accepted technical debt."),
        tuple("False Positive", "The issue is raised unexpectedly on code that should not trigger an issue."));
  }

  @Test
  void it_should_allow_2_statuses_when_user_has_permission_for_sonarcloud() {
    fakeServerWithIssue("issueKey", "orgKey", List.of("wontfix", "falsepositive"));
    System.setProperty("sonarlint.internal.sonarcloud.url", mockWebServerExtension.endpointParams().getBaseUrl());
    backend = newBackend()
      .withSonarCloudConnection("connectionId", "orgKey")
      .build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(IssueStatus.class))
      .extracting(IssueStatus::getTitle, IssueStatus::getDescription)
      .containsExactly(
        tuple("Won't Fix", "The issue is valid but does not need fixing. It represents accepted technical debt."),
        tuple("False Positive", "The issue is raised unexpectedly on code that should not trigger an issue."));
  }

  @Test
  void it_should_not_permit_status_change_when_issue_misses_required_transitions() {
    fakeServerWithIssue("issueKey", List.of("confirm"));
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::isPermitted, CheckStatusChangePermittedResponse::getNotPermittedReason,
        CheckStatusChangePermittedResponse::getAllowedStatuses)
      .containsExactly(false, "Marking an issue as resolved requires the 'Administer Issues' permission", List.of(IssueStatus.WONT_FIX, IssueStatus.FALSE_POSITIVE));
  }

  @Test
  void it_should_fail_if_no_issue_is_returned_wy_web_api() {
    fakeServerWithResponse("issueKey", null, Issues.SearchWsResponse.newBuilder().build());
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(UnexpectedBodyException.class)
      .withMessage("No issue found with key 'issueKey'");
  }

  @Test
  void it_should_fail_if_web_api_returns_an_error() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(NotFoundException.class);
  }

  @Test
  void it_should_fail_if_web_api_returns_unexpected_body() {
    fakeServerWithWrongBody("issueKey");
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.endpointParams().getBaseUrl())
      .build();

    var response = checkStatusChangePermitted("connectionId", "issueKey");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(UnexpectedBodyException.class)
      .withMessage("Unexpected body received");
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
