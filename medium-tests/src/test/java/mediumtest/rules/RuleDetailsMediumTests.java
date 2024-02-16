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
package mediumtest.rules;

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

import static mediumtest.fixtures.ServerFixture.newSonarCloudServer;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class RuleDetailsMediumTests {

  private SonarLintTestRpcServer backend;
  private static String oldSonarCloudUrl;

  @BeforeEach
  void prepare() {
    oldSonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url");
  }

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (oldSonarCloudUrl == null) {
      System.clearProperty("sonarlint.internal.sonarcloud.url");
    } else {
      System.setProperty("sonarlint.internal.sonarcloud.url", oldSonarCloudUrl);
    }
  }

  @Test
  void it_should_return_details_from_embedded_secrets_rules_when_sonarqube_less_than_9_9() {
    var server = newSonarQubeServer("9.8")
      .withProject("projectKey",
        project -> project.withBranch("branchName"))
      .start();
    backend = newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withServerSentEventsEnabled()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .build();

    var ruleDetails = backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams("configScope", "secrets:S6290")).join();

    assertThat(ruleDetails.getType()).isEqualTo(RuleType.VULNERABILITY);
  }

  @Test
  void it_should_return_details_from_server_when_sonarqube_9_9_plus() {
    var server = newSonarQubeServer()
      .withProject("projectKey",
        project -> project.withBranch("branchName"))
      .start();
    backend = newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withServerSentEventsEnabled()
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("9.9").withProject("projectKey", project -> project.withRuleSet("secrets", ruleSet -> ruleSet.withActiveRule("secrets:S6290", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .build();

    var ruleDetails = backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams("configScope", "secrets:S6290")).join();

    assertThat(ruleDetails.getType())
      .isEqualTo(RuleType.VULNERABILITY);
  }

  @Test
  void it_should_return_details_from_server_when_sonarcloud() {
    var server = newSonarCloudServer()
      .withProject("projectKey",
        project -> project.withBranch("branchName"))
      .start();
    System.setProperty("sonarlint.internal.sonarcloud.url", server.baseUrl());
    backend = newBackend()
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.TEXT)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .withServerSentEventsEnabled()
      .withSonarCloudConnection("connectionId", storage -> storage.withPlugin(TestPlugin.TEXT).withProject("projectKey", project -> project.withRuleSet("secrets", ruleSet -> ruleSet.withActiveRule("secrets:S6290", "MAJOR"))))
      .withBoundConfigScope("configScope", "connectionId", "projectKey")
      .build();

    var ruleDetails = backend.getAnalysisService().getRuleDetails(new GetRuleDetailsParams("configScope", "secrets:S6290")).join();

    assertThat(ruleDetails.getType()).isEqualTo(RuleType.VULNERABILITY);
  }
}
