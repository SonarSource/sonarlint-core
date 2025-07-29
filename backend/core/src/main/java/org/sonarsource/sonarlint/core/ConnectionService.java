/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.validation.InvalidFields;
import org.sonarsource.sonarlint.core.commons.validation.RegexpValidator;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionCredentialsChangedEvent;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode.UNAUTHORIZED;

public class ConnectionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  /**
   * Validator for strings containing small letters, numbers and "-" symbols.
   */
  private static final RegexpValidator REGEXP_VALIDATOR = new RegexpValidator("[a-z0-9\\-]+");

  private final ApplicationEventPublisher applicationEventPublisher;
  private final ConnectionConfigurationRepository repository;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final SonarQubeClientManager sonarQubeClientManager;
  private final TokenGeneratorHelper tokenGeneratorHelper;

  @Inject
  public ConnectionService(ApplicationEventPublisher applicationEventPublisher, ConnectionConfigurationRepository repository, InitializeParams params,
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment, TokenGeneratorHelper tokenGeneratorHelper, SonarQubeClientManager sonarQubeClientManager) {
    this(applicationEventPublisher, repository, params.getSonarQubeConnections(), params.getSonarCloudConnections(), sonarCloudActiveEnvironment, sonarQubeClientManager,
      tokenGeneratorHelper);
  }

  ConnectionService(ApplicationEventPublisher applicationEventPublisher, ConnectionConfigurationRepository repository,
    @Nullable List<SonarQubeConnectionConfigurationDto> initSonarQubeConnections, @Nullable List<SonarCloudConnectionConfigurationDto> initSonarCloudConnections,
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment, SonarQubeClientManager sonarQubeClientManager, TokenGeneratorHelper tokenGeneratorHelper) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.repository = repository;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
    this.sonarQubeClientManager = sonarQubeClientManager;
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

  private SonarCloudConnectionConfiguration adapt(SonarCloudConnectionConfigurationDto scDto) {
    var region = SonarCloudRegion.valueOf(scDto.getRegion().toString());
    return new SonarCloudConnectionConfiguration(sonarCloudActiveEnvironment.getUri(region), sonarCloudActiveEnvironment.getApiUri(region), scDto.getConnectionId(),
      scDto.getOrganization(), region, scDto.isDisableNotifications());
  }

  private static void putAndLogIfDuplicateId(Map<String, AbstractConnectionConfiguration> map, AbstractConnectionConfiguration config) {
    if (map.put(config.getConnectionId(), config) != null) {
      LOG.error("Duplicate connection registered: {}", config.getConnectionId());
    }
  }

  public void didUpdateConnections(List<SonarQubeConnectionConfigurationDto> sonarQubeConnections,
    List<SonarCloudConnectionConfigurationDto> sonarCloudConnections) {
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
    LOG.debug("Connection '{}' added", connectionConfiguration.getConnectionId());
    applicationEventPublisher.publishEvent(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId(), connectionConfiguration.getKind()));
  }

  private void removeConnection(String removedConnectionId) {
    var removed = repository.remove(removedConnectionId);
    if (removed == null) {
      LOG.debug("Attempt to remove connection '{}' that was not registered. Possibly a race condition?", removedConnectionId);
    } else {
      LOG.debug("Connection '{}' removed", removedConnectionId);
      applicationEventPublisher.publishEvent(new ConnectionConfigurationRemovedEvent(removedConnectionId));
    }
  }

  private void updateConnection(AbstractConnectionConfiguration connectionConfiguration) {
    var connectionId = connectionConfiguration.getConnectionId();
    var previous = repository.addOrReplace(connectionConfiguration);
    if (previous == null) {
      LOG.debug("Attempt to update connection '{}' that was not registered. Possibly a race condition?", connectionId);
      applicationEventPublisher.publishEvent(new ConnectionConfigurationAddedEvent(connectionConfiguration.getConnectionId(), connectionConfiguration.getKind()));
    } else {
      LOG.debug("Connection '{}' updated", previous.getConnectionId());
      applicationEventPublisher.publishEvent(new ConnectionConfigurationUpdatedEvent(connectionConfiguration.getConnectionId()));
    }
  }

  public ValidateConnectionResponse validateConnection(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection,
    SonarLintCancelMonitor cancelMonitor) {
    try {
      var serverApi = sonarQubeClientManager.getForTransientConnection(transientConnection);
      var serverChecker = new ServerVersionAndStatusChecker(serverApi);
      serverChecker.checkVersionAndStatus(cancelMonitor);
      var validateCredentials = serverApi.authentication().validate(cancelMonitor);
      if (validateCredentials.success() && transientConnection.isRight()) {
        var organizationKey = transientConnection.getRight().getOrganization();
        if (organizationKey != null) {
          var organization = serverApi.organization().searchOrganization(organizationKey, cancelMonitor);
          if (organization.isEmpty()) {
            return new ValidateConnectionResponse(false, "No organizations found for key: " + organizationKey);
          }
        }
      }
      return new ValidateConnectionResponse(validateCredentials.success(), validateCredentials.message());
    } catch (Exception e) {
      return new ValidateConnectionResponse(false, e.getMessage());
    }
  }

  public HelpGenerateUserTokenResponse helpGenerateUserToken(String serverUrl, @Nullable HelpGenerateUserTokenParams.Utm utm, SonarLintCancelMonitor cancelMonitor) {
    if (utm != null) {
      var invalidFields = validateUtm(utm);
      if (invalidFields.hasInvalidFields()) {
        throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams,
          "UTM parameters should match regular expression: [a-z0-9\\-]+",
          invalidFields.getNames()));
      }
    }

    return tokenGeneratorHelper.helpGenerateUserToken(serverUrl, utm, cancelMonitor);
  }

  private static InvalidFields validateUtm(HelpGenerateUserTokenParams.Utm utm) {
    return REGEXP_VALIDATOR.validateAll(Map.of(
      "utm_medium", utm.getMedium(),
      "utm_source", utm.getSource(),
      "utm_content", utm.getContent(),
      "utm_term", utm.getTerm()));
  }

  public List<SonarProjectDto> getAllProjects(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection, SonarLintCancelMonitor cancelMonitor) {
    var serverApi = sonarQubeClientManager.getForTransientConnection(transientConnection);

    try {
      return serverApi.component().getAllProjects(cancelMonitor)
        .stream().map(serverProject -> new SonarProjectDto(serverProject.key(), serverProject.name()))
        .toList();
    } catch (UnauthorizedException e) {
      throw new ResponseErrorException(new ResponseError(UNAUTHORIZED, "The authorization has failed. Please check your credentials.", null));
    }
  }

  public Map<String, String> getProjectNamesByKey(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection,
    List<String> projectKeys, SonarLintCancelMonitor cancelMonitor) {
    var serverApi = sonarQubeClientManager.getForTransientConnection(transientConnection);
    var projectNamesByKey = new HashMap<String, String>();
    projectKeys.forEach(key -> {
      var projectName = serverApi.component().getProject(key, cancelMonitor).map(ServerProject::name).orElse(null);
      projectNamesByKey.put(key, projectName);
    });
    return projectNamesByKey;
  }
}
