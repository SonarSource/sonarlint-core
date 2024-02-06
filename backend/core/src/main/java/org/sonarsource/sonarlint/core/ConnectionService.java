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
package org.sonarsource.sonarlint.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.OrganizationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.toMap;

@Named
@Singleton
public class ConnectionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ApplicationEventPublisher applicationEventPublisher;
  private final ConnectionConfigurationRepository repository;
  private final ServerApiProvider serverApiProvider;
  private final TokenGeneratorHelper tokenGeneratorHelper;

  @Inject
  public ConnectionService(ApplicationEventPublisher applicationEventPublisher, ConnectionConfigurationRepository repository, InitializeParams params,
    TokenGeneratorHelper tokenGeneratorHelper, ServerApiProvider serverApiProvider) {
    this(applicationEventPublisher, repository, params.getSonarQubeConnections(), params.getSonarCloudConnections(), serverApiProvider, tokenGeneratorHelper);
  }

  ConnectionService(ApplicationEventPublisher applicationEventPublisher, ConnectionConfigurationRepository repository,
    @Nullable List<SonarQubeConnectionConfigurationDto> initSonarQubeConnections, @Nullable List<SonarCloudConnectionConfigurationDto> initSonarCloudConnections,
    ServerApiProvider serverApiProvider, TokenGeneratorHelper tokenGeneratorHelper) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.repository = repository;
    this.serverApiProvider = serverApiProvider;
    this.tokenGeneratorHelper = tokenGeneratorHelper;
    if (initSonarQubeConnections != null) {
      initSonarQubeConnections.forEach(c -> repository.addOrReplace(adapt(c)));
    }
    if (initSonarCloudConnections != null) {
      initSonarCloudConnections.forEach(c -> repository.addOrReplace(adapt(c)));
    }
  }

  private static SonarQubeConnectionConfiguration adapt(SonarQubeConnectionConfigurationDto sqDto) {
    return new SonarQubeConnectionConfiguration(sqDto.getConnectionId(), sqDto.getServerUrl(), sqDto.getDisableNotifications());
  }

  private static SonarCloudConnectionConfiguration adapt(SonarCloudConnectionConfigurationDto scDto) {
    return new SonarCloudConnectionConfiguration(scDto.getConnectionId(), scDto.getOrganization(), scDto.isDisableNotifications());
  }

  private static void putAndLogIfDuplicateId(Map<String, AbstractConnectionConfiguration> map, AbstractConnectionConfiguration config) {
    if (map.put(config.getConnectionId(), config) != null) {
      LOG.error("Duplicate connection registered: {}", config.getConnectionId());
    }
  }

  public void didUpdateConnections(List<SonarQubeConnectionConfigurationDto> sonarQubeConnections, List<SonarCloudConnectionConfigurationDto> sonarCloudConnections) {
    var newConnectionsById = new HashMap<String, AbstractConnectionConfiguration>();
    sonarQubeConnections.forEach(config -> putAndLogIfDuplicateId(newConnectionsById, adapt(config)));
    sonarCloudConnections.forEach(config -> putAndLogIfDuplicateId(newConnectionsById, adapt(config)));

    var previousConnectionsById = repository.getConnectionsById();

    var updatedConnections = newConnectionsById.entrySet().stream()
      .filter(e -> previousConnectionsById.containsKey(e.getKey()))
      .filter(e -> !previousConnectionsById.get(e.getKey()).equals(e.getValue()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    var addedConnections = newConnectionsById.entrySet().stream()
      .filter(e -> !previousConnectionsById.containsKey(e.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    var removedConnectionIds = new HashSet<>(previousConnectionsById.keySet());
    removedConnectionIds.removeAll(newConnectionsById.keySet());

    updatedConnections.values().forEach(this::updateConnection);
    addedConnections.values().forEach(this::addConnection);
    removedConnectionIds.forEach(this::removeConnection);
  }

  public void didChangeCredentials(String connectionId) {
    applicationEventPublisher.publishEvent(new ConnectionCredentialsChangedEvent(connectionId));
  }

  private void addConnection(AbstractConnectionConfiguration connectionConfiguration) {
    repository.addOrReplace(connectionConfiguration);
    applicationEventPublisher.publishEvent(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId()));
  }

  private void removeConnection(String removedConnectionId) {
    var removed = repository.remove(removedConnectionId);
    if (removed == null) {
      LOG.debug("Attempt to remove connection '{}' that was not registered. Possibly a race condition?", removedConnectionId);
    } else {
      applicationEventPublisher.publishEvent(new ConnectionConfigurationRemovedEvent(removedConnectionId));
    }
  }

  private void updateConnection(AbstractConnectionConfiguration connectionConfiguration) {
    var connectionId = connectionConfiguration.getConnectionId();
    var previous = repository.addOrReplace(connectionConfiguration);
    if (previous == null) {
      LOG.debug("Attempt to update connection '{}' that was not registered. Possibly a race condition?", connectionId);
      applicationEventPublisher.publishEvent(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId()));
    } else {
      applicationEventPublisher.publishEvent(new ConnectionConfigurationUpdatedEvent(connectionConfiguration.getConnectionId()));
    }
  }

  public ValidateConnectionResponse validateConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection,
    SonarLintCancelMonitor cancelMonitor) {
    var serverApi = serverApiProvider.getForTransientConnection(transientConnection);
    var serverChecker = new ServerVersionAndStatusChecker(serverApi);
    try {
      serverChecker.checkVersionAndStatus(cancelMonitor);
      var validateCredentials = serverApi.authentication().validate(cancelMonitor);
      if (validateCredentials.success() && transientConnection.isRight()) {
        var organizationKey = transientConnection.getRight().getOrganization();
        var organization = serverApi.organization().getOrganization(organizationKey, cancelMonitor);
        if (organization.isEmpty()) {
          return new ValidateConnectionResponse(false, "No organizations found for key: " + organizationKey);
        }
      }
      return new ValidateConnectionResponse(validateCredentials.success(), validateCredentials.message());
    } catch (Exception e) {
      return new ValidateConnectionResponse(false, e.getMessage());
    }
  }

  public boolean checkSmartNotificationsSupported(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection,
    SonarLintCancelMonitor cancelMonitor) {
    var serverApi = serverApiProvider.getForTransientConnection(transientConnection);
    var developersApi = serverApi.developers();
    return developersApi.isSupported(cancelMonitor);
  }

  public HelpGenerateUserTokenResponse helpGenerateUserToken(String serverUrl, boolean isSonarCloud, SonarLintCancelMonitor cancelMonitor) {
    return tokenGeneratorHelper.helpGenerateUserToken(serverUrl, isSonarCloud, cancelMonitor);
  }

  public List<SonarProjectDto> getAllProjects(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection, SonarLintCancelMonitor cancelMonitor) {
    var serverApi = serverApiProvider.getForTransientConnection(transientConnection);
    return serverApi.component().getAllProjects(cancelMonitor)
      .stream().map(serverProject -> new SonarProjectDto(serverProject.getKey(), serverProject.getName()))
      .collect(Collectors.toList());
  }

}
