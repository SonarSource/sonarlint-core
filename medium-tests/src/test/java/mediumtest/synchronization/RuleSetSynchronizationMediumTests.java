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
package mediumtest.synchronization;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class RuleSetSynchronizationMediumTests {

  @SonarLintTest
  void it_should_pull_active_ruleset_from_server(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("ruleKey", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .withProject("projectKey", project -> project.withQualityProfile("qpKey").withBranch("main"))
      .start();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .start();

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> assertThat(getAnalyzerConfigFile(backend, "connectionId", "projectKey"))
      .exists()
      .extracting(this::readRuleSets, as(InstanceOfAssertFactories.map(String.class, Sonarlint.RuleSet.class)))
      .hasSize(1)
      .extractingByKey("java")
      .extracting(Sonarlint.RuleSet::getRuleList, as(LIST))
      .containsExactly(Sonarlint.RuleSet.ActiveRule.newBuilder().setRuleKey("ruleKey").setSeverity("MAJOR").build()));
  }

  @SonarLintTest
  void it_should_not_pull_when_server_is_down(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.3")
      .withStatus(ServerFixture.ServerStatus.DOWN)
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("ruleKey", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .withProject("projectKey", project -> project.withQualityProfile("qpKey").withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server)
      .withFullSynchronization()
      .start(client);

    addConfigurationScope(backend, "configScopeId", "connectionId", "projectKey");

    waitAtMost(3, SECONDS).untilAsserted(() -> {
      assertThat(getAnalyzerConfigFile(backend, "connectionId", "projectKey")).doesNotExist();
      assertThat(client.getLogMessages()).contains("Error during synchronization");
    });
  }

  private void addConfigurationScope(SonarLintTestRpcServer backend, String configScopeId, String connectionId, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(configScopeId, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));
  }

  private Path getAnalyzerConfigFile(SonarLintTestRpcServer backend, String connectionId, String projectKey) {
    return backend.getStorageRoot().resolve(encodeForFs(connectionId)).resolve("projects").resolve(encodeForFs(projectKey)).resolve("analyzer_config.pb");
  }

  private Map<String, Sonarlint.RuleSet> readRuleSets(Path protoFilePath) {
    return ProtobufFileUtil.readFile(protoFilePath, Sonarlint.AnalyzerConfiguration.parser()).getRuleSetsByLanguageKeyMap();
  }
}
