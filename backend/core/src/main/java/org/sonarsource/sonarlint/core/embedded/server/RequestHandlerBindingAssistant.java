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
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.BindingCandidatesFinder;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ExecutorServiceShutdownWatchable;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.RevokeTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;

@Named
@Singleton
public class RequestHandlerBindingAssistant {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final BindingSuggestionProvider bindingSuggestionProvider;
  private final BindingCandidatesFinder bindingCandidatesFinder;
  private final SonarLintRpcClient client;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final ConfigurationRepository configurationRepository;
  private final UserTokenService userTokenService;
  private final ExecutorServiceShutdownWatchable<?> executorService;
  private final String sonarCloudUrl;

  public RequestHandlerBindingAssistant(BindingSuggestionProvider bindingSuggestionProvider, BindingCandidatesFinder bindingCandidatesFinder,
    SonarLintRpcClient client, ConnectionConfigurationRepository connectionConfigurationRepository, ConfigurationRepository configurationRepository,
    UserTokenService userTokenService, SonarCloudActiveEnvironment sonarCloudActiveEnvironment) {
    this.bindingSuggestionProvider = bindingSuggestionProvider;
    this.bindingCandidatesFinder = bindingCandidatesFinder;
    this.client = client;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.configurationRepository = configurationRepository;
    this.userTokenService = userTokenService;
    this.executorService = new ExecutorServiceShutdownWatchable<>(new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(), r -> new Thread(r, "Show Issue or Hotspot Request Handler")));
    this.sonarCloudUrl = sonarCloudActiveEnvironment.getUri().toString();
  }

  interface Callback {
    void andThen(String connectionId, @Nullable String configurationScopeId, SonarLintCancelMonitor cancelMonitor);
  }

  void assistConnectionAndBindingIfNeededAsync(AssistCreatingConnectionParams connectionParams, String projectKey, Callback callback) {
    var cancelMonitor = new SonarLintCancelMonitor();
    cancelMonitor.watchForShutdown(executorService);
    executorService.submit(() -> assistConnectionAndBindingIfNeeded(connectionParams, projectKey, callback, cancelMonitor));
  }

  private void assistConnectionAndBindingIfNeeded(AssistCreatingConnectionParams connectionParams, String projectKey,
    Callback callback, SonarLintCancelMonitor cancelMonitor) {
    var serverUrl = getServerUrl(connectionParams);
    LOG.debug("Assist connection and binding if needed for project {} and server {}", projectKey, serverUrl);
    try {
      var isSonarCloud = connectionParams.getConnectionParams().isRight();
      var connectionsMatchingOrigin = isSonarCloud ?
        connectionConfigurationRepository.findByOrganization(connectionParams.getConnectionParams().getRight().getOrganizationKey()) :
        connectionConfigurationRepository.findByUrl(serverUrl);
      if (connectionsMatchingOrigin.isEmpty()) {
        startFullBindingProcess();
        try {
          var assistNewConnectionResult = assistCreatingConnectionAndWaitForRepositoryUpdate(connectionParams, cancelMonitor);
          var assistNewBindingResult = assistBindingAndWaitForRepositoryUpdate(assistNewConnectionResult.getNewConnectionId(), isSonarCloud,
            projectKey, cancelMonitor);
          callback.andThen(assistNewConnectionResult.getNewConnectionId(), assistNewBindingResult.getConfigurationScopeId(), cancelMonitor);
        } finally {
          endFullBindingProcess();
        }
      } else {
        // we pick the first connection but this could lead to issues later if there were several matches (make the user select the right
        // one?)
        assistBindingIfNeeded(connectionsMatchingOrigin.get(0).getConnectionId(), isSonarCloud, projectKey, callback, cancelMonitor);
      }
    } catch (Exception e) {
      LOG.error("Unable to show issue", e);
    }
  }

  private String getServerUrl(AssistCreatingConnectionParams connectionParams) {
    return connectionParams.getConnectionParams().isLeft() ? connectionParams.getConnectionParams().getLeft().getServerUrl() : sonarCloudUrl;
  }

  private AssistCreatingConnectionResponse assistCreatingConnectionAndWaitForRepositoryUpdate(
    AssistCreatingConnectionParams connectionParams, SonarLintCancelMonitor cancelMonitor) {
    var assistNewConnectionResult = assistCreatingConnection(connectionParams, cancelMonitor);

    // Wait 5s for the connection to be created in the repository. This is happening asynchronously by the
    // ConnectionService::didUpdateConnections event
    LOG.debug("Waiting for connection creation notification...");
    for (var i = 50; i >= 0; i--) {
      if (connectionConfigurationRepository.getConnectionsById().containsKey(assistNewConnectionResult.getNewConnectionId())) {
        break;
      }
      sleep();
    }
    if (!connectionConfigurationRepository.getConnectionsById().containsKey(assistNewConnectionResult.getNewConnectionId())) {
      LOG.warn("Did not receive connection creation notification on a timely manner");
      throw new CancellationException();
    }

    return assistNewConnectionResult;
  }

  private void assistBindingIfNeeded(String connectionId, boolean isSonarCloud, String projectKey, Callback callback, SonarLintCancelMonitor cancelMonitor) {
    var scopes = configurationRepository.getBoundScopesToConnectionAndSonarProject(connectionId, projectKey);
    if (scopes.isEmpty()) {
      var assistNewBindingResult = assistBindingAndWaitForRepositoryUpdate(connectionId, isSonarCloud, projectKey, cancelMonitor);
      callback.andThen(connectionId, assistNewBindingResult.getConfigurationScopeId(), cancelMonitor);
    } else {
      // we pick the first bound scope but this could lead to issues later if there were several matches (make the user select the right one?)
      callback.andThen(connectionId, scopes.iterator().next().getConfigScopeId(), cancelMonitor);
    }
  }

  private NewBinding assistBindingAndWaitForRepositoryUpdate(String connectionId, boolean isSonarCloud, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    var assistNewBindingResult = assistBinding(connectionId, isSonarCloud, projectKey, cancelMonitor);
    // Wait 5s for the binding to be created in the repository. This is happening asynchronously by the
    // ConfigurationService::didUpdateBinding event
    var configurationScopeId = assistNewBindingResult.getConfigurationScopeId();
    if (configurationScopeId != null) {
      LOG.debug("Waiting for binding creation notification...");
      for (var i = 50; i >= 0; i--) {
        if (configurationRepository.getEffectiveBinding(configurationScopeId).isPresent()) {
          break;
        }
        sleep();
      }
      if (configurationRepository.getEffectiveBinding(configurationScopeId).isEmpty()) {
        LOG.warn("Did not receive binding creation notification on a timely manner");
        throw new CancellationException();
      }
    }

    return assistNewBindingResult;
  }

  private static void sleep() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted!");
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

  AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams connectionParams, SonarLintCancelMonitor cancelMonitor) {
    try {
      var future = client.assistCreatingConnection(connectionParams);
      cancelMonitor.onCancel(() -> future.cancel(true));
      return future.join();
    } catch (Exception e) {
      revokeToken(connectionParams, cancelMonitor);
      throw e;
    }
  }

  private void revokeToken(AssistCreatingConnectionParams connectionParams, SonarLintCancelMonitor cancelMonitor) {
    String tokenName = connectionParams.getTokenName();
    String tokenValue = connectionParams.getTokenValue();
    if (tokenName != null && tokenValue != null) {
      var revokeTokenParams = new RevokeTokenParams(getServerUrl(connectionParams), tokenName, tokenValue);
      userTokenService.revokeToken(revokeTokenParams, cancelMonitor);
    }
  }

  NewBinding assistBinding(String connectionId, boolean isSonarCloud, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    var configScopeCandidates = bindingCandidatesFinder.findConfigScopesToBind(connectionId, projectKey, cancelMonitor);
    // For now, we decided to only support automatic binding if there is only one clear candidate
    if (configScopeCandidates.size() != 1) {
      client.noBindingSuggestionFound(new NoBindingSuggestionFoundParams(projectKey, isSonarCloud));
      return new NewBinding(connectionId, null);
    }
    var bindableConfig = configScopeCandidates.iterator().next();
    var future = client.assistBinding(new AssistBindingParams(connectionId, projectKey, bindableConfig.getConfigurationScope().getId(),
      bindableConfig.isFromSharedConfiguration()));
    cancelMonitor.onCancel(() -> future.cancel(true));
    var response = future.join();
    return new NewBinding(connectionId, response.getConfigurationScopeId());
  }


  static class NewBinding {
    private final String connectionId;
    private final String configurationScopeId;

    private NewBinding(String connectionId, @Nullable String configurationScopeId) {
      this.connectionId = connectionId;
      this.configurationScopeId = configurationScopeId;
    }

    public String getConnectionId() {
      return connectionId;
    }

    @CheckForNull
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
