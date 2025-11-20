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
package mediumtest.ai.ide;

import java.time.Duration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetHookScriptContentParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;

class AiHookMediumTests {

  @SonarLintTest
  void it_should_return_hook_script_content_for_windsurf_with_embedded_port(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .withClientName("ClientName")
      .start(fakeClient);

    // Wait for embedded server to start
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var response = backend.getAiAgentService()
      .getHookScriptContent(new GetHookScriptContentParams(AiAgent.WINDSURF))
      .join();

    // Check script content
    assertThat(response.getScriptFileName()).matches("sonarqube_analysis_hook\\.(js|py|sh)");
    assertThat(response.getScriptContent())
      .contains("SonarQube for IDE Windsurf Hook")
      .contains("sonarqube_analysis_hook")
      .contains("/sonarlint/api/analysis/files")
      .contains("/sonarlint/api/status")
      .contains("STARTING_PORT")
      .contains("ENDING_PORT")
      .containsAnyOf(
        "EXPECTED_IDE_NAME = 'Windsurf'",  // JS/Python
        "EXPECTED_IDE_NAME=\"Windsurf\""   // Bash
      );

    // Check config content
    assertThat(response.getConfigFileName()).isEqualTo("hooks.json");
    assertThat(response.getConfigContent()).contains("\"post_write_code\"");
    assertThat(response.getConfigContent()).contains("{{SCRIPT_PATH}}");
    assertThat(response.getConfigContent()).contains("\"show_output\": true");
  }

  @SonarLintTest
  void it_should_throw_exception_for_cursor_not_yet_implemented(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .withClientName("ClientName")
      .start(fakeClient);

    // Wait for embedded server to start
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0));

    var futureResponse = backend.getAiAgentService()
      .getHookScriptContent(new GetHookScriptContentParams(AiAgent.CURSOR));

    assertThat(futureResponse)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableThat()
      .withCauseInstanceOf(UnsupportedOperationException.class)
      .withMessageContaining("hook configuration not yet implemented");
  }

}

