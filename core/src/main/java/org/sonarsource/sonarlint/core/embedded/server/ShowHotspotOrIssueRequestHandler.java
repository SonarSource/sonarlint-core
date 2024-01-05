/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.BindingSuggestionProviderImpl;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;

import javax.annotation.Nullable;

public class ShowHotspotOrIssueRequestHandler {
  private final BindingSuggestionProviderImpl bindingSuggestionProvider;
  private final SonarLintClient client;

  public ShowHotspotOrIssueRequestHandler(BindingSuggestionProviderImpl bindingSuggestionProvider, SonarLintClient client) {
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.client = client;
  }

  void startFullBindingProcess() {
    // we don't want binding suggestions to appear in the middle of a full binding creation process (connection + binding)
    // the other possibility would be to still notify the client anyway and let it handle UI interactions one at a time (assists, messages,
    // suggestions, ...)
    bindingSuggestionProvider.disable();
  }

  void endFullBindingProcess() {
    bindingSuggestionProvider.enable();
  }

  CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(String serverUrl) {
    return assistCreatingConnection(serverUrl, null, null);
  }

  CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(String serverUrl,
    @Nullable String tokenName, @Nullable String tokenValue) {
    return client.assistCreatingConnection(new AssistCreatingConnectionParams(serverUrl, tokenName, tokenValue));
  }

  CompletableFuture<NewBinding> assistBinding(String connectionId, String projectKey) {
    return client.assistBinding(new AssistBindingParams(connectionId, projectKey))
      .thenApply(response -> new NewBinding(connectionId, response.getConfigurationScopeId()));
  }

  static class NewBinding {
    private final String connectionId;
    private final String configurationScopeId;

    private NewBinding(String connectionId, String configurationScopeId) {
      this.connectionId = connectionId;
      this.configurationScopeId = configurationScopeId;
    }

    public String getConnectionId() {
      return connectionId;
    }

    public String getConfigurationScopeId() {
      return configurationScopeId;
    }
  }
}
