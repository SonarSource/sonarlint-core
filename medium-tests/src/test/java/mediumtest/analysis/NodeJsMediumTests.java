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
package mediumtest.analysis;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeClientNodeJsPathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;

class NodeJsMediumTests {

  private static final String JAVASCRIPT_S1481 = "javascript:S1481";

  @SonarLintTest
  void wrong_node_path_prevents_loading_sonar_js_rules(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .withClientNodeJsPath(Paths.get("wrong"))
      .start(client);

    var futureRuleDetails = backend.getRulesService().getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams(JAVASCRIPT_S1481));

    assertThat(futureRuleDetails).failsWithin(Duration.ofSeconds(1))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("Could not find rule 'javascript:S1481' in embedded rules");
    assertThat(client.getLogMessages()).contains("Unable to query node version");
  }

  @SonarLintTest
  void can_retrieve_auto_detected_node_js(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .start(client);

    var nodeJsDetails = backend.getAnalysisService().getAutoDetectedNodeJs().join().getDetails();

    assertThat(nodeJsDetails).isNotNull();
    assertThat(nodeJsDetails.getPath()).isNotNull();
    assertThat(nodeJsDetails.getVersion()).isNotNull();
  }

  @SonarLintTest
  void can_retrieve_forced_node_js(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .start(client);

    var nodeJsDetails = backend.getAnalysisService().didChangeClientNodeJsPath(new DidChangeClientNodeJsPathParams(null)).join().getDetails();

    assertThat(nodeJsDetails).isNotNull();
    assertThat(nodeJsDetails.getPath()).isNotNull();
    assertThat(nodeJsDetails.getVersion()).isNotNull();
  }

}
