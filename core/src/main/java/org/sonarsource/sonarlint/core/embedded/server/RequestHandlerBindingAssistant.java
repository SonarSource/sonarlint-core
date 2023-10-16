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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
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

  public RequestHandlerBindingAssistant(BindingSuggestionProvider bindingSuggestionProvider, SonarLintRpcClient client, ConnectionConfigurationRepository connectionConfigurationRepository,
    ConfigurationService configurationService) {
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.client = client;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationService = configurationService;
    this.executorService = new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(), r -> new Thread(r, "Show Issue or Hotspot Request Handler"));
  }

  void assistConnectionAndBindingIfNeededAsync(String serverUrl, String projectKey, BiConsumer<String, String> andThen) {
    executorService.submit(() -> assistConnectionAndBindingIfNeeded(serverUrl, projectKey, andThen));
  }

  private void assistConnectionAndBindingIfNeeded(String serverUrl, String projectKey, BiConsumer<String, String> andThen) {
    try {
      var connectionsMatchingOrigin = connectionConfigurationRepository.findByUrl(serverUrl);
      if (connectionsMatchingOrigin.isEmpty()) {
        startFullBindingProcess();
        try {
          var assistNewConnectionResult = assistCreatingConnection(serverUrl);
          if (assistNewConnectionResult.isEmpty()) {
            return;
          }

          var assistNewBindingResult = assistBinding(assistNewConnectionResult.get().getNewConnectionId(), projectKey);
          if (assistNewBindingResult.isEmpty()) {
            return;
          }

          // Wait 5s for the connection to be created in the repository. This is happening asynchronously by the ConnectionService::didUpdateConnections event
          for (int i = 50; i >= 0; i--) {
            if (connectionConfigurationRepository.getConnectionsById().containsKey(assistNewConnectionResult.get().getNewConnectionId())) {
              break;
            }
            Thread.sleep(100);
          }
          andThen.accept(assistNewConnectionResult.get().getNewConnectionId(), assistNewBindingResult.get().getConfigurationScopeId());

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
      if (assistNewBindingResult.isEmpty()) {
        return;
      }
      andThen.accept(connectionId, assistNewBindingResult.get().getConfigurationScopeId());
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

  Optional<AssistCreatingConnectionResponse> assistCreatingConnection(String serverUrl) {
    try {
      return Optional.of(client.assistCreatingConnection(new AssistCreatingConnectionParams(serverUrl)).get());
    } catch (InterruptedException e) {
      LOG.debug("Interrupted!", e);
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResponseErrorException && ((ResponseErrorException) e.getCause()).getResponseError().getCode() == ResponseErrorCode.ServerCancelled.getValue()) {
        LOG.debug("Assist creating connection cancelled by the SonarLint client", e);
        return Optional.empty();
      } else {
        LOG.error("Assist creating connection failed", e);
        return Optional.empty();
      }
    }
  }

  Optional<NewBinding> assistBinding(String connectionId, String projectKey) {
    try {
      return Optional.of(client.assistBinding(new AssistBindingParams(connectionId, projectKey)).get()).map(response -> new NewBinding(connectionId, response.getConfigurationScopeId()));
    } catch (InterruptedException e) {
      LOG.debug("Interrupted!", e);
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResponseErrorException && ((ResponseErrorException) e.getCause()).getResponseError().getCode() == ResponseErrorCode.ServerCancelled.getValue()) {
        LOG.debug("Assist binding cancelled by the SonarLint client", e);
        return Optional.empty();
      } else {
        LOG.error("Assist binding failed", e);
        return Optional.empty();
      }
    }
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
