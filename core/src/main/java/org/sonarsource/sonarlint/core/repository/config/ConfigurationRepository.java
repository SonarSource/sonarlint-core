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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;

import static java.util.Objects.requireNonNull;

@Named
@Singleton
public class ConfigurationRepository {

  private final Map<String, ConfigurationScope> configScopePerId = new HashMap<>();
  private final Map<String, BindingConfiguration> bindingPerConfigScopeId = new HashMap<>();

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

  public List<ConfigurationScope> getBoundScopesByConnection(String connectionId, String projectKey) {
    return bindingPerConfigScopeId.entrySet().stream()
      .filter(e -> e.getValue().isBoundTo(connectionId, projectKey))
      .map(e -> configScopePerId.get(e.getKey()))
      .collect(Collectors.toList());
  }

  public Collection<BoundScope> getBoundScopesByConnection(String connectionId) {
    return bindingPerConfigScopeId.entrySet().stream()
      .filter(e -> e.getValue().isBoundToConnection(connectionId))
      .map(e -> new BoundScope(e.getKey(), connectionId, requireNonNull(e.getValue().getSonarProjectKey())))
      .collect(Collectors.toList());
  }

  public List<ConfigurationScope> getConfigScopesWithBindingConfiguredTo(String connectionId) {
    return bindingPerConfigScopeId.entrySet().stream()
      .filter(e -> e.getValue().isBoundToConnection(connectionId))
      .map(e -> configScopePerId.get(e.getKey()))
      .collect(Collectors.toList());
  }

  public List<BoundScope> getBoundScopesByProject(String projectKey) {
    return bindingPerConfigScopeId.entrySet().stream()
      .filter(e -> e.getValue().isBoundToProject(projectKey))
      .map(e -> new BoundScope(e.getKey(), requireNonNull(e.getValue().getConnectionId()), projectKey))
      .collect(Collectors.toList());
  }

  public Map<String, Map<String, Set<String>>> getScopeIdsPerProjectKeyPerConnectionId() {
    Map<String, Map<String, Set<String>>> scopeIdsPerProjectKeyPerConnectionId = new HashMap<>();
    bindingPerConfigScopeId
      .forEach((scopeId, bindingConfiguration) -> {
        if (bindingConfiguration.isBound()) {
          scopeIdsPerProjectKeyPerConnectionId.computeIfAbsent(bindingConfiguration.getConnectionId(), k -> new HashMap<>())
            .computeIfAbsent(bindingConfiguration.getSonarProjectKey(), k -> new HashSet<>())
            .add(scopeId);
        }
      });
    return scopeIdsPerProjectKeyPerConnectionId;
  }

  public Map<String, Binding> getEffectiveBindingForLeafConfigScopesById() {
    var leaves = getLeafConfigurationScopeIds();
    return bindingPerConfigScopeId.keySet().stream()
      .filter(leaves::contains)
      .map(scopeId -> Map.entry(scopeId, getEffectiveBinding(scopeId)))
      .filter(entry -> entry.getValue().isPresent())
      .map(entry -> Map.entry(entry.getKey(), entry.getValue().get()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Set<String> getLeafConfigurationScopeIds() {
    var parentConfigScopeIds = configScopePerId.values().stream().map(ConfigurationScope::getParentId).filter(Predicate.not(Objects::isNull)).collect(Collectors.toSet());
    var leaves = new HashSet<>(configScopePerId.keySet());
    leaves.removeAll(parentConfigScopeIds);
    return leaves;
  }
}
