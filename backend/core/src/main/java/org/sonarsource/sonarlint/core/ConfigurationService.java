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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.springframework.context.ApplicationEventPublisher;

@Named
@Singleton
public class ConfigurationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ApplicationEventPublisher applicationEventPublisher;
  private final ConfigurationRepository repository;

  public ConfigurationService(ApplicationEventPublisher applicationEventPublisher, ConfigurationRepository repository) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.repository = repository;
  }

  public void didAddConfigurationScopes(List<ConfigurationScopeDto> addedScopes) {
    Set<String> addedIds = new HashSet<>();
    for (var addedDto : addedScopes) {
      var previous = addOrUpdateRepository(addedDto);
      if (previous != null) {
        LOG.error("Duplicate configuration scope registered: {}", addedDto.getId());
      } else {
        addedIds.add(addedDto.getId());
      }
    }
    if (!addedIds.isEmpty()) {
      applicationEventPublisher.publishEvent(new ConfigurationScopesAddedEvent(addedIds));
    }
  }

  private ConfigurationScope addOrUpdateRepository(ConfigurationScopeDto dto) {
    var configScopeInReferential = adapt(dto);
    var bindingDto = dto.getBinding();
    var bindingConfigInReferential = adapt(bindingDto);
    return repository.addOrReplace(configScopeInReferential, bindingConfigInReferential);
  }

  private static BindingConfiguration adapt(@Nullable BindingConfigurationDto dto) {
    if (dto == null) {
      return new BindingConfiguration(null, null, false);
    }
    return new BindingConfiguration(dto.getConnectionId(), dto.getSonarProjectKey(), dto.isBindingSuggestionDisabled());
  }

  private static ConfigurationScope adapt(ConfigurationScopeDto dto) {
    return new ConfigurationScope(dto.getId(), dto.getParentId(), dto.isBindable(), dto.getName());
  }

  public void didRemoveConfigurationScope(String removedId) {
    var removed = repository.remove(removedId);
    if (removed == null) {
      LOG.error("Attempt to remove configuration scope '{}' that was not registered", removedId);
    } else {
      applicationEventPublisher.publishEvent(new ConfigurationScopeRemovedEvent(removed.getScope(), removed.getBindingConfiguration()));
    }
  }

  public void didUpdateBinding(String configScopeId, BindingConfigurationDto updatedBinding) {
    var boundEvent = bind(configScopeId, updatedBinding);
    if (boundEvent != null) {
      applicationEventPublisher.publishEvent(boundEvent);
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

}
