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
package mediumtest.sca;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeScaIssueStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerScaIssueFixtures.aServerScaIssue;

class ScaIssueStatusChangeMediumTests {

  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";
  private static final String BRANCH_NAME = "main";

  @SonarLintTest
  void it_should_update_the_status_on_sonarqube_when_changing_the_status_on_a_server_matched_sca_issue(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.CONFIRM,
        ServerScaIssue.Transition.REOPEN
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var comment = "I confirm this is a risk";
    var response = backend.getScaService().changeStatus(new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey,
      DependencyRiskTransition.CONFIRM, comment));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"CONFIRM",
          "comment":"%s"
        }
      """, scaIssueKey, comment);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_throw_when_sca_issue_does_not_exist(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME)))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var nonExistentKey = UUID.randomUUID();
    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, nonExistentKey, DependencyRiskTransition.CONFIRM, null);
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Dependency Risk with key " + nonExistentKey + " was not found");
  }

  @SonarLintTest
  void it_should_throw_when_transition_is_not_allowed(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.CONFIRM
        // ACCEPT is not in the allowed transitions
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, "comment");
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Transition ACCEPT is not allowed for this SCA issue");
  }

  @SonarLintTest
  void it_should_throw_when_accept_transition_has_no_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, null);
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT, FIXED, and SAFE transitions");
  }

  @SonarLintTest
  void it_should_throw_when_fixed_transition_has_no_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.FIXED
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.FIXED, null);
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT, FIXED, and SAFE transitions");
  }

  @SonarLintTest
  void it_should_throw_when_safe_transition_has_no_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.SAFE
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.SAFE, null);
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT, FIXED, and SAFE transitions");
  }

  @SonarLintTest
  void it_should_succeed_when_accept_transition_has_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, "This is acceptable");
    var scaService = backend.getScaService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"This is acceptable"
        }
      """, scaIssueKey);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_succeed_when_safe_transition_has_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.SAFE
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.SAFE, "This is safe");
    var scaService = backend.getScaService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"SAFE",
          "comment":"This is safe"
        }
      """, scaIssueKey);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_fail_when_server_returns_error(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.CONFIRM
      ));

    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.CONFIRM, null);
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Error 404 on", "/api/v2/sca/issues-releases/change-status");
      });
  }

  @SonarLintTest
  void it_should_handle_multiple_sca_issues_with_different_transitions(SonarLintTestHarness harness) {
    var scaIssueKey1 = UUID.randomUUID();
    var scaIssueKey2 = UUID.randomUUID();

    var scaIssue1 = aServerScaIssue()
      .withKey(scaIssueKey1)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable1")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.CONFIRM,
        ServerScaIssue.Transition.REOPEN
      ));

    var scaIssue2 = aServerScaIssue()
      .withKey(scaIssueKey2)
      .withType(ServerScaIssue.Type.PROHIBITED_LICENSE)
      .withSeverity(ServerScaIssue.Severity.BLOCKER)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.prohibited")
      .withPackageVersion("2.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch
          .withScaIssue(scaIssue1)
          .withScaIssue(scaIssue2))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var scaService = backend.getScaService();

    // Test first issue with CONFIRM transition
    var params1 = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey1, DependencyRiskTransition.CONFIRM, null);
    var response1 = scaService.changeStatus(params1);
    assertThat(response1).succeedsWithin(Duration.ofSeconds(2));

    // Test second issue with ACCEPT transition and comment
    var params2 = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey2, DependencyRiskTransition.ACCEPT, "License is acceptable");
    var response2 = scaService.changeStatus(params2);
    assertThat(response2).succeedsWithin(Duration.ofSeconds(2));

    var expectedJson1 = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"CONFIRM"
        }
      """, scaIssueKey1);

    var expectedJson2 = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"License is acceptable"
        }
      """, scaIssueKey2);

    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withRequestBody(equalToJson(expectedJson1)));

      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withRequestBody(equalToJson(expectedJson2)));
    });
  }

  @SonarLintTest
  void it_should_handle_empty_comment_for_accept_transition(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, "");
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT, FIXED, and SAFE transitions");
  }

  @SonarLintTest
  void it_should_handle_whitespace_only_comment_for_accept_transition(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, "   ");
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT, FIXED, and SAFE transitions");
  }

  @SonarLintTest
  void it_should_handle_sca_issue_with_different_severities(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.critical")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.CONFIRM,
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, "Critical issue accepted");
    var scaService = backend.getScaService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"Critical issue accepted"
        }
      """, scaIssueKey);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_sca_issue_with_long_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var longComment = "This is a very long comment that exceeds the typical length of a normal comment. " +
      "It contains multiple sentences and should be handled properly by the SCA issue status change functionality. " +
      "The comment should be truncated or handled appropriately by the server.";

    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, longComment);
    var scaService = backend.getScaService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"%s"
        }
      """, scaIssueKey, longComment);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_sca_issue_with_special_characters_in_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var specialComment = "Comment with special chars: \"quotes\", 'apostrophes', & < > symbols, and \n newlines \t tabs";

    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, specialComment);
    var scaService = backend.getScaService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"%s"
        }
      """, scaIssueKey, specialComment
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\t", "\\t"));
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_sca_issue_with_unicode_characters_in_comment(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var unicodeComment = "Comment with unicode: 🚀 emoji, éñtîôñs, 中文, русский, العربية";

    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerScaIssue.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.ACCEPT, unicodeComment);
    var scaService = backend.getScaService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"%s"
        }
      """, scaIssueKey, unicodeComment);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_sca_issue_with_no_transitions_available(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.randomUUID();
    var scaIssue = aServerScaIssue()
      .withKey(scaIssueKey)
      .withType(ServerScaIssue.Type.VULNERABILITY)
      .withSeverity(ServerScaIssue.Severity.HIGH)
      .withStatus(ServerScaIssue.Status.OPEN)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of()); // No transitions available

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withScaIssue(scaIssue))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeScaIssueStatusParams(CONFIGURATION_SCOPE_ID, scaIssueKey, DependencyRiskTransition.CONFIRM, null);
    var scaService = backend.getScaService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Transition CONFIRM is not allowed for this SCA issue");
  }
} 