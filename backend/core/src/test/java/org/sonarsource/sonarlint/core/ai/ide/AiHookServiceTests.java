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
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(ExecutableType.NODEJS));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getFileName()).isEqualTo("post_write_code.js");
    assertThat(response.getContent())
      .contains("#!/usr/bin/env node")
      .contains("hostname: 'localhost'")
      .contains("port: 64120")
      .contains("path: '/sonarlint/api/analysis/files'");
  }

  @Test
  void it_should_generate_python_script_when_python_detected() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64121);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(ExecutableType.PYTHON));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.CURSOR);

    assertThat(response.getFileName()).isEqualTo("post_write_code.py");
    assertThat(response.getContent())
      .contains("#!/usr/bin/env python3")
      .contains("http://localhost:64121/sonarlint/api/analysis/files");
  }

  @Test
  void it_should_generate_bash_script_when_bash_detected() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64122);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(ExecutableType.BASH));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getFileName()).isEqualTo("post_write_code.sh");
    assertThat(response.getContent())
      .contains("#!/bin/bash")
      .contains("http://localhost:64122/sonarlint/api/analysis/files");
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
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(ExecutableType.NODEJS));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.WINDSURF);

    assertThat(response.getContent()).contains("SonarQube for IDE WINDSURF Hook");
  }

  @Test
  void it_should_embed_correct_agent_in_script_comment_for_cursor() {
    var embeddedServer = mock(EmbeddedServer.class);
    var executableLocator = mock(ExecutableLocator.class);

    when(embeddedServer.getPort()).thenReturn(64120);
    when(executableLocator.detectBestExecutable()).thenReturn(Optional.of(ExecutableType.PYTHON));

    var service = new AiHookService(embeddedServer, executableLocator);
    var response = service.getHookScriptContent(AiAgent.CURSOR);

    assertThat(response.getContent()).contains("SonarQube for IDE CURSOR Hook");
  }
}

