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
package mediumtest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintBackendFixture.FakeSonarLintClient.ProgressReport;
import mediumtest.fixtures.SonarLintBackendFixture.FakeSonarLintClient.ProgressStep;
import mediumtest.fixtures.SonarLintTestBackend;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.branch.DidChangeActiveSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.clientapi.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.commons.TextRange;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

class SynchronizationMediumTests {

  @Test
  void it_should_automatically_synchronize_bound_projects_that_have_an_active_branch() {
    var serverWithIssues = newSonarQubeServer("9.6")
      .withProject("projectKey",
        project -> project.withBranch("branchName",
          branch -> branch.withIssue("key", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", new TextRange(1, 0, 3, 4))))
      .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", serverWithIssues)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "branchName")
      .withProjectSynchronization()
      .build();

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(backend.getWorkDir()).isDirectoryContaining(path -> path.getFileName().toString().contains("xodus-issue-store"));
      assertThat(backend.getNewCodeService().getNewCodeDefinition(new GetNewCodeDefinitionParams("configScopeId"))).isCompleted();
    });
  }

  @Test
  void it_should_automatically_synchronize_bound_projects_when_the_active_branch_changes() {
    var fakeClient = newFakeClient().build();
    var serverWithIssues = newSonarQubeServer("9.6")
      .withProject("projectKey",
        project -> project.withBranch("branchName",
          branch -> branch.withIssue("key", "ruleKey", "msg", "author", "file/path", "REVIEWED", "SAFE", new TextRange(1, 0, 3, 4))))
      .withSourceFile("projectKey:file/path", sourceFile -> sourceFile.withCode("source\ncode\nfile"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", serverWithIssues)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withProjectSynchronization()
      .build(fakeClient);

    backend.getSonarProjectBranchService().didChangeActiveSonarProjectBranch(new DidChangeActiveSonarProjectBranchParams("configScopeId", "branchName"));

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(fakeClient.getSynchronizedConfigScopeIds()).contains("configScopeId");
    });
  }

  @Test
  void it_should_report_progress_to_the_client_when_synchronizing() {
    var fakeClient = newFakeClient().build();
    var serverWithIssues = newSonarQubeServer("9.6")
      .withProject("projectKey", project -> project.withEmptyBranch("branchName"))
      .withProject("projectKey2", project -> project.withEmptyBranch("branchName2"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", serverWithIssues)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "branchName")
      .withBoundConfigScope("configScopeId2", "connectionId", "projectKey2", "branchName2")
      .withProjectSynchronization()
      .build(fakeClient);

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(fakeClient.getProgressReportsByTaskId())
        .hasKeySatisfying(isUUID())
        .containsValue(new ProgressReport(null, "Synchronizing projects...", null, false, false,
          List.of(
            new ProgressStep("Synchronizing with 'connectionId'...", 0),
            new ProgressStep("Synchronizing project 'projectKey'...", 0),
            new ProgressStep("Synchronizing project 'projectKey2'...", 50)),
          true));
    });
  }

  @Test
  void it_should_not_report_progress_to_the_client_when_synchronizing_if_client_rejects_progress() {
    var fakeClient = newFakeClient().rejectingProgress().build();
    var serverWithIssues = newSonarQubeServer("9.6")
      .withProject("projectKey", project -> project.withEmptyBranch("branchName"))
      .withProject("projectKey2", project -> project.withEmptyBranch("branchName2"))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", serverWithIssues)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "branchName")
      .withBoundConfigScope("configScopeId2", "connectionId", "projectKey2", "branchName2")
      .withProjectSynchronization()
      .build(fakeClient);

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
  }

  private SonarLintTestBackend backend;
}
