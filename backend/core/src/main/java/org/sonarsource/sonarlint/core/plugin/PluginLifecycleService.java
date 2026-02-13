/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import org.sonarsource.sonarlint.core.active.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;

/**
 * Coordinates plugin lifecycle operations and cache eviction.
 * This service sits at a higher architectural level than PluginsService, RulesRepository,
 * and ActiveRulesService, orchestrating operations across these services without creating
 * circular dependencies.
 */
public class PluginLifecycleService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final PluginsService pluginsService;
  private final RulesRepository rulesRepository;
  private final ActiveRulesService activeRulesService;

  public PluginLifecycleService(PluginsService pluginsService, RulesRepository rulesRepository, ActiveRulesService activeRulesService) {
    this.pluginsService = pluginsService;
    this.rulesRepository = rulesRepository;
    this.activeRulesService = activeRulesService;
  }

  public LoadedPlugins reloadPluginsAndEvictCaches(String connectionId) {
    LOG.debug("Reloading plugins and evicting all related caches for connection '{}'", connectionId);

    unloadPluginsAndEvictCaches(connectionId);
    return pluginsService.getPlugins(connectionId);
  }

  public void unloadPluginsAndEvictCaches(String connectionId) {
    LOG.debug("Unloading plugins and evicting all related caches for connection '{}'", connectionId);

    pluginsService.unloadPlugins(connectionId);
    rulesRepository.evictFor(connectionId);
    activeRulesService.evictFor(connectionId);
  }
}
