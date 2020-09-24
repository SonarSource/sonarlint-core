/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.client.api.common.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginRepositoryTest {
  private PluginRepository pluginRepository;
  private PluginInfosLoader cacheLoader;
  private PluginInstancesLoader loader;

  @Before
  public void setup() {
    cacheLoader = mock(PluginInfosLoader.class);
    loader = mock(PluginInstancesLoader.class);
    pluginRepository = new PluginRepository(cacheLoader, loader);
  }

  @Test
  public void testRepo() {
    PluginInfo info = new PluginInfo("key");
    info.setVersion(Version.create("2.0"));
    test(info);
  }

  @Test
  public void testAnalyzerWithoutVersion() {
    PluginInfo info = new PluginInfo("key");
    test(info);
  }

  private void test(PluginInfo info) {
    Plugin plugin = mock(Plugin.class);
    Map<String, PluginInfo> infos = Collections.singletonMap("key", info);
    when(cacheLoader.load()).thenReturn(infos);
    when(loader.load(infos)).thenReturn(Collections.singletonMap("key", plugin));
    pluginRepository.start();

    verify(loader).load(infos);
    verify(cacheLoader).load();

    assertThat(pluginRepository.getPluginDetails()).hasSize(1);
    assertThat(pluginRepository.getPluginInfo("key")).isEqualTo(info);
    assertThat(pluginRepository.getActivePluginInfos()).containsExactly(info);
    assertThat(pluginRepository.getPluginInstance("key")).isEqualTo(plugin);
    assertThat(pluginRepository.hasPlugin("key")).isTrue();
  }

}
