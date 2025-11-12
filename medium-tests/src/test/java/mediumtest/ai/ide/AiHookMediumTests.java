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
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0);
    });

    var response = backend.getAiAgentService()
      .getHookScriptContent(new GetHookScriptContentParams(AiAgent.WINDSURF))
      .join();

    assertThat(response.getFileName()).matches("post_write_code\\.(js|py|sh)");
    assertThat(response.getContent()).contains("SonarQube for IDE WINDSURF Hook");
    assertThat(response.getContent()).contains("post_write_code");
    assertThat(response.getContent()).containsAnyOf(
      "http://localhost:" + backend.getEmbeddedServerPort(),
      "port: " + backend.getEmbeddedServerPort()
    );
    assertThat(response.getContent()).contains("/sonarlint/api/analysis/files");
  }

  @SonarLintTest
  void it_should_return_hook_script_content_for_cursor_with_embedded_port(SonarLintTestHarness harness) {
    var fakeClient = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withBackendCapability(EMBEDDED_SERVER)
      .withClientName("ClientName")
      .start(fakeClient);

    // Wait for embedded server to start
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      assertThat(backend.getEmbeddedServerPort()).isGreaterThan(0);
    });

    var response = backend.getAiAgentService()
      .getHookScriptContent(new GetHookScriptContentParams(AiAgent.CURSOR))
      .join();

    assertThat(response.getFileName()).matches("post_write_code\\.(js|py|sh)");

    assertThat(response.getContent()).contains("SonarQube for IDE CURSOR Hook");
    assertThat(response.getContent()).contains("post_write_code");
    assertThat(response.getContent()).containsAnyOf(
      "http://localhost:" + backend.getEmbeddedServerPort(),
      "port: " + backend.getEmbeddedServerPort()
    );
  }

}

