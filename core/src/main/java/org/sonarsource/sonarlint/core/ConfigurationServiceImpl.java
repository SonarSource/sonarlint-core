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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.clientapi.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingConfiguration;
import org.sonarsource.sonarlint.core.clientapi.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.ConfigurationScope;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.InitializeParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;

public class ConfigurationServiceImpl implements ConfigurationService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Map<String, ConfigurationScope> configScopePerId = new HashMap<>();
  private final Map<String, BindingConfiguration> bindingPerConfigScopeId = new HashMap<>();
  private final EventBus clientEventBus;

  public ConfigurationServiceImpl(EventBus clientEventBus) {
    this.clientEventBus = clientEventBus;
  }

  @Override
  public CompletableFuture<Void> initialize(InitializeParams params) {
    params.getConfigScopes().forEach(config -> {
      configScopePerId.put(config.getId(), config);
      bindingPerConfigScopeId.put(config.getId(), config.getBinding());
    });
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void didAddConfigurationScope(DidAddConfigurationScopeParams params) {
    var added = params.getAdded();
    var id = added.getId();
    var previous = configScopePerId.put(id, added);
    bindingPerConfigScopeId.put(id, added.getBinding());
    if (previous != null) {
      LOG.error("Duplicate configuration scope registered: {}", id);
    } else {
      clientEventBus.post(new ConfigurationScopeAddedEvent(id));
    }
  }

  @Override
  public void didRemoveConfigurationScope(DidRemoveConfigurationScopeParams params) {
    var idToRemove = params.getRemovedId();
    var removed = configScopePerId.remove(idToRemove);
    bindingPerConfigScopeId.remove(idToRemove);
    if (removed == null) {
      LOG.error("Attempt to remove configuration scope '{}' that was not registered", idToRemove);
    }
  }

  @Override
  public void didUpdateBinding(DidUpdateBindingParams params) {
    var configScopeId = params.getConfigScopeId();
    var previousBindingConfig = bindingPerConfigScopeId.get(configScopeId);
    if (previousBindingConfig == null) {
      LOG.error("Attempt to update binding in configuration scope '{}' that was not registered", configScopeId);
      return;
    }
    var newBindingConfig = params.getUpdatedBinding();
    bindingPerConfigScopeId.put(configScopeId, newBindingConfig);

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

  public Set<String> getConfigScopeIds() {
    return Set.copyOf(configScopePerId.keySet());
  }

  public BindingConfiguration getBindingConfiguration(String configScopeId) {
    var bindingConfiguration = bindingPerConfigScopeId.get(configScopeId);
    if (bindingConfiguration == null) {
      throw new IllegalStateException("Unknown scope with id '" + configScopeId + "'");
    }
    return bindingConfiguration;
  }
}
