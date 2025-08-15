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

import java.util.HashSet;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedWithBindingEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScopeWithBinding;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

public class ConfigurationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ApplicationEventPublisher applicationEventPublisher;
  private final ConfigurationRepository repository;

  public ConfigurationService(ApplicationEventPublisher applicationEventPublisher, ConfigurationRepository repository) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.repository = repository;
  }

  public void didAddConfigurationScopes(List<ConfigurationScopeDto> addedScopes) {
    var addedIds = new HashSet<ConfigurationScopeWithBinding>();
    for (var addedDto : addedScopes) {
      var configScopeInReferential = adapt(addedDto);
      var bindingDto = addedDto.getBinding();
      var bindingConfigInReferential = adapt(bindingDto);
      var previous = repository.addOrReplace(configScopeInReferential, bindingConfigInReferential);
      if (previous != null) {
        LOG.error("Duplicate configuration scope registered: {}", addedDto.getId());
      } else {
        LOG.debug("Added configuration scope '{}'", configScopeInReferential.id());
        addedIds.add(new ConfigurationScopeWithBinding(configScopeInReferential, bindingConfigInReferential));
      }
    }
    if (!addedIds.isEmpty()) {
      applicationEventPublisher.publishEvent(new ConfigurationScopesAddedWithBindingEvent(addedIds));
    }
  }

  private static BindingConfiguration adapt(@Nullable BindingConfigurationDto dto) {
    if (dto == null) {
      return BindingConfiguration.noBinding();
    }
    return new BindingConfiguration(dto.getConnectionId(), dto.getSonarProjectKey(), dto.isBindingSuggestionDisabled());
  }

  private static ConfigurationScope adapt(ConfigurationScopeDto dto) {
    return new ConfigurationScope(dto.getId(), dto.getParentId(), dto.isBindable(), dto.getName());
  }

  public void didRemoveConfigurationScope(String removedId) {
    var removed = repository.remove(removedId);
    if (removed == null) {
      LOG.debug("Attempt to remove configuration scope '{}' that was not registered", removedId);
    } else {
      LOG.debug("Removed configuration scope '{}'", removedId);
      applicationEventPublisher.publishEvent(new ConfigurationScopeRemovedEvent(removed.scope(), removed.bindingConfiguration()));
    }
  }

  public void didUpdateBinding(String configScopeId, BindingConfigurationDto updatedBinding,
    @Nullable BindingMode bindingMode, @Nullable BindingSuggestionOrigin origin) {
    LOG.debug("Did update binding for configuration scope '{}', new binding: '{}'", configScopeId, updatedBinding);
    var boundEvent = bind(configScopeId, updatedBinding, bindingMode, origin);
    if (boundEvent != null) {
      applicationEventPublisher.publishEvent(boundEvent);
    }
  }

  @EventListener
  public void connectionRemoved(ConnectionConfigurationRemovedEvent event) {
    var bindingConfigurationByConfigScope = repository.removeBindingForConnection(event.getRemovedConnectionId());
    bindingConfigurationByConfigScope.forEach((configScope, bindingConfiguration) ->
      applicationEventPublisher.publishEvent(new BindingConfigChangedEvent(configScope, bindingConfiguration,
        BindingConfiguration.noBinding(bindingConfiguration.bindingSuggestionDisabled()))));
  }

  @CheckForNull
  private BindingConfigChangedEvent bind(String configurationScopeId, BindingConfigurationDto bindingConfiguration,
  @Nullable BindingMode bindingMode, @Nullable BindingSuggestionOrigin origin) {
    var previousBindingConfig = repository.getBindingConfiguration(configurationScopeId);
    if (previousBindingConfig == null) {
      LOG.error("Attempt to update binding in configuration scope '{}' that was not registered", configurationScopeId);
      return null;
    }
    var newBindingConfig = adapt(bindingConfiguration);
    repository.updateBinding(configurationScopeId, newBindingConfig);

    return createChangedEventIfNeeded(configurationScopeId, previousBindingConfig, newBindingConfig, bindingMode, origin);
  }

  @CheckForNull
  private static BindingConfigChangedEvent createChangedEventIfNeeded(String configScopeId, BindingConfiguration previousBindingConfig,
    BindingConfiguration newBindingConfig, @Nullable BindingMode bindingMode, @Nullable BindingSuggestionOrigin origin) {
    if (!previousBindingConfig.equals(newBindingConfig)) {
      return new BindingConfigChangedEvent(configScopeId, previousBindingConfig, newBindingConfig, bindingMode, origin);
    }
    return null;
  }

}
