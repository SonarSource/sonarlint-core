/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.ConfigurationService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;

@Named
@Singleton
public class RequestHandlerBindingAssistant {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final BindingSuggestionProvider bindingSuggestionProvider;
  private final SonarLintRpcClient client;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationService configurationService;
  private final ExecutorService executorService;

  public RequestHandlerBindingAssistant(BindingSuggestionProvider bindingSuggestionProvider, SonarLintRpcClient client,
    ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationService configurationService) {
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.client = client;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationService = configurationService;
    this.executorService = new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(), r -> new Thread(r, "Show Issue or Hotspot Request Handler"));
  }

  void assistConnectionAndBindingIfNeededAsync(String serverUrl, String tokenName, String tokenValue, String projectKey, BiConsumer<String, String> andThen) {
    executorService.submit(() -> assistConnectionAndBindingIfNeeded(serverUrl, tokenName, tokenValue, projectKey, andThen));
  }

  private void assistConnectionAndBindingIfNeeded(String serverUrl, @Nullable String tokenName, @Nullable String tokenValue, String projectKey,
    BiConsumer<String, String> andThen) {
    try {
      var connectionsMatchingOrigin = connectionConfigurationRepository.findByUrl(serverUrl);
      if (connectionsMatchingOrigin.isEmpty()) {
        startFullBindingProcess();
        try {
          var assistNewConnectionResult = assistCreatingConnection(serverUrl, tokenName, tokenValue);

          var assistNewBindingResult = assistBinding(assistNewConnectionResult.getNewConnectionId(), projectKey);

          // Wait 5s for the connection to be created in the repository. This is happening asynchronously by the ConnectionService::didUpdateConnections event
          for (int i = 50; i >= 0; i--) {
            if (connectionConfigurationRepository.getConnectionsById().containsKey(assistNewConnectionResult.getNewConnectionId())) {
              break;
            }
            Thread.sleep(100);
          }
          andThen.accept(assistNewConnectionResult.getNewConnectionId(), assistNewBindingResult.getConfigurationScopeId());

        } finally {
          endFullBindingProcess();
        }
      } else {
        // we pick the first connection but this could lead to issues later if there were several matches (make the user select the right
        // one?)
        assistBindingIfNeeded(connectionsMatchingOrigin.get(0).getConnectionId(), projectKey, andThen);
      }
    } catch (Exception e) {
      LOG.error("Unable to show issue", e);
    }
  }

  private void assistBindingIfNeeded(String connectionId, String projectKey, BiConsumer<String, String> andThen) {
    var scopes = configurationService.getConfigScopesWithBindingConfiguredTo(connectionId, projectKey);
    if (scopes.isEmpty()) {
      var assistNewBindingResult = assistBinding(connectionId, projectKey);
      andThen.accept(connectionId, assistNewBindingResult.getConfigurationScopeId());
    } else {
      // we pick the first bound scope but this could lead to issues later if there were several matches (make the user select the right one?)
      andThen.accept(connectionId, scopes.get(0).getId());
    }
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

  AssistCreatingConnectionResponse assistCreatingConnection(String serverUrl, @Nullable String tokenName, @Nullable String tokenValue) {
    return client.assistCreatingConnection(new AssistCreatingConnectionParams(serverUrl, tokenName, tokenValue)).join();
  }

  NewBinding assistBinding(String connectionId, String projectKey) {
    var response = client.assistBinding(new AssistBindingParams(connectionId, projectKey)).join();
    return new NewBinding(connectionId, response.getConfigurationScopeId());
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

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop show issue request handler executor service in a timely manner");
    }
  }
}
