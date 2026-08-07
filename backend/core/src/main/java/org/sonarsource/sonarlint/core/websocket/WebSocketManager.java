/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.websocket;

import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;
import org.sonarsource.sonarlint.core.event.SonarServerEventReceivedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.concurrent.TimeUnit.SECONDS;

public class WebSocketManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String RETRY_INITIAL_DELAY_PROPERTY = "sonarlint.internal.websocket.retry.initialDelay";

  private SonarCloudWebSocket sonarCloudWebSocket;
  private final Set<String> connectionIdsInterestedInNotifications = new HashSet<>();
  private String connectionIdUsedToCreateConnection;
  private final Map<String, String> subscribedProjectKeysByConfigScopes = new HashMap<>();
  private final ScheduledExecutorService executorService;
  private final ApplicationEventPublisher eventPublisher;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final ConfigurationRepository configurationRepository;
  private final URI websocketEndpointUri;
  private final AtomicReference<ScheduledFuture<?>> pendingRetry = new AtomicReference<>();
  private volatile Attempt currentAttempt = new Attempt();

  public WebSocketManager(ApplicationEventPublisher eventPublisher, SonarQubeClientManager sonarQubeClientManager, ConfigurationRepository configurationRepository,
    URI websocketEndpointUri) {
    this(eventPublisher, sonarQubeClientManager, configurationRepository, websocketEndpointUri,
      FailSafeExecutors.newSingleThreadScheduledExecutor("sonarlint-websocket-subscriber"));
  }

  WebSocketManager(ApplicationEventPublisher eventPublisher, SonarQubeClientManager sonarQubeClientManager, ConfigurationRepository configurationRepository,
    URI websocketEndpointUri, ScheduledExecutorService executorService) {
    this.eventPublisher = eventPublisher;
    this.sonarQubeClientManager = sonarQubeClientManager;
    this.configurationRepository = configurationRepository;
    this.websocketEndpointUri = websocketEndpointUri;
    this.executorService = executorService;
  }

  private void handleSonarServerEvent(SonarServerEvent event) {
    connectionIdsInterestedInNotifications.forEach(id -> eventPublisher.publishEvent(new SonarServerEventReceivedEvent(id, event)));
  }

  public void forgetConnection(String connectionId, String reason) {
    var previouslyInterestedInNotifications = connectionIdsInterestedInNotifications.remove(connectionId);
    if (!previouslyInterestedInNotifications) {
      return;
    }
    if (connectionIdsInterestedInNotifications.isEmpty()) {
      closeSocket(reason);
      subscribedProjectKeysByConfigScopes.clear();
    } else if (this.connectionIdUsedToCreateConnection.equals(connectionId)) {
      // stop using the credentials, switch to another connection
      var otherConnectionId = connectionIdsInterestedInNotifications.stream().findAny().orElseThrow();
      removeProjectsFromSubscriptionListForConnection(connectionId);
      this.reopenConnection(otherConnectionId, reason + ", reopening for other SC connection");
    } else {
      configurationRepository.getBoundScopesToConnection(connectionId)
        .forEach(configScope -> forget(configScope.getConfigScopeId()));
    }
  }

  private void removeProjectsFromSubscriptionListForConnection(String updatedConnectionId) {
    var configurationScopesToUnsubscribe = configurationRepository.getBoundScopesToConnection(updatedConnectionId);
    for (var configScope : configurationScopesToUnsubscribe) {
      subscribedProjectKeysByConfigScopes.remove(configScope.getConfigScopeId());
    }
  }

  /**
   * @return the connection if it was or has been opened, else empty
   */
  public Optional<SonarCloudWebSocket> createConnectionIfNeeded(String connectionId) {
    connectionIdsInterestedInNotifications.add(connectionId);
    if (hasOpenConnection() || isConnectionPending()) {
      return Optional.of(sonarCloudWebSocket);
    }
    cancelPendingRetry();
    try {
      return sonarQubeClientManager.getValidWebSocketClient(connectionId)
        .map(webSocketClient -> {
          closeSocketIfPresent("Creating a new WebSocket connection");
          this.connectionIdUsedToCreateConnection = connectionId;
          this.sonarCloudWebSocket = SonarCloudWebSocket.create(this.websocketEndpointUri, webSocketClient, this::handleSonarServerEvent, this::reopenConnectionOnClose,
            this::onConnectionSucceeded, () -> onConnectionFailed(connectionId));
          return sonarCloudWebSocket;
        });
    } catch (Exception e) {
      LOG.error("Error while creating WebSocket connection", e);
      scheduleRetryAfterFailure(connectionId);
      return Optional.empty();
    }
  }

  public void reopenConnection(String connectionId, String reason) {
    cancelPendingRetry();
    currentAttempt = new Attempt();
    closeSocketIfPresent(reason);
    createConnectionIfNeeded(connectionId)
      .ifPresent(connection -> resubscribeAll());
  }

  protected void reopenConnectionOnClose() {
    executorService.execute(() -> {
      var connectionId = connectionIdsInterestedInNotifications.stream().findFirst().orElse(null);
      if (this.sonarCloudWebSocket != null && connectionId != null) {
        // If connection already exists, close it and create new one before it expires on its own
        this.reopenConnection(connectionId, "WebSocket was closed by server or reached EOL");
      }
    });
  }

  private void onConnectionSucceeded() {
    executorService.execute(() -> {
      cancelPendingRetry();
      currentAttempt = new Attempt();
    });
  }

  private void onConnectionFailed(String connectionId) {
    executorService.execute(() -> scheduleRetryAfterFailure(connectionId));
  }

  private void scheduleRetryAfterFailure(String connectionId) {
    if (connectionId == null || !connectionIdsInterestedInNotifications.contains(connectionId)) {
      return;
    }
    if (currentAttempt.isMax()) {
      LOG.debug("Cannot connect to SonarCloud WebSocket, stop retrying");
      return;
    }
    var retryDelay = currentAttempt.delay;
    LOG.debug("Cannot connect to SonarCloud WebSocket, retrying in " + retryDelay + "s");
    var nextAttempt = currentAttempt.next();
    scheduleRetry(() -> {
      currentAttempt = nextAttempt;
      reopenConnectionWithoutResettingAttempt(connectionId, "Retrying after connection failure");
    }, retryDelay);
  }

  private void reopenConnectionWithoutResettingAttempt(String connectionId, String reason) {
    cancelPendingRetry();
    closeSocketIfPresent(reason);
    createConnectionIfNeeded(connectionId)
      .ifPresent(connection -> resubscribeAll());
  }

  private void scheduleRetry(Runnable task, long delayInSeconds) {
    if (!executorService.isShutdown()) {
      cancelPendingRetry();
      pendingRetry.set(executorService.schedule(task, delayInSeconds, SECONDS));
    }
  }

  private void cancelPendingRetry() {
    var pendingRetryOrNull = pendingRetry.getAndSet(null);
    if (pendingRetryOrNull != null) {
      pendingRetryOrNull.cancel(true);
    }
  }

  public void closeSocketIfNoMoreNeeded() {
    if (subscribedProjectKeysByConfigScopes.isEmpty()) {
      closeSocket("No more bound project");
    }
  }

  public void subscribe(String configScopeId, Binding binding) {
    createConnectionIfNeeded(binding.connectionId())
      .ifPresent(connection -> {
        var projectKey = binding.sonarProjectKey();
        if (subscribedProjectKeysByConfigScopes.containsKey(configScopeId) && !subscribedProjectKeysByConfigScopes.get(configScopeId).equals(projectKey)) {
          this.forget(configScopeId);
        }
        if (!subscribedProjectKeysByConfigScopes.containsValue(projectKey)) {
          connection.subscribe(projectKey);
        }
        subscribedProjectKeysByConfigScopes.put(configScopeId, projectKey);
      });
  }

  private void resubscribeAll() {
    var uniqueProjectKeys = new HashSet<>(subscribedProjectKeysByConfigScopes.values());
    uniqueProjectKeys.forEach(projectKey -> sonarCloudWebSocket.subscribe(projectKey));
  }

  public void closeSocket(String reason) {
    cancelPendingRetry();
    currentAttempt = new Attempt();
    closeSocketIfPresent(reason);
  }

  private void closeSocketIfPresent(String reason) {
    if (this.sonarCloudWebSocket != null) {
      var socket = this.sonarCloudWebSocket;
      this.sonarCloudWebSocket = null;
      this.connectionIdUsedToCreateConnection = null;
      socket.close(reason);
    }
  }

  public boolean hasOpenConnection() {
    return sonarCloudWebSocket != null && sonarCloudWebSocket.isOpen();
  }

  private boolean isConnectionPending() {
    return sonarCloudWebSocket != null && sonarCloudWebSocket.isConnecting();
  }

  public void forget(String configScopeId) {
    var projectKey = subscribedProjectKeysByConfigScopes.remove(configScopeId);
    if (projectKey != null && !subscribedProjectKeysByConfigScopes.containsValue(projectKey) && hasOpenConnection()) {
      sonarCloudWebSocket.unsubscribe(projectKey);
    }
  }

  public Map<String, String> getSubscribedProjectKeysByConfigScopes() {
    return subscribedProjectKeysByConfigScopes;
  }

  public boolean isInterestedInNotifications(String connectionId) {
    return connectionIdsInterestedInNotifications.contains(connectionId);
  }

  public void shutdown() {
    closeSocket("Backend is shutting down");
    subscribedProjectKeysByConfigScopes.clear();
    connectionIdsInterestedInNotifications.clear();
    if (!MoreExecutors.shutdownAndAwaitTermination(executorService, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop websocket manager executor service in a timely manner");
    }
  }

  static class Attempt {
    private static final int BACK_OFF_MULTIPLIER = 2;
    private static final int MAX_ATTEMPTS = 10;

    private final long delay;
    private final int attemptNumber;

    public Attempt() {
      this(initialDelaySeconds(), 1);
    }

    public Attempt(long delay, int attemptNumber) {
      this.delay = delay;
      this.attemptNumber = attemptNumber;
    }

    public Attempt next() {
      return new Attempt(delay * BACK_OFF_MULTIPLIER, attemptNumber + 1);
    }

    public boolean isMax() {
      return attemptNumber == MAX_ATTEMPTS;
    }

    private static long initialDelaySeconds() {
      return Long.parseLong(System.getProperty(RETRY_INITIAL_DELAY_PROPERTY, "60"));
    }
  }
}
