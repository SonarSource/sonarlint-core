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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.ChangeDependencyRiskStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeDependencyRisksParams;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.test.utils.server.ServerFixture.ServerStatus.DOWN;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerDependencyRiskFixtures.aServerDependencyRisk;

class DependencyRiskStatusChangeMediumTests {

  private static final String CONFIGURATION_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";
  private static final String BRANCH_NAME = "main";

  @SonarLintTest
  void it_should_update_the_status_on_sonarqube_when_changing_the_status_on_a_server_matched_dependency_risk(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM,
        ServerDependencyRisk.Transition.REOPEN
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var comment = "I confirm this is a risk";
    var response = backend.getDependencyRiskService().changeStatus(new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey,
      DependencyRiskTransition.CONFIRM, comment));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"CONFIRM",
          "comment":"%s"
        }
      """, dependencyRiskKey, comment);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_update_the_status_in_the_local_storage_when_changing_the_status_on_a_server_matched_dependency_risk(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM,
        ServerDependencyRisk.Transition.REOPEN
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var comment = "I confirm this is a risk";
    var response = backend.getDependencyRiskService().changeStatus(new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey,
      DependencyRiskTransition.CONFIRM, comment));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var issueStorage = backend.getIssueStorageService().connection(CONNECTION_ID).project(PROJECT_KEY).findings();
    var storedDependencyRisk = issueStorage.loadDependencyRisks(BRANCH_NAME).stream().filter(risk -> risk.key().equals(dependencyRiskKey)).findFirst();
    assertThat(storedDependencyRisk)
      .map(ServerDependencyRisk::status)
      .contains(ServerDependencyRisk.Status.CONFIRM);


  }

  @SonarLintTest
  void it_should_notify_the_client_with_updated_dependency_risk_when_changing_the_status_on_a_server_matched_dependency_risk(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM,
        ServerDependencyRisk.Transition.REOPEN));

    var fakeClient = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start(fakeClient);

    var comment = "I confirm this is a risk";
    var response = backend.getDependencyRiskService().changeStatus(new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey,
      DependencyRiskTransition.CONFIRM, comment));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    verify(fakeClient, timeout(2000).times(1)).didChangeDependencyRisks(any(), any(), any(), any());
    assertThat(fakeClient.getDependencyRiskChanges())
      .extracting(DidChangeDependencyRisksParams::getAddedDependencyRisks, DidChangeDependencyRisksParams::getClosedDependencyRiskIds)
      .containsExactly(tuple(List.of(), Set.of()));
    assertThat(fakeClient.getDependencyRiskChanges().get(0).getUpdatedDependencyRisks())
      .extracting(DependencyRiskDto::getId, DependencyRiskDto::getStatus, DependencyRiskDto::getTransitions)
      .containsExactly(tuple(dependencyRiskKey, DependencyRiskDto.Status.CONFIRM, List.of(DependencyRiskDto.Transition.REOPEN, DependencyRiskDto.Transition.SAFE, DependencyRiskDto.Transition.ACCEPT)));
  }

  @SonarLintTest
  void it_should_notify_the_client_with_updated_dependency_risk_when_reopening_a_server_matched_dependency_risk(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.ACCEPT)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.REOPEN));

    var fakeClient = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start(fakeClient);

    var comment = "I confirm this is a risk";
    var response = backend.getDependencyRiskService().changeStatus(new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey,
      DependencyRiskTransition.REOPEN, comment));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    verify(fakeClient, timeout(2000).times(1)).didChangeDependencyRisks(any(), any(), any(), any());
    assertThat(fakeClient.getDependencyRiskChanges())
      .extracting(DidChangeDependencyRisksParams::getAddedDependencyRisks, DidChangeDependencyRisksParams::getClosedDependencyRiskIds)
      .containsExactly(tuple(List.of(), Set.of()));
    assertThat(fakeClient.getDependencyRiskChanges().get(0).getUpdatedDependencyRisks())
      .extracting(DependencyRiskDto::getId, DependencyRiskDto::getStatus, DependencyRiskDto::getTransitions)
      .containsExactly(tuple(dependencyRiskKey, DependencyRiskDto.Status.OPEN,
        List.of(DependencyRiskDto.Transition.CONFIRM, DependencyRiskDto.Transition.SAFE, DependencyRiskDto.Transition.ACCEPT)));
  }

  @SonarLintTest
  void it_should_throw_when_dependency_risk_does_not_exist(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME)))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var nonExistentKey = UUID.randomUUID();
    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, nonExistentKey, DependencyRiskTransition.CONFIRM, null);
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Dependency Risk with key " + nonExistentKey + " was not found");
  }

  @SonarLintTest
  void it_should_throw_when_transition_is_not_allowed(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.MAINTAINABILITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM
        // ACCEPT is not in the allowed transitions
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, "comment");
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Transition ACCEPT is not allowed for this dependency risk");
  }

  @SonarLintTest
  void it_should_throw_when_accept_transition_has_no_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.RELIABILITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, null);
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT and SAFE transitions");
  }

  @SonarLintTest
  void it_should_throw_when_safe_transition_has_no_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.SAFE
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.SAFE, null);
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT and SAFE transitions");
  }

  @SonarLintTest
  void it_should_succeed_when_accept_transition_has_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, "This is acceptable");
    var scaService = backend.getDependencyRiskService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"This is acceptable"
        }
      """, dependencyRiskKey);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_succeed_when_safe_transition_has_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.SAFE
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.SAFE, "This is safe");
    var scaService = backend.getDependencyRiskService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"SAFE",
          "comment":"This is safe"
        }
      """, dependencyRiskKey);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_fail_when_server_returns_error(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM
      ));

    var server = harness.newFakeSonarQubeServer().withStatus(DOWN).start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.CONFIRM, null);
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOfSatisfying(ResponseErrorException.class, ex -> {
        assertThat(ex.getResponseError().getData().toString()).contains("Error 404 on", "/api/v2/sca/issues-releases/change-status");
      });
  }

  @SonarLintTest
  void it_should_handle_multiple_dependency_risks_with_different_transitions(SonarLintTestHarness harness) {
    var dependencyRiskKey1 = UUID.randomUUID();
    var dependencyRiskKey2 = UUID.randomUUID();

    var dependencyRisk1 = aServerDependencyRisk()
      .withKey(dependencyRiskKey1)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable1")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM,
        ServerDependencyRisk.Transition.REOPEN
      ));

    var dependencyRisk2 = aServerDependencyRisk()
      .withKey(dependencyRiskKey2)
      .withType(ServerDependencyRisk.Type.PROHIBITED_LICENSE)
      .withSeverity(ServerDependencyRisk.Severity.BLOCKER)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withPackageName("com.example.prohibited")
      .withPackageVersion("2.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch
          .withDependencyRisk(dependencyRisk1)
          .withDependencyRisk(dependencyRisk2))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var scaService = backend.getDependencyRiskService();

    // Test first issue with CONFIRM transition
    var params1 = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey1, DependencyRiskTransition.CONFIRM, null);
    var response1 = scaService.changeStatus(params1);
    assertThat(response1).succeedsWithin(Duration.ofSeconds(2));

    // Test second issue with ACCEPT transition and comment
    var params2 = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey2, DependencyRiskTransition.ACCEPT, "License is acceptable");
    var response2 = scaService.changeStatus(params2);
    assertThat(response2).succeedsWithin(Duration.ofSeconds(2));

    var expectedJson1 = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"CONFIRM"
        }
      """, dependencyRiskKey1);

    var expectedJson2 = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"License is acceptable"
        }
      """, dependencyRiskKey2);

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
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, "");
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT and SAFE transitions");
  }

  @SonarLintTest
  void it_should_handle_whitespace_only_comment_for_accept_transition(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, "   ");
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Comment is required for ACCEPT and SAFE transitions");
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_with_different_severities(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.critical")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.CONFIRM,
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, "Critical issue accepted");
    var scaService = backend.getDependencyRiskService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"Critical issue accepted"
        }
      """, dependencyRiskKey);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_with_long_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var longComment = "This is a very long comment that exceeds the typical length of a normal comment. " +
      "It contains multiple sentences and should be handled properly by the dependency risk status change functionality. " +
      "The comment should be truncated or handled appropriately by the server.";

    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, longComment);
    var scaService = backend.getDependencyRiskService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"%s"
        }
      """, dependencyRiskKey, longComment);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_with_special_characters_in_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var specialComment = "Comment with special chars: \"quotes\", 'apostrophes', & < > symbols, and \n newlines \t tabs";

    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, specialComment);
    var scaService = backend.getDependencyRiskService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"%s"
        }
      """, dependencyRiskKey, specialComment
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
  void it_should_handle_dependency_risk_with_unicode_characters_in_comment(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var unicodeComment = "Comment with unicode: 🚀 emoji, éñtîôñs, 中文, русский, العربية";

    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of(
        ServerDependencyRisk.Transition.ACCEPT
      ));

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.ACCEPT, unicodeComment);
    var scaService = backend.getDependencyRiskService();

    var response = scaService.changeStatus(params);
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var expectedJson = String.format("""
        {
          "issueReleaseKey":"%s",
          "transitionKey":"ACCEPT",
          "comment":"%s"
        }
      """, dependencyRiskKey, unicodeComment);
    waitAtMost(2, SECONDS).untilAsserted(() -> {
      server.getMockServer()
        .verify(WireMock.postRequestedFor(urlEqualTo("/api/v2/sca/issues-releases/change-status"))
          .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
          .withRequestBody(equalToJson(expectedJson)));
    });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_with_no_transitions_available(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
    var dependencyRisk = aServerDependencyRisk()
      .withKey(dependencyRiskKey)
      .withType(ServerDependencyRisk.Type.VULNERABILITY)
      .withSeverity(ServerDependencyRisk.Severity.HIGH)
      .withStatus(ServerDependencyRisk.Status.OPEN)
      .withQuality(ServerDependencyRisk.SoftwareQuality.SECURITY)
      .withPackageName("com.example.vulnerable")
      .withPackageVersion("1.0.0")
      .withTransitions(List.of()); // No transitions available

    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch(BRANCH_NAME))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server.baseUrl(), storage -> storage
        .withProject(PROJECT_KEY, project -> project.withMainBranch(BRANCH_NAME, branch -> branch.withDependencyRisk(dependencyRisk))))
      .withBoundConfigScope(CONFIGURATION_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var params = new ChangeDependencyRiskStatusParams(CONFIGURATION_SCOPE_ID, dependencyRiskKey, DependencyRiskTransition.CONFIRM, null);
    var scaService = backend.getDependencyRiskService();

    assertThat(scaService.changeStatus(params))
      .failsWithin(2, TimeUnit.SECONDS)
      .withThrowableOfType(ExecutionException.class)
      .withCauseExactlyInstanceOf(ResponseErrorException.class)
      .withMessageContaining("Transition CONFIRM is not allowed for this dependency risk");
  }
}
