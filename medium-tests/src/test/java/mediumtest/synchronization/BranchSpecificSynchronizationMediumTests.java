/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package mediumtest.synchronization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Condition;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient.ProgressReport;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture.FakeSonarLintRpcClient.ProgressStep;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;

class BranchSpecificSynchronizationMediumTests {

  @SonarLintTest
  void it_should_automatically_synchronize_bound_projects_that_have_an_active_branch(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("9.9")
      .withProject("projectKey",
        project -> project.withBranch("main",
          branch -> branch.withIssue("key", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4))
            .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
      .start();

    var client = harness.newFakeClient().withMatchedBranch("configScopeId", "branchName").build();

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    waitAtMost(3, SECONDS)
      .untilAsserted(() -> assertThat(backend.getNewCodeService().getNewCodeDefinition(new GetNewCodeDefinitionParams("configScopeId"))).succeedsWithin(1, MINUTES));
  }

  @SonarLintTest
  void it_should_honor_binding_inheritance(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("9.9")
      .withProject("projectKey",
        project -> project
          .withBranch("branchNameParent",
            branch -> branch.withIssue("keyParent", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4))
              .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile")))
          .withBranch("branchNameChild",
            branch -> branch.withIssue("keyChild", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4))
              .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
      .start();

    var client = harness.newFakeClient()
      .withMatchedBranch("parentScope", "branchNameParent")
      .withMatchedBranch("childScope", "branchNameChild")
      .build();

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(client);

    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto("parentScope", null, true, "Parent", new BindingConfigurationDto("connectionId", "projectKey", true)),
        new ConfigurationScopeDto("childScope", "parentScope", true, "Child", new BindingConfigurationDto(null, null, true)))));

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(client.getLogs()).extracting(LogParams::getMessage).contains(
        "Matching Sonar project branch",
        "Matched Sonar project branch for configuration scope 'parentScope' changed from 'null' to 'branchNameParent'",
        "Matching Sonar project branch",
        "Matched Sonar project branch for configuration scope 'childScope' changed from 'null' to 'branchNameChild'",
        "[SYNC] Synchronizing issues for project 'projectKey' on branch 'branchNameParent'",
        "[SYNC] Synchronizing issues for project 'projectKey' on branch 'branchNameChild'");
    });

    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("parentScope")))
      .succeedsWithin(1, MINUTES)
      .matches(response -> "branchNameParent".equals(response.getMatchedSonarProjectBranch()));
    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("childScope")))
      .succeedsWithin(1, MINUTES)
      .matches(response -> "branchNameChild".equals(response.getMatchedSonarProjectBranch()));
  }

  @SonarLintTest
  void it_should_report_progress_to_the_client_when_synchronizing(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .build();
    var server = harness.newFakeSonarQubeServer("9.9")
      .withProject("projectKey")
      .withProject("projectKey2")
      .start();
    harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBoundConfigScope("configScopeId2", "connectionId", "projectKey2")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);

    fakeClient.waitForSynchronization();

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(fakeClient.getProgressReportsByTaskId())
      .hasKeySatisfying(isUUID())
      .containsValue(new ProgressReport(null, "Synchronizing projects...", null, false, false,
        List.of(
          new ProgressStep("Synchronizing with 'connectionId'...", 0),
          new ProgressStep("Synchronizing project 'projectKey'...", 0),
          new ProgressStep("Synchronizing project 'projectKey2'...", 50)),
        true)));
  }

  @SonarLintTest
  void it_should_not_report_progress_to_the_client_when_synchronizing_if_client_rejects_progress(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .build();
    doThrow(new UnsupportedOperationException("Failed to start progress"))
      .when(fakeClient)
      .startProgress(any());

    var server = harness.newFakeSonarQubeServer("9.9")
      .withProject("projectKey")
      .withProject("projectKey2")
      .start();
    harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBoundConfigScope("configScopeId2", "connectionId", "projectKey2")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);
    fakeClient.waitForSynchronization();

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(fakeClient.getSynchronizedConfigScopeIds()).contains("configScopeId");
      assertThat(fakeClient.getProgressReportsByTaskId()).isEmpty();
    });
  }

  @SonarLintTest
  void it_should_skip_second_consecutive_synchronization_for_the_same_server_project(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient()
      .build();
    var server = harness.newFakeSonarQubeServer("9.9")
      .withProject("projectKey")
      .withProject("projectKey2")
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(FULL_SYNCHRONIZATION)
      .start(fakeClient);
    fakeClient.waitForSynchronization();
    reset(fakeClient);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
      new ConfigurationScopeDto("configScope2", null, true, "Child1", new BindingConfigurationDto("connectionId", "projectKey", true)))));

    verify(fakeClient, after(2000).times(0)).didSynchronizeConfigurationScopes(any());
  }

  private static Condition<String> isUUID() {
      @Override
    return new Condition<>() {
      public boolean matches(String value) {
        try {
          UUID.fromString(value);
          return true;
        } catch (Exception e) {
          return false;
        }
      }
    };
  }
}
