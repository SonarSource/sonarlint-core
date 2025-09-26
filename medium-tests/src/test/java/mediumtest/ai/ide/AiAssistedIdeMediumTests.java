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
package mediumtest.ai.ide;

import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAssistedIde;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentParams;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistedIdeMediumTests {

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_cursor(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAssistedIdeRpcService().getRuleFileContent(new GetRuleFileContentParams(AiAssistedIde.CURSOR)).join();

    assertThat(response.getContent()).contains("alwaysApply: true");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_vscode(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAssistedIdeRpcService().getRuleFileContent(new GetRuleFileContentParams(AiAssistedIde.VSCODE)).join();

    assertThat(response.getContent()).doesNotContain("alwaysApply: true");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

  @SonarLintTest
  void it_should_return_the_rule_file_content_for_windsurf(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var response = backend.getAiAssistedIdeRpcService().getRuleFileContent(new GetRuleFileContentParams(AiAssistedIde.WINDSURF)).join();

    assertThat(response.getContent()).doesNotContain("alwaysApply: true");
    assertThat(response.getContent()).contains("Important Tool Guidelines");
  }

}
