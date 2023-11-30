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
package org.sonarsource.sonarlint.core.repository.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

@Named
@Singleton
public class ConfigurationRepository {

  private final Map<String, ConfigurationScope> configScopePerId = new ConcurrentHashMap<>();
  private final Map<String, BindingConfiguration> bindingPerConfigScopeId = new ConcurrentHashMap<>();

  public ConfigurationScope addOrReplace(ConfigurationScope configScope, BindingConfiguration bindingConfig) {
    var id = configScope.getId();
    var previous = configScopePerId.put(id, configScope);
    bindingPerConfigScopeId.put(id, bindingConfig);
    return previous;
  }

  @CheckForNull
  public ConfigurationScopeWithBinding remove(String idToRemove) {
    var removedScope = configScopePerId.remove(idToRemove);
    var removeBindingConfiguration = bindingPerConfigScopeId.remove(idToRemove);
    return removedScope == null ? null : new ConfigurationScopeWithBinding(removedScope, removeBindingConfiguration);
  }

  public void updateBinding(String configScopeId, BindingConfiguration bindingConfig) {
    bindingPerConfigScopeId.put(configScopeId, bindingConfig);
  }

  public Set<String> getConfigScopeIds() {
    return Set.copyOf(configScopePerId.keySet());
  }

  @CheckForNull
  public BindingConfiguration getBindingConfiguration(String configScopeId) {
    return bindingPerConfigScopeId.get(configScopeId);
  }

  public Optional<Binding> getEffectiveBinding(String configScopeId) {
    var configScopeIdToSearchIn = requireNonNull(configScopeId, "Configuration Scope ID is mandatory");
    while (true) {
      var binding = getConfiguredBinding(configScopeIdToSearchIn);
      if (binding.isPresent()) {
        return binding;
      }
      var parentId = getParentId(configScopeIdToSearchIn);
      if (parentId.isEmpty()) {
        return Optional.empty();
      }
      configScopeIdToSearchIn = parentId.get();
    }
  }

  public Binding getEffectiveBindingOrThrow(String configScopeId) {
    return getEffectiveBinding(configScopeId).orElseThrow(() -> {
      var error = new ResponseError(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND, "No binding for config scope '" + configScopeId + "'", configScopeId);
      throw new ResponseErrorException(error);
    });
  }

  public Optional<Binding> getConfiguredBinding(String configScopeId) {
    var bindingConfiguration = bindingPerConfigScopeId.get(configScopeId);
    if (bindingConfiguration != null && bindingConfiguration.isBound()) {
      return Optional.of(new Binding(requireNonNull(bindingConfiguration.getConnectionId()),
        requireNonNull(bindingConfiguration.getSonarProjectKey())));
    }
    return Optional.empty();
  }

  private Optional<String> getParentId(String configScopeId) {
    var configurationScope = configScopePerId.get(configScopeId);
    if (configurationScope != null) {
      return Optional.ofNullable(configurationScope.getParentId());
    }
    return Optional.empty();
  }

  @CheckForNull
  public ConfigurationScope getConfigurationScope(String configScopeId) {
    return configScopePerId.get(configScopeId);
  }

  private Collection<BoundScope> getAllBoundScopes() {
    return configScopePerId.keySet()
      .stream()
      .map(scopeId -> {
        var effectiveBinding = getEffectiveBinding(scopeId);
        return effectiveBinding.map(binding -> new BoundScope(scopeId, requireNonNull(binding.getConnectionId()),
          requireNonNull(binding.getSonarProjectKey()))).orElse(null);
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @CheckForNull
  public BoundScope getBoundScope(String configScopeId) {
    var effectiveBinding = getEffectiveBinding(configScopeId);
    return effectiveBinding.map(binding -> new BoundScope(configScopeId, requireNonNull(binding.getConnectionId()),
      requireNonNull(binding.getSonarProjectKey()))).orElse(null);
  }

  public Collection<BoundScope> getBoundScopesToConnectionAndSonarProject(String connectionId, String projectKey) {
    return getBoundScopesToConnection(connectionId)
      .stream()
      .filter(b -> projectKey.equals(b.getSonarProjectKey()))
      .collect(Collectors.toList());
  }

  public Collection<BoundScope> getBoundScopesToConnection(String connectionId) {
    return getAllBoundScopes()
      .stream()
      .filter(b -> connectionId.equals(b.getConnectionId()))
      .collect(Collectors.toList());
  }

  /**
   * Return the set of Sonar Project keys used in at least one binding for the given connection.
   */
  public Set<String> getSonarProjectsUsedForConnection(String connectionId) {
    return getAllBoundScopes()
      .stream()
      .filter(b -> connectionId.equals(b.getConnectionId()))
      .map(BoundScope::getSonarProjectKey)
      .collect(toSet());
  }

  public Map<String, Map<String, Collection<BoundScope>>> getBoundScopeByConnectionAndSonarProject() {
    return getAllBoundScopes()
      .stream()
      .collect(groupingBy(BoundScope::getConnectionId, groupingBy(BoundScope::getSonarProjectKey, Collectors.toCollection(ArrayList::new))));
  }

}
