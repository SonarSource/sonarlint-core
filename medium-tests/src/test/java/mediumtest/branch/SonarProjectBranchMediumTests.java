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
package mediumtest.branch;

import java.time.Duration;
import java.util.Set;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.PROJECT_SYNCHRONIZATION;

class SonarProjectBranchMediumTests {

  @SonarLintTest
  void it_should_not_request_client_to_match_branch_when_vcs_repo_change_occurs_on_unbound_project(SonarLintTestHarness harness) throws InterruptedException {
    var client = harness.newFakeClient().build();

    var backend = harness.newBackend()
      .withUnboundConfigScope("configScopeId")
      .start(client);

    notifyVcsRepositoryChanged(backend, "configScopeId");

    Thread.sleep(200);
    verify(client, never()).matchSonarProjectBranch(any(), any(), any(), any());
    verify(client, never()).didChangeMatchedSonarProjectBranch(any(), any());
  }

  @SonarLintTest
  void it_should_request_client_to_match_branch_when_vcs_repo_change_occurs_on_bound_project(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    when(client.matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any())).thenReturn("myBranch");

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    notifyVcsRepositoryChanged(backend, "configScopeId");

    verify(client, timeout(2000)).didChangeMatchedSonarProjectBranch("configScopeId", "myBranch");
  }

  @SonarLintTest
  void it_should_not_notify_client_if_matched_branch_did_not_change(SonarLintTestHarness harness) throws InterruptedException {
    var client = harness.newFakeClient()
      .build();
    when(client.matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any())).thenReturn("myBranch");

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    // Wait for the first branch matching
    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), any());

    // Trigger another branch matching
    notifyVcsRepositoryChanged(backend, "configScopeId");

    verify(client, timeout(1000).times(2)).matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());
    Thread.sleep(200);
    verify(client, times(1)).didChangeMatchedSonarProjectBranch(any(), any());
  }

  @SonarLintTest
  void it_should_default_to_the_main_branch_if_client_unable_to_match_branch(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    when(client.matchSonarProjectBranch(any(), any(), any(), any())).thenReturn(null);

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    notifyVcsRepositoryChanged(backend, "configScopeId");

    verify(client, timeout(2000)).didChangeMatchedSonarProjectBranch("configScopeId", "main");
  }

  @SonarLintTest
  void it_should_not_match_any_branch_if_there_is_none_in_the_storage(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    notifyVcsRepositoryChanged(backend, "configScopeId");

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Cannot match Sonar branch, storage is empty"));
    verify(client, never()).matchSonarProjectBranch(any(), any(), any(), any());
    verify(client, never()).didChangeMatchedSonarProjectBranch(any(), any());
  }

  @SonarLintTest
  void it_should_not_notify_client_when_error_occurs_during_client_branch_matching_and_default_to_main_branch(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    when(client.matchSonarProjectBranch(any(), any(), any(), any())).thenThrow(new ConfigScopeNotFoundException());
    harness.newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "main");
  }

  @SonarLintTest
  void verify_that_multiple_quick_branch_notifications_are_not_running_in_race_conditions(SonarLintTestHarness harness) {
    var client = harness.newFakeClient()
      .printLogsToStdOut()
      .build();
    doReturn("branchA", "branchB").when(client).matchSonarProjectBranch(any(), any(), any(), any());
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    // Wait for initial branch matching
    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch("configScopeId", "branchA");

    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));

    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch("configScopeId", "branchB");
  }

  @SonarLintTest
  void it_should_return_matched_branch_after_matching(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    doReturn("main", "myBranch")
      .when(client).matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    // Initial matching
    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "main");

    notifyVcsRepositoryChanged(backend, "configScopeId");

    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "myBranch");

    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("configScopeId")))
      .succeedsWithin(Duration.ofSeconds(1))
      .extracting(GetMatchedSonarProjectBranchResponse::getMatchedSonarProjectBranch)
      .isEqualTo("myBranch");
  }

  @SonarLintTest
  void it_should_trigger_branch_specific_synchronization_if_the_branch_changed(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("myBranch", branch -> branch.withIssue("issueKey")))
      .start();
    var client = harness.newFakeClient()
      .build();
    doReturn("main", "myBranch")
      .when(client)
      .matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBackendCapability(PROJECT_SYNCHRONIZATION)
      .start(client);

    // Wait for first sync
    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), eq("main"));
    verify(client, timeout(5000)).didSynchronizeConfigurationScopes(Set.of("configScopeId"));

    // Now emulate a branch change
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));

    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), eq("myBranch"));
    verify(client, timeout(5000).times(2)).didSynchronizeConfigurationScopes(Set.of("configScopeId"));
  }

  @SonarLintTest
  void it_should_clear_the_matched_branch_when_the_binding_changes(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("myBranch", branch -> branch.withIssue("issueKey")))
      .start();
    var client = harness.newFakeClient().build();
    doReturn("myBranch")
      .when(client)
      .matchSonarProjectBranch(eq("configScopeId"), any(), any(), any());

    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .start(client);

    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), eq("myBranch"));

    // Emulate a binding change to a project having no branches
    bind(backend, "configScopeId", "connectionId", "projectKey2");

    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("configScopeId")))
      .succeedsWithin(Duration.ofSeconds(5))
      .extracting(GetMatchedSonarProjectBranchResponse::getMatchedSonarProjectBranch)
      .isNull();
  }

  @SonarLintTest
  void it_should_match_project_branch(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();

    client.matchProjectBranch(any(), any(), any());

    verify(client).matchProjectBranch(any(), any(), any());
  }

  private void bind(SonarLintTestRpcServer backend, String configScopeId, String connectionId, String projectKey) {
    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, projectKey, true)));
  }

  private void notifyVcsRepositoryChanged(SonarLintTestRpcServer backend, String configScopeId) {
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(configScopeId));
  }
}
