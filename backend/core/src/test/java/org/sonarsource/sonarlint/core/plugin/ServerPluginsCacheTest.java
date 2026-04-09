/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerPluginsCacheTest {

  private SonarQubeClientManager sonarQubeClientManager;
  private ServerPluginsCache cache;

  @BeforeEach
  void setUp() {
    sonarQubeClientManager = mock(SonarQubeClientManager.class);
    cache = new ServerPluginsCache(sonarQubeClientManager);
  }

  @Test
  void should_return_cached_plugins_on_second_call() {
    var plugins = List.of(mockPlugin("java"));
    mockApiResponse("conn", plugins);

    cache.getPlugins("conn");
    cache.getPlugins("conn");

    verifyApiCalledOnce("conn");
  }

  @Test
  void should_invalidate_on_connection_removed() {
    var plugins = List.of(mockPlugin("java"));
    mockApiResponse("conn", plugins);

    cache.getPlugins("conn");
    cache.connectionRemoved(new ConnectionConfigurationRemovedEvent("conn"));
    cache.getPlugins("conn");

    verifyApiCalledTimes("conn", 2);
  }

  @Test
  void should_invalidate_on_connection_updated() {
    var plugins = List.of(mockPlugin("java"));
    mockApiResponse("conn", plugins);

    cache.getPlugins("conn");
    cache.connectionUpdated(new ConnectionConfigurationUpdatedEvent("conn"));
    cache.getPlugins("conn");

    verifyApiCalledTimes("conn", 2);
  }

  @Test
  void should_refresh_bypasses_cache() {
    var plugins = List.of(mockPlugin("java"));
    mockApiResponse("conn", plugins);

    cache.getPlugins("conn");
    cache.refreshAndGet("conn");

    verifyApiCalledTimes("conn", 2);
  }

  @Test
  void should_cache_per_connection_id() {
    mockApiResponse("conn1", List.of(mockPlugin("java")));
    mockApiResponse("conn2", List.of(mockPlugin("python")));

    var result1 = cache.getPlugins("conn1");
    var result2 = cache.getPlugins("conn2");

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1.get()).extracting(ServerPlugin::getKey).containsExactly("java");
    assertThat(result2.get()).extracting(ServerPlugin::getKey).containsExactly("python");
  }

  @Test
  void should_return_empty_when_connection_not_found() {
    when(sonarQubeClientManager.withActiveClientAndReturn(eq("unknown"), any(Function.class)))
      .thenReturn(Optional.empty());

    var result = cache.getPlugins("unknown");

    assertThat(result).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private void mockApiResponse(String connectionId, List<ServerPlugin> plugins) {
    when(sonarQubeClientManager.withActiveClientAndReturn(eq(connectionId), any(Function.class)))
      .thenAnswer(invocation -> {
        Function<Object, Object> fn = invocation.getArgument(1);
        var api = mock(org.sonarsource.sonarlint.core.serverapi.ServerApi.class);
        var pluginsApi = mock(org.sonarsource.sonarlint.core.serverapi.plugins.PluginsApi.class);
        when(api.plugins()).thenReturn(pluginsApi);
        when(pluginsApi.getInstalled(any())).thenReturn(plugins);
        return Optional.of(fn.apply(api));
      });
  }

  @SuppressWarnings("unchecked")
  private void verifyApiCalledOnce(String connectionId) {
    verify(sonarQubeClientManager, times(1)).withActiveClientAndReturn(eq(connectionId), any(Function.class));
  }

  @SuppressWarnings("unchecked")
  private void verifyApiCalledTimes(String connectionId, int times) {
    verify(sonarQubeClientManager, times(times)).withActiveClientAndReturn(eq(connectionId), any(Function.class));
  }

  private static ServerPlugin mockPlugin(String key) {
    var plugin = mock(ServerPlugin.class);
    when(plugin.getKey()).thenReturn(key);
    return plugin;
  }
}
