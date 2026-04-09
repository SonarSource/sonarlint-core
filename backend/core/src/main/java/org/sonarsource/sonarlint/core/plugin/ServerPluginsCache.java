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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.springframework.context.event.EventListener;

public class ServerPluginsCache {

  private final SonarQubeClientManager sonarQubeClientManager;

  private final Cache<String, Optional<List<ServerPlugin>>> cache = CacheBuilder.newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  public ServerPluginsCache(SonarQubeClientManager sonarQubeClientManager) {
    this.sonarQubeClientManager = sonarQubeClientManager;
  }

  public Optional<List<ServerPlugin>> getPlugins(String connectionId) {
    try {
      return cache.get(connectionId, () -> fetch(connectionId));
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

  public Optional<List<ServerPlugin>> refreshAndGet(String connectionId) {
    cache.invalidate(connectionId);
    return getPlugins(connectionId);
  }

  private Optional<List<ServerPlugin>> fetch(String connectionId) {
    return sonarQubeClientManager.withActiveClientAndReturn(connectionId,
      api -> api.plugins().getInstalled(new SonarLintCancelMonitor()));
  }

  @EventListener
  public void connectionRemoved(ConnectionConfigurationRemovedEvent event) {
    cache.invalidate(event.removedConnectionId());
  }

  @EventListener
  public void connectionUpdated(ConnectionConfigurationUpdatedEvent event) {
    cache.invalidate(event.updatedConnectionId());
  }
}
