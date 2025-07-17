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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ScaIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeScaIssuesParams;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SCA_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue.Severity;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue.Status;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue.Type;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerScaIssueFixtures.aServerScaIssue;

class ScaIssuesMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @SonarLintTest
  void it_should_return_no_sca_issues_if_the_scope_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(scaIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_sca_issues_if_the_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(scaIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_sca_issues_if_the_storage_is_empty(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(scaIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_return_the_stored_sca_issues(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withScaIssue(aServerScaIssue()
              .withKey(scaIssueKey)
              .withPackageName("com.example.vulnerable")
              .withPackageVersion("2.1.0")
              .withType(Type.VULNERABILITY)
              .withSeverity(Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(scaIssues)
      .extracting(ScaIssueDto::getId)
      .containsOnly(scaIssueKey));
  }

  @SonarLintTest
  void it_should_refresh_sca_issues_when_requested(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withScaIssue(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerScaIssue(scaIssueKey.toString(), "PROHIBITED_LICENSE", "HIGH", "OPEN", "com.example.vulnerable",
                "2.1.0", List.of("CONFIRM")))))
      .start();
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withGlobalSettings(Map.of("sonar.sca.enabled", "true"))
          .withProject(PROJECT_KEY, project -> project.withMainBranch("main")))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var refreshedScaIssues = refreshAndListAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(refreshedScaIssues)
      .extracting(ScaIssueDto::getId, ScaIssueDto::getType, ScaIssueDto::getSeverity, ScaIssueDto::getStatus, ScaIssueDto::getTransitions, ScaIssueDto::getPackageName,
        ScaIssueDto::getPackageVersion)
      .containsExactly(
        tuple(scaIssueKey, ScaIssueDto.Type.PROHIBITED_LICENSE, ScaIssueDto.Severity.HIGH, ScaIssueDto.Status.OPEN, List.of(ScaIssueDto.Transition.CONFIRM),
          "com.example.vulnerable", "2.1.0"));
  }

  @SonarLintTest
  void it_should_notify_client_when_new_sca_issues_are_added(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withGlobalSetting("sonar.sca.enabled", "true")
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withScaIssue(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerScaIssue(scaIssueKey.toString(), "VULNERABILITY", "HIGH", "OPEN", "com.example.vulnerable",
                "2.1.0", List.of("CONFIRM")))))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var scaChanges = client.getScaIssueChanges();
      assertThat(scaChanges).hasSize(1);
      var change = scaChanges.get(0);
      assertThat(change.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
      assertThat(change.getClosedScaIssueIds()).isEmpty();
      assertThat(change.getAddedScaIssues())
        .hasSize(1)
        .first()
        .satisfies(scaIssue -> {
          assertThat(scaIssue.getId()).isEqualTo(scaIssueKey);
          assertThat(scaIssue.getPackageName()).isEqualTo("com.example.vulnerable");
          assertThat(scaIssue.getPackageVersion()).isEqualTo("2.1.0");
          assertThat(scaIssue.getType()).isEqualTo(ScaIssueDto.Type.VULNERABILITY);
          assertThat(scaIssue.getSeverity()).isEqualTo(ScaIssueDto.Severity.HIGH);
        });
      assertThat(change.getUpdatedScaIssues()).isEmpty();
    });
  }

  @SonarLintTest
  void it_should_notify_client_when_sca_issues_are_removed(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withGlobalSetting("sonar.sca.enabled", "true")
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withScaIssue(aServerScaIssue()
              .withKey(scaIssueKey)
              .withPackageName("com.example.vulnerable")
              .withPackageVersion("2.1.0")
              .withType(Type.VULNERABILITY)
              .withSeverity(Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var scaChanges = client.getScaIssueChanges();
      assertThat(scaChanges)
        .extracting(DidChangeScaIssuesParams::getConfigurationScopeId, DidChangeScaIssuesParams::getClosedScaIssueIds, DidChangeScaIssuesParams::getAddedScaIssues,
          DidChangeScaIssuesParams::getUpdatedScaIssues)
        .containsExactly(tuple(CONFIG_SCOPE_ID, Set.of(scaIssueKey), List.of(), List.of()));
    });
  }

  @SonarLintTest
  void it_should_notify_client_when_sca_issues_are_updated(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withGlobalSetting("sonar.sca.enabled", "true")
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withScaIssue(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerScaIssue(scaIssueKey.toString(), "VULNERABILITY", "LOW", "ACCEPT", "com.example.vulnerable",
                "2.1.0", List.of("REOPEN")))))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withScaIssue(aServerScaIssue()
              .withKey(scaIssueKey)
              .withPackageName("com.example.vulnerable2")
              .withPackageVersion("0.1.2")
              .withType(Type.PROHIBITED_LICENSE)
              .withSeverity(Severity.HIGH)
              .withStatus(Status.OPEN)
              .withTransitions(List.of(ServerScaIssue.Transition.REOPEN))))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var scaChanges = client.getScaIssueChanges();
      assertThat(scaChanges).hasSize(1);
      var change = scaChanges.get(0);
      assertThat(change.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
      assertThat(change.getClosedScaIssueIds()).isEmpty();
      assertThat(change.getAddedScaIssues()).isEmpty();
      assertThat(change.getUpdatedScaIssues())
        .extracting(ScaIssueDto::getId, ScaIssueDto::getType, ScaIssueDto::getSeverity, ScaIssueDto::getStatus, ScaIssueDto::getTransitions, ScaIssueDto::getPackageName,
          ScaIssueDto::getPackageVersion)
        .containsExactly(
          tuple(scaIssueKey, ScaIssueDto.Type.VULNERABILITY, ScaIssueDto.Severity.LOW, ScaIssueDto.Status.ACCEPT, List.of(ScaIssueDto.Transition.REOPEN), "com.example.vulnerable",
            "2.1.0"));
    });
  }

  private List<ScaIssueDto> listAllScaIssues(SonarLintTestRpcServer backend, String configScopeId) {
    try {
      return backend.getScaIssueTrackingService().listAll(new ListAllParams(configScopeId)).get().getScaIssues();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private List<ScaIssueDto> refreshAndListAllScaIssues(SonarLintTestRpcServer backend, String configScopeId) {
    try {
      return backend.getScaIssueTrackingService().listAll(new ListAllParams(configScopeId, true)).get().getScaIssues();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
