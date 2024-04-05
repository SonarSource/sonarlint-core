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
package org.sonarsource.sonarlint.core;

import com.google.common.eventbus.EventBus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;

@Named
@Singleton
public class ConfigurationServiceImpl implements ConfigurationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final EventBus clientEventBus;
  private final ConfigurationRepository repository;

  public ConfigurationServiceImpl(EventBus clientEventBus, ConfigurationRepository repository) {
    this.clientEventBus = clientEventBus;
    this.repository = repository;
  }

  @Override
  public void didAddConfigurationScopes(DidAddConfigurationScopesParams params) {
    var addedDtos = params.getAddedScopes();
    Set<String> addedIds = new HashSet<>();
    for (var addedDto : addedDtos) {
      var previous = addOrUpdateRepository(addedDto);
      if (previous != null) {
        LOG.error("Duplicate configuration scope registered: {}", addedDto.getId());
      } else {
        addedIds.add(addedDto.getId());
      }
    }
    clientEventBus.post(new ConfigurationScopesAddedEvent(addedIds));
  }

  private ConfigurationScope addOrUpdateRepository(ConfigurationScopeDto dto) {
    var configScopeInReferential = adapt(dto);
    var bindingDto = dto.getBinding();
    var bindingConfigInReferential = adapt(bindingDto);
    return repository.addOrReplace(configScopeInReferential, bindingConfigInReferential);
  }

  @NotNull
  private static BindingConfiguration adapt(BindingConfigurationDto dto) {
    return new BindingConfiguration(dto.getConnectionId(), dto.getSonarProjectKey(), dto.isBindingSuggestionDisabled());
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
    } else {
      clientEventBus.post(new ConfigurationScopeRemovedEvent(removed.getScope(), removed.getBindingConfiguration()));
    }
  }

  @Override
  public void didUpdateBinding(DidUpdateBindingParams params) {
    var boundEvent = bind(params.getConfigScopeId(), params.getUpdatedBinding());
    if (boundEvent != null) {
      clientEventBus.post(boundEvent);
    }
  }

  @CheckForNull
  private BindingConfigChangedEvent bind(String configurationScopeId, BindingConfigurationDto bindingConfiguration) {
    var previousBindingConfig = repository.getBindingConfiguration(configurationScopeId);
    if (previousBindingConfig == null) {
      LOG.error("Attempt to update binding in configuration scope '{}' that was not registered", configurationScopeId);
      return null;
    }
    var newBindingConfig = adapt(bindingConfiguration);
    repository.updateBinding(configurationScopeId, newBindingConfig);

    return createChangedEventIfNeeded(configurationScopeId, previousBindingConfig, newBindingConfig);
  }

  @CheckForNull
  private static BindingConfigChangedEvent createChangedEventIfNeeded(String configScopeId, BindingConfiguration previousBindingConfig, BindingConfiguration newBindingConfig) {
    if (!previousBindingConfig.equals(newBindingConfig)) {
      return new BindingConfigChangedEvent(configScopeId, previousBindingConfig, newBindingConfig);
    }
    return null;
  }

  public List<ConfigurationScope> getConfigScopesWithBindingConfiguredTo(String connectionId, String projectKey) {
    return repository.getBoundScopesByConnection(connectionId, projectKey);
  }
}
