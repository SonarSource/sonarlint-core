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
      var reposBefore = new HashSet<>(context.repositories().stream().map(RulesDefinition.Repository::key).collect(Collectors.toSet()));
      try {
        pluginDefinition.define(context);
        var reposAfter = context.repositories().stream().map(RulesDefinition.Repository::key).collect(Collectors.toSet());
        var newRepos = reposAfter.stream().filter(r -> !reposBefore.contains(r)).collect(Collectors.toList());
        if (!newRepos.isEmpty()) {
          var rulesCount = newRepos.stream()
            .mapToInt(repoKey -> context.repository(repoKey).rules().size())
            .sum();
          LOG.debug("Plugin '{}' registered {} rule repositories with {} rules total: {}",
            pluginDefinition.getClass().getSimpleName(), newRepos.size(), rulesCount, newRepos);
        } else {
          LOG.debug("Plugin '{}' did not register any rule repositories", pluginDefinition.getClass().getSimpleName());
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
