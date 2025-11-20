/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.embedded.server.EmbeddedServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiHookServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_generate_nodejs_script_when_nodejs_detected() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64120);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(HookScriptType.NODEJS));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getScriptFileName()).isEqualTo("sonarqube_analysis_hook.js");
    assertThat(response.getScriptContent())
      .contains("#!/usr/bin/env node")
      .contains("hostname: 'localhost'")
      .contains("STARTING_PORT = 64120")
      .contains("ENDING_PORT = 64130")
      .contains("path: '/sonarlint/api/analysis/files'")
      .contains("path: '/sonarlint/api/status'");
    assertThat(response.getConfigFileName()).isEqualTo("hooks.json");
    assertThat(response.getConfigContent())
      .contains("\"post_write_code\"")
      .contains("{{SCRIPT_PATH}}")
      .contains("\"show_output\": true");
  }

  @Test
  void it_should_generate_python_script_when_python_detected() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64121);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(HookScriptType.PYTHON));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getScriptFileName()).isEqualTo("sonarqube_analysis_hook.py");
    assertThat(response.getScriptContent())
      .contains("#!/usr/bin/env python3")
      .contains("STARTING_PORT = 64120")
      .contains("ENDING_PORT = 64130")
      .contains("/sonarlint/api/analysis/files")
      .contains("/sonarlint/api/status");
    assertThat(response.getConfigFileName()).isEqualTo("hooks.json");
    assertThat(response.getConfigContent())
      .contains("\"post_write_code\"")
      .contains("{{SCRIPT_PATH}}")
      .contains("\"show_output\": true");
  }

  @Test
  void it_should_generate_bash_script_when_bash_detected() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64122);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(HookScriptType.BASH));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getScriptFileName()).isEqualTo("sonarqube_analysis_hook.sh");
    assertThat(response.getScriptContent())
      .contains("#!/bin/bash")
      .contains("STARTING_PORT=64120")
      .contains("ENDING_PORT=64130")
      .contains("/sonarlint/api/analysis/files")
      .contains("/sonarlint/api/status");
    assertThat(response.getConfigFileName()).isEqualTo("hooks.json");
    assertThat(response.getConfigContent())
      .contains("\"post_write_code\"")
      .contains("{{SCRIPT_PATH}}")
      .contains("\"show_output\": true");
  }

  @Test
  void it_should_throw_exception_when_embedded_server_not_started() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(-1);

    var service = new AiHookService(embeddedServer, executableLocator);

    assertThatThrownBy(() -> service.getHookScriptContent(AiAgent.WINDSURF))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Embedded server is not started");
  }

  @Test
  void it_should_throw_exception_when_no_executable_found() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64120);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.empty());

    var service = new AiHookService(embeddedServer, executableLocator);

    assertThatThrownBy(() -> service.getHookScriptContent(AiAgent.WINDSURF))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No suitable executable found");
  }

  @Test
  void it_should_embed_correct_agent_in_script_comment_for_windsurf() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64120);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(HookScriptType.NODEJS));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getScriptContent())
      .contains("SonarQube for IDE Windsurf Hook")
      .contains("EXPECTED_IDE_NAME = 'Windsurf'");
  }

  @Test
  void it_should_throw_exception_for_unsupported_cursor_agent() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64120);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(HookScriptType.PYTHON));

    var service = new AiHookService(embeddedServer, executableLocator);

    assertThatThrownBy(() -> service.getHookScriptContent(AiAgent.CURSOR))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessageContaining("hook configuration not yet implemented");
  }

  @Test
  void it_should_throw_exception_for_unsupported_github_copilot_agent() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64120);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(HookScriptType.BASH));

    var service = new AiHookService(embeddedServer, executableLocator);

    assertThatThrownBy(() -> service.getHookScriptContent(AiAgent.GITHUB_COPILOT))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessageContaining("GitHub Copilot does not support hooks");
  }

}

