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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.SonarProjectBranches;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;

class SonarProjectBranchMediumTests {

  private SonarLintTestRpcServer backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_not_request_client_to_match_branch_when_vcs_repo_change_occurs_on_unbound_project() {
    var client = newFakeClient().withMatchedSonarProjectBranch("myBranch").build();
    backend = newBackend()
      .withUnboundConfigScope("configScopeId")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().during(Duration.ofMillis(200)).untilAsserted(() -> {
      assertThat(client.getToMatchSonarProjectBranchesPerConfigScopeId()).isEmpty();
      assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).isEmpty();
    });
  }

  @Test
  void it_should_request_client_to_match_branch_when_vcs_repo_change_occurs_on_bound_project() {
    var client = newFakeClient().withMatchedSonarProjectBranch("myBranch").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
      assertThat(client.getToMatchSonarProjectBranchesPerConfigScopeId())
        .containsOnlyKeys("configScopeId")
        .extractingByKey("configScopeId")
        .extracting(SonarProjectBranches::getMainBranchName, SonarProjectBranches::getAllBranchesNames)
        .containsExactly("main", Set.of("main", "myBranch"));
      assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).containsOnly(entry("configScopeId", "myBranch"));
    });
  }

  @Test
  void it_should_not_notify_client_if_matched_branch_did_not_change() {
    var client = newFakeClient().withMatchedSonarProjectBranch("myBranch").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withMatchedBranch("configScopeId", "myBranch")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
      assertThat(client.getToMatchSonarProjectBranchesPerConfigScopeId())
        .containsOnlyKeys("configScopeId")
        .extractingByKey("configScopeId")
        .extracting(SonarProjectBranches::getMainBranchName, SonarProjectBranches::getAllBranchesNames)
        .containsExactly("main", Set.of("main", "myBranch"));
      assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).isEmpty();
    });
  }

  @Test
  void it_should_default_to_the_main_branch_if_client_unable_to_match_branch() {
    var client = newFakeClient().withMatchedSonarProjectBranch(null).build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withMatchedBranch("configScopeId", "myBranch")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
      assertThat(client.getToMatchSonarProjectBranchesPerConfigScopeId())
        .containsOnlyKeys("configScopeId")
        .extractingByKey("configScopeId")
        .extracting(SonarProjectBranches::getMainBranchName, SonarProjectBranches::getAllBranchesNames)
        .containsExactly("main", Set.of("main", "myBranch"));
      assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).containsOnly(entry("configScopeId", "main"));
    });
  }

  @Test
  void it_should_not_match_any_branch_if_there_is_none_in_the_storage() {
    var client = newFakeClient().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withMatchedBranch("configScopeId", "myBranch")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().during(Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(client.getToMatchSonarProjectBranchesPerConfigScopeId()).isEmpty();
      assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).isEmpty();
      assertThat(client.getLogMessages()).contains("Cannot match Sonar branch, storage is empty");
    });
  }

  @Test
  void it_should_not_notify_client_when_error_occurs_during_client_branch_matching_and_default_to_main_branch() {
    var client = newFakeClient().withSonarProjectBranchMatchingError().build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withMatchedBranch("configScopeId", "myBranch")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
      assertThat(client.getToMatchSonarProjectBranchesPerConfigScopeId())
        .containsOnlyKeys("configScopeId")
        .extractingByKey("configScopeId")
        .extracting(SonarProjectBranches::getMainBranchName, SonarProjectBranches::getAllBranchesNames)
        .containsExactly("main", Set.of("main", "myBranch"));
      assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).contains(entry("configScopeId", "main"));
    });
  }

  @Test
  void it_should_return_matched_branch_after_matching() {
    var client = newFakeClient().withMatchedSonarProjectBranch("myBranch").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(client.getMatchedSonarProjectBranchPerConfigScopeId()).containsOnly(entry("configScopeId", "myBranch")));
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
    var client = newFakeClient().withMatchedSonarProjectBranch("myBranch").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withProjectSynchronization()
      .build(client);

    notifyVcsRepositoryChanged("configScopeId");

    await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(backend.getIssueStorageService()
      .connection("connectionId")
      .project("projectKey")
      .findings()
      .containsIssue("issueKey", false))
        .isTrue());
  }

  @Test
  void it_should_clear_the_matched_branch_when_the_binding_changes() {
    var server = newSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("myBranch", branch -> branch.withIssue("issueKey")))
      .start();
    var client = newFakeClient().withMatchedSonarProjectBranch("myBranch").build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main").withNonMainBranch("myBranch")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withMatchedBranch("configScopeId", "myOldBranch")
      .build(client);

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
