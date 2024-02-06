/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintBackendFixture.FakeSonarLintRpcClient.ProgressReport;
import mediumtest.fixtures.SonarLintBackendFixture.FakeSonarLintRpcClient.ProgressStep;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class BranchSpecificSynchronizationMediumTests {

  private ServerFixture.Server server;

  @Test
  void it_should_automatically_synchronize_bound_projects_that_have_an_active_branch() {
    server = newSonarQubeServer("9.6")
      .withProject("projectKey",
        project -> project.withBranch("main",
          branch -> branch.withIssue("key", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4))
            .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
      .start();

    var client = newFakeClient().withMatchedBranch("configScopeId", "branchName").build();

    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .build(client);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(backend.getWorkDir()).isDirectoryContaining(path -> path.getFileName().toString().contains("xodus-issue-store"));
      assertThat(backend.getNewCodeService().getNewCodeDefinition(new GetNewCodeDefinitionParams("configScopeId"))).succeedsWithin(1, MINUTES);
    });
  }

  @Test
  void it_should_honor_binding_inheritance() {
    server = newSonarQubeServer("9.6")
      .withProject("projectKey",
        project -> project
          .withBranch("branchNameParent",
            branch -> branch.withIssue("keyParent", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4))
              .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile")))
          .withBranch("branchNameChild",
            branch -> branch.withIssue("keyChild", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", Instant.now(), new TextRange(1, 0, 3, 4))
              .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))))
      .start();

    var client = newFakeClient()
      .withMatchedBranch("parentScope", "branchNameParent")
      .withMatchedBranch("childScope", "branchNameChild")
      .build();

    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .build(client);

    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto("parentScope", null, true, "Parent", new BindingConfigurationDto("connectionId", "projectKey", true)),
        new ConfigurationScopeDto("childScope", "parentScope", true, "Child", new BindingConfigurationDto(null, null, true)))));

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(client.getLogs()).extracting(LogParams::getMessage, LogParams::getConfigScopeId).contains(
        tuple("Matching Sonar project branch", "parentScope"),
        tuple("Matched Sonar project branch for configuration scope 'parentScope' changed from 'null' to 'branchNameParent'", "parentScope"),
        tuple("Matching Sonar project branch", "childScope"),
        tuple("Matched Sonar project branch for configuration scope 'childScope' changed from 'null' to 'branchNameChild'", "childScope"),
        tuple("[SYNC] Synchronizing issues for project 'projectKey' on branch 'branchNameParent'", null),
        tuple("[SYNC] Synchronizing issues for project 'projectKey' on branch 'branchNameChild'", null));
    });

    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("parentScope")))
      .succeedsWithin(1, MINUTES)
      .matches(response -> "branchNameParent".equals(response.getMatchedSonarProjectBranch()));
    assertThat(backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams("childScope")))
      .succeedsWithin(1, MINUTES)
      .matches(response -> "branchNameChild".equals(response.getMatchedSonarProjectBranch()));
  }

  @Test
  void it_should_report_progress_to_the_client_when_synchronizing() {
    var fakeClient = newFakeClient()
      .build();
    server = newSonarQubeServer("9.6")
      .withProject("projectKey")
      .withProject("projectKey2")
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBoundConfigScope("configScopeId2", "connectionId", "projectKey2")
      .withFullSynchronization()
      .build(fakeClient);

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

  @Test
  void it_should_not_report_progress_to_the_client_when_synchronizing_if_client_rejects_progress() {
    var fakeClient = newFakeClient()
      .build();
    doThrow(new UnsupportedOperationException("Failed to start progress"))
      .when(fakeClient)
      .startProgress(any());

    server = newSonarQubeServer("9.6")
      .withProject("projectKey")
      .withProject("projectKey2")
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withBoundConfigScope("configScopeId2", "connectionId", "projectKey2")
      .withFullSynchronization()
      .build(fakeClient);
    fakeClient.waitForSynchronization();

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(fakeClient.getSynchronizedConfigScopeIds()).contains("configScopeId");
      assertThat(fakeClient.getProgressReportsByTaskId()).isEmpty();
    });
  }

  private static Condition<String> isUUID() {
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

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
    }
  }

  private SonarLintTestRpcServer backend;
}
