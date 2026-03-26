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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.active.rules.ActiveRulesService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginLifecycleServiceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private PluginsService pluginsService;
  private RulesRepository rulesRepository;
  private ActiveRulesService activeRulesService;
  private PluginLifecycleService underTest;

  @BeforeEach
  void setUp() {
    pluginsService = mock(PluginsService.class);
    rulesRepository = mock(RulesRepository.class);
    activeRulesService = mock(ActiveRulesService.class);
    underTest = new PluginLifecycleService(pluginsService, rulesRepository, activeRulesService);
  }

  @Test
  void unloadPluginsAndEvictCaches_delegates_to_all_three_services() {
    var connectionId = "conn1";

    underTest.unloadPluginsAndEvictCaches(connectionId);

    verify(pluginsService).unloadPlugins(connectionId);
    verify(rulesRepository).evictFor(connectionId);
    verify(activeRulesService).evictFor(connectionId);
  }

  @Test
  void reloadPluginsAndEvictCaches_unloads_then_loads_and_returns_new_plugins() {
    var connectionId = "conn1";
    var loadedPlugins = mock(LoadedPlugins.class);
    when(pluginsService.getPlugins(connectionId)).thenReturn(loadedPlugins);

    var result = underTest.reloadPluginsAndEvictCaches(connectionId);

    verify(pluginsService).unloadPlugins(connectionId);
    verify(rulesRepository).evictFor(connectionId);
    verify(activeRulesService).evictFor(connectionId);
    verify(pluginsService).getPlugins(connectionId);
    assertThat(result).isSameAs(loadedPlugins);
  }

  @Test
  void unloadEmbeddedPluginsAndEvictCaches_delegates_to_all_three_services() {
    underTest.unloadEmbeddedPluginsAndEvictCaches();

    verify(pluginsService).unloadEmbeddedPlugins();
    verify(rulesRepository).evictEmbedded();
    verify(activeRulesService).evictStandalone();
  }

  @Test
  void reloadEmbeddedPluginsAndEvictCaches_unloads_then_loads_and_returns_new_embedded_plugins() {
    var loadedPlugins = mock(LoadedPlugins.class);
    when(pluginsService.getEmbeddedPlugins()).thenReturn(loadedPlugins);

    var result = underTest.reloadEmbeddedPluginsAndEvictCaches();

    verify(pluginsService).unloadEmbeddedPlugins();
    verify(rulesRepository).evictEmbedded();
    verify(activeRulesService).evictStandalone();
    verify(pluginsService).getEmbeddedPlugins();
    assertThat(result).isSameAs(loadedPlugins);
  }

}
