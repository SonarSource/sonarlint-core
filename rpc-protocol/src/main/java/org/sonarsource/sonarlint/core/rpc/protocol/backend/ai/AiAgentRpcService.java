/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("ai")
public interface AiAgentRpcService {

  /**
   * Returns the content of rule file to be written to each IDE's rule folder, based on the agent.
   * The rule file provides good practices to the agent.
   */
  @JsonRequest
  CompletableFuture<GetRuleFileContentResponse> getRuleFileContent(GetRuleFileContentParams params);

  /**
   * Returns hook script content with auto-detected executable type.
   * The hook script will analyze code after write events using the sonarqube_analysis_hook hook.
   */
  @JsonRequest
  CompletableFuture<GetHookScriptContentResponse> getHookScriptContent(GetHookScriptContentParams params);

  /**
   * Returns the capabilities of the agents detected by the client together with the current
   * SonarQube CLI installation and authentication state.
   */
  @JsonRequest
  CompletableFuture<GetAiIntegrationStateResponse> getIntegrationState(GetAiIntegrationStateParams params);

  /**
   * Prepares the OS-specific SonarQube CLI installation command for the client's native interactive terminal.
   * Credentials are never included in the response.
   */
  @JsonRequest
  CompletableFuture<PrepareCliCommandResponse> prepareInstallCommand();

  /**
   * Prepares {@code sonar auth login} for the client's native interactive terminal.
   * Credentials are never included in the response.
   * The request fails if no usable SonarQube CLI installation is found; clients should check
   * {@link #getIntegrationState(GetAiIntegrationStateParams)} first.
   */
  @JsonRequest
  CompletableFuture<PrepareCliCommandResponse> prepareAuthenticateCommand(PrepareAuthenticateCliCommandParams params);

  /**
   * Prepares {@code sonar integrate <agent> --global} for the client's native interactive terminal.
   * Credentials are never included in the response.
   * The request fails if no usable CLI installation is found, or if the agent is missing or not
   * supported by the CLI (see {@link AiIntegrationAgentCapability#isCliIntegrationSupported()}).
   */
  @JsonRequest
  CompletableFuture<PrepareCliCommandResponse> prepareIntegrateCommand(PrepareIntegrateCliCommandParams params);

}
