/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import com.google.common.eventbus.EventBus;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.clientapi.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.InitializeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;

public class ConfigurationServiceImpl implements ConfigurationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final EventBus clientEventBus;
  private final ConfigurationRepository repository;

  public ConfigurationServiceImpl(EventBus clientEventBus, ConfigurationRepository repository) {
    this.clientEventBus = clientEventBus;
    this.repository = repository;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    params.getConfigScopes().forEach(this::addOrUpdateRepository);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void didAddConfigurationScope(DidAddConfigurationScopeParams params) {
    var addedDto = params.getAdded();
    ConfigurationScope previous = addOrUpdateRepository(addedDto);
    if (previous != null) {
      LOG.error("Duplicate configuration scope registered: {}", addedDto.getId());
    } else {
      clientEventBus.post(new ConfigurationScopeAddedEvent(addedDto.getId()));
    }
  }

  private ConfigurationScope addOrUpdateRepository(ConfigurationScopeDto dto) {
    var configScopeInReferential = adapt(dto);
    var bindingDto = dto.getBinding();
    var bindingConfigInReferential = adapt(bindingDto);
    return repository.addOrReplace(configScopeInReferential, bindingConfigInReferential);
  }

  @NotNull
  private static BindingConfiguration adapt(BindingConfigurationDto dto) {
    return new BindingConfiguration(dto.getConnectionId(), dto.getSonarProjectKey(), dto.isAutoBindEnabled());
  }

  @NotNull
  private static ConfigurationScope adapt(ConfigurationScopeDto dto) {
    return new ConfigurationScope(dto.getId(), dto.getParentId(), dto.isBindable(), dto.getName());
  }

  @Override
  public void didRemoveConfigurationScope(DidRemoveConfigurationScopeParams params) {
    var idToRemove = params.getRemovedId();
    var removed = repository.remove(idToRemove);
    if (removed == null) {
      LOG.error("Attempt to remove configuration scope '{}' that was not registered", idToRemove);
    }
  }

  @Override
  public void didUpdateBinding(DidUpdateBindingParams params) {
    var configScopeId = params.getConfigScopeId();
    var previousBindingConfig = repository.getBindingConfiguration(configScopeId);
    if (previousBindingConfig == null) {
      LOG.error("Attempt to update binding in configuration scope '{}' that was not registered", configScopeId);
      return;
    }
    var newBindingConfig = adapt(params.getUpdatedBinding());
    repository.updateBinding(configScopeId, newBindingConfig);

    postChangedEventIfNeeded(configScopeId, previousBindingConfig, newBindingConfig);
  }

  private void postChangedEventIfNeeded(String configScopeId, BindingConfiguration previousBindingConfig, BindingConfiguration newBindingConfig) {
    var previousConfigForEvent = new BindingConfigChangedEvent.BindingConfig(configScopeId, previousBindingConfig.getConnectionId(),
      previousBindingConfig.getSonarProjectKey(), previousBindingConfig.isAutoBindEnabled());
    var newConfigForEvent = new BindingConfigChangedEvent.BindingConfig(configScopeId, newBindingConfig.getConnectionId(),
      newBindingConfig.getSonarProjectKey(), newBindingConfig.isAutoBindEnabled());

    if (!previousConfigForEvent.equals(newConfigForEvent)) {
      clientEventBus.post(new BindingConfigChangedEvent(previousConfigForEvent, newConfigForEvent));
    }
  }

}
