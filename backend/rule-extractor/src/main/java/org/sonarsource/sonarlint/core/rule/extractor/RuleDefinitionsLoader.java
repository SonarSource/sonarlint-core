/*
 * SonarLint Core - Rule Extractor
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Load rules directly from plugins {@link RulesDefinition}
 */
public class RuleDefinitionsLoader {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final RulesDefinition.Context context;

  public RuleDefinitionsLoader(Optional<List<RulesDefinition>> pluginDefs) {
    context = new RulesDefinition.Context();
    for (var pluginDefinition : pluginDefs.orElse(List.of())) {
      // Capture state before
      var reposBefore = context.repositories().stream()
        .collect(Collectors.toMap(RulesDefinition.Repository::key, r -> r.rules().size()));
      try {
        pluginDefinition.define(context);
        // Capture state after
        var reposAfter = context.repositories().stream()
          .collect(Collectors.toMap(RulesDefinition.Repository::key, r -> r.rules().size()));
        
        // Find new repos
        var newRepos = reposAfter.keySet().stream()
          .filter(r -> !reposBefore.containsKey(r))
          .collect(Collectors.toList());
        
        // Find updated repos (rule count changed)
        var updatedRepos = reposAfter.entrySet().stream()
          .filter(e -> reposBefore.containsKey(e.getKey()) && !reposBefore.get(e.getKey()).equals(e.getValue()))
          .map(e -> e.getKey() + " (" + reposBefore.get(e.getKey()) + " -> " + e.getValue() + " rules)")
          .collect(Collectors.toList());
        
        if (!newRepos.isEmpty() || !updatedRepos.isEmpty()) {
          var newRulesCount = newRepos.stream()
            .mapToInt(repoKey -> context.repository(repoKey).rules().size())
            .sum();
          LOG.debug("Plugin '{}': new repos {} with {} rules, updated repos {}",
            pluginDefinition.getClass().getSimpleName(), newRepos, newRulesCount, updatedRepos);
        } else {
          LOG.debug("Plugin '{}' did not register or update any rule repositories", pluginDefinition.getClass().getSimpleName());
        }
      } catch (Exception e) {
        LOG.warn(String.format("Failed to load rule definitions for %s, associated rules will be skipped", pluginDefinition), e);
      }
    }
  }

  public RulesDefinition.Context getContext() {
    return context;
  }

}
