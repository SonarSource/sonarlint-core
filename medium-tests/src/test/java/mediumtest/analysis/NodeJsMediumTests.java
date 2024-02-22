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
package mediumtest.analysis;

import java.nio.file.Paths;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;

class NodeJsMediumTests {

  private static final String JAVASCRIPT_S1481 = "javascript:S1481";

  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown();
    }
  }

  @Test
  void wrong_node_path_can_still_load_rules() {
    var client = newFakeClient().build();
    backend = newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .withClientNodeJsPath(Paths.get("wrong"))
      .build(client);

    var globalConfig = backend.getAnalysisService().getGlobalStandaloneConfiguration().join();

    assertThat(globalConfig.getNodeJsPath()).isNull();
    assertThat(globalConfig.getNodeJsVersion()).isNull();
    assertThat(client.getLogMessages()).contains("Unable to query node version");

    var ruleDetails = backend.getRulesService().getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams(JAVASCRIPT_S1481)).join().getRuleDefinition();
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS);
    assertThat(ruleDetails.getSeverity()).isEqualTo(IssueSeverity.MINOR);
  }

  @Test
  void can_retrieve_auto_detected_node_js() {
    var client = newFakeClient().build();
    backend = newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .build(client);

    var nodeJsDetails = backend.getAnalysisService().getAutoDetectedNodeJs().join().getDetails();

    assertThat(nodeJsDetails).isNotNull();
    assertThat(nodeJsDetails.getPath()).isNotNull();
    assertThat(nodeJsDetails.getVersion()).isNotNull();
  }

}
