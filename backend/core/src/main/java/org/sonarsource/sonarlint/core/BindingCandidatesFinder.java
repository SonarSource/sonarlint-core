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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Named
@Singleton
public class BindingCandidatesFinder {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SPLIT_PATTERN = "[\\W_]+";
  private final ConfigurationRepository configRepository;
  private final BindingClueProvider bindingClueProvider;
  private final SonarProjectsCache sonarProjectsCache;

  @Inject
  public BindingCandidatesFinder(ConfigurationRepository configRepository, BindingClueProvider bindingClueProvider, SonarProjectsCache sonarProjectsCache) {
    this.configRepository = configRepository;
    this.bindingClueProvider = bindingClueProvider;
    this.sonarProjectsCache = sonarProjectsCache;
  }

  public Set<ConfigurationScopeSharedContext> findConfigScopesToBind(String connectionId, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    var configScopeCandidates = configRepository.getAllBindableUnboundScopes();
    if (configScopeCandidates.isEmpty()) {
      return Set.of();
    }

    var goodConfigScopeCandidates = new HashSet<ConfigurationScopeSharedContext>();

    for (var scope : configScopeCandidates) {
      checkIfScopeIsGoodCandidateForBinding(scope, connectionId, projectKey, cancelMonitor).ifPresent(goodConfigScopeCandidates::add);
    }

    // if both a parent and a child configuration scope are candidates, preference should be given to the higher scope in the hierarchy
    // we prefer to bind at the broadest possible scope
    return filterOutLeafCandidates(goodConfigScopeCandidates);
  }

  private Optional<ConfigurationScopeSharedContext> checkIfScopeIsGoodCandidateForBinding(
    ConfigurationScope scope, String connectionId, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    cancelMonitor.checkCanceled();

    var cluesAndConnections = bindingClueProvider.collectBindingCluesWithConnections(scope.getId(), Set.of(connectionId), cancelMonitor);

    var cluesWithMatchingProjectKey = cluesAndConnections.stream()
      .filter(c -> projectKey.equals(c.getBindingClue().getSonarProjectKey()))
      .collect(toList());


    if (!cluesWithMatchingProjectKey.isEmpty()) {
      var isFromSharedConfiguration = cluesWithMatchingProjectKey.stream().anyMatch(c -> c.getBindingClue().isFromSharedConfiguration());
      return Optional.of(new ConfigurationScopeSharedContext(scope, isFromSharedConfiguration));
    }
    var configScopeName = scope.getName();
    if (isNotBlank(configScopeName) && isConfigScopeNameCloseEnoughToSonarProject(configScopeName, connectionId, projectKey, cancelMonitor)) {
      return Optional.of(new ConfigurationScopeSharedContext(scope, false));
    }
    return Optional.empty();
  }

  private static Set<ConfigurationScopeSharedContext> filterOutLeafCandidates(Set<ConfigurationScopeSharedContext> candidates) {
    var candidateIds = candidates.stream().map(ConfigurationScopeSharedContext::getConfigurationScope).map(ConfigurationScope::getId).collect(Collectors.toSet());
    return candidates.stream().filter(bindableConfig -> {
      var scope = bindableConfig.getConfigurationScope();
      var parentId = scope.getParentId();
      return parentId == null || !candidateIds.contains(parentId);
    }).collect(Collectors.toSet());
  }

  private boolean isConfigScopeNameCloseEnoughToSonarProject(String configScopeName, String connectionId, String projectKey, SonarLintCancelMonitor cancelMonitor) {
    // FIXME: it looks a bit overkill to create a TextSearchIndex with just one element, apparently just to verify that the configScopeName is a good enough match for the SonarProject
    var sonarProjectOpt = sonarProjectsCache.getSonarProject(connectionId, projectKey, cancelMonitor);
    if (sonarProjectOpt.isEmpty()) {
      LOG.debug("Unable to find SonarProject with key '{}' on connection '{}' in the cache", projectKey, connectionId);
      return false;
    }
    TextSearchIndex<ServerProject> index = new TextSearchIndex<>(SPLIT_PATTERN);
    var p = sonarProjectOpt.get();
    index.index(p, p.getKey() + " " + p.getName());
    var searchResult = index.search(configScopeName);
    return !searchResult.isEmpty();
  }
}
