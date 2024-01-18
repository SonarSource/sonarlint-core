/*
 * SonarLint Core - Medium Tests
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
package mediumtest.branch;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
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

class SonarProjectBranchMediumTests {

  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void it_should_not_request_client_to_match_branch_when_vcs_repo_change_occurs_on_unbound_project() throws InterruptedException {
    var client = newFakeClient().build();

    backend = newBackend()
      .withUnboundConfigScope("configScopeId")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    Thread.sleep(200);
    verify(client, never()).matchSonarProjectBranch(any(), any(), any(), any());
    verify(client, never()).didChangeMatchedSonarProjectBranch(any(), any());
  }

  @Test
  void it_should_request_client_to_match_branch_when_vcs_repo_change_occurs_on_bound_project() {
    var client = newFakeClient().build();
    when(client.matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any())).thenReturn("myBranch");

    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    verify(client, timeout(2000)).didChangeMatchedSonarProjectBranch("configScopeId", "myBranch");
  }

  @Test
  void it_should_not_notify_client_if_matched_branch_did_not_change() throws InterruptedException {
    var client = newFakeClient()
      .build();
    when(client.matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any())).thenReturn("myBranch");

    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    // Wait for the first branch matching
    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), any());

    // Trigger another branch matching
    notifyVcsRepositoryChanged("configScopeId");

    verify(client, timeout(1000).times(2)).matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());
    Thread.sleep(200);
    verify(client, times(1)).didChangeMatchedSonarProjectBranch(any(), any());
  }

  @Test
  void it_should_default_to_the_main_branch_if_client_unable_to_match_branch() {
    var client = newFakeClient().build();
    when(client.matchSonarProjectBranch(any(), any(), any(), any())).thenReturn(null);

    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "main");
  }

  @Test
  void it_should_not_match_any_branch_if_there_is_none_in_the_storage() {
    var client = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Cannot match Sonar branch, storage is empty"));
    verify(client, never()).matchSonarProjectBranch(any(), any(), any(), any());
    verify(client, never()).didChangeMatchedSonarProjectBranch(any(), any());
  }

  @Test
  void it_should_not_notify_client_when_error_occurs_during_client_branch_matching_and_default_to_main_branch() {
    var client = newFakeClient().build();
    when(client.matchSonarProjectBranch(any(), any(), any(), any())).thenThrow(new ConfigScopeNotFoundException());
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "main");
  }

  @Test
  void verify_that_multiple_quick_branch_notifications_are_not_running_in_race_conditions() {
    var client = newFakeClient()
      .printLogsToStdOut()
      .build();
    doReturn("branchA").when(client).matchSonarProjectBranch(any(), any(), any(), any());
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));
    doReturn("branchB").when(client).matchSonarProjectBranch(any(), any(), any(), any());
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));
    doReturn("branchC").when(client).matchSonarProjectBranch(any(), any(), any(), any());
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));
    doReturn("branchD").when(client).matchSonarProjectBranch(any(), any(), any(), any());
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));

    // FIXME in a perfect world we would like to have a single notification for branchD, but there is a possible race condition if the branch matching job is cancelled *after*
    // having already send the event, then we can receive multiple notifications. This is not a big deal, but we should try to fix it some day.
    verify(client, timeout(5000).atLeastOnce()).didChangeMatchedSonarProjectBranch("configScopeId", "branchD");
  }

  @Test
  void it_should_return_matched_branch_after_matching() {
    var client = newFakeClient().build();
    when(client.matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any()))
      .thenReturn("main");

    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    // Initial matching
    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "main");

    // Emulate a different response from client
    when(client.matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any())).thenReturn("myBranch");

    notifyVcsRepositoryChanged("configScopeId");

    verify(client, timeout(1000)).didChangeMatchedSonarProjectBranch("configScopeId", "myBranch");

    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("configScopeId")))
      .succeedsWithin(Duration.ofSeconds(1))
      .extracting(GetMatchedSonarProjectBranchResponse::getMatchedSonarProjectBranch)
      .isEqualTo("myBranch");
  }

  @Test
  void it_should_trigger_branch_specific_synchronization_if_the_branch_changed() {
    var server = newSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("myBranch", branch -> branch.withIssue("issueKey")))
      .start();
    var client = newFakeClient()
      .build();
    doReturn("main").when(client).matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withProjectSynchronization()
      .build(client);

    // Wait for first sync
    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), eq("main"));
    verify(client, timeout(5000)).didSynchronizeConfigurationScopes(Set.of("configScopeId"));

    // Now emulate a branch change
    doReturn("myBranch").when(client).matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams("configScopeId"));

    verify(client, timeout(5000)).didChangeMatchedSonarProjectBranch(eq("configScopeId"), eq("myBranch"));
    verify(client, timeout(5000).times(2)).didSynchronizeConfigurationScopes(Set.of("configScopeId"));
  }

  @Test
  void it_should_clear_the_matched_branch_when_the_binding_changes() {
    var server = newSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("myBranch", branch -> branch.withIssue("issueKey")))
      .start();
    var client = newFakeClient().build();
    doReturn("oldBranchName").when(client).matchSonarProjectBranch(eq("configScopeId"), eq("main"), eq(Set.of("main", "myBranch")), any());
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    doReturn("branchName").when(client).matchSonarProjectBranch(eq("configScopeId"), any(), any(), any());
    bind("configScopeId", "connectionId", "projectKey2");

    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("configScopeId")))
      .succeedsWithin(Duration.ofSeconds(1))
      .extracting(GetMatchedSonarProjectBranchResponse::getMatchedSonarProjectBranch)
      .isNull();
  }

  private void bind(String configScopeId, String connectionId, String projectKey) {
    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(connectionId, projectKey, true)));
  }

  private void notifyVcsRepositoryChanged(String configScopeId) {
    backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(configScopeId));
  }
}
