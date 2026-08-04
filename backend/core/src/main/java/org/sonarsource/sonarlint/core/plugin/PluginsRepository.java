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

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.throwFirstWithOtherSuppressed;
import static org.sonarsource.sonarlint.core.commons.IOExceptionUtils.tryAndCollectIOException;

public class PluginsRepository {
  private final Map<PluginContext, CachedPluginsConfiguration> configurationsByContext = new ConcurrentHashMap<>();

  public record CacheLookup(PluginsConfiguration configuration, boolean created) {
  }

  public CacheLookup getOrLoad(PluginContext context, Supplier<PluginsConfiguration> loader) {
    var created = new AtomicBoolean();
    var cachedConfiguration = configurationsByContext.computeIfAbsent(context, ignored -> {
      created.set(true);
      return new CachedPluginsConfiguration(loader.get());
    });
    return new CacheLookup(cachedConfiguration.configuration(), created.get());
  }

  public void transferOwnership(PluginContext context, PluginsConfiguration configuration) {
    configurationsByContext.compute(context, (ignored, cachedConfiguration) -> {
      if (cachedConfiguration == null || cachedConfiguration.configuration() != configuration) {
        throw new IllegalStateException("Cannot transfer ownership of a plugin configuration that is no longer cached");
      }
      cachedConfiguration.transferOwnership();
      return cachedConfiguration;
    });
  }

  void unloadAllPlugins() throws IOException {
    Queue<IOException> exceptions = new LinkedList<>();
    configurationsByContext.values().stream()
      .filter(CachedPluginsConfiguration::isOwnedByRepository)
      .forEach(config -> tryAndCollectIOException(config.configuration().plugins()::close, exceptions));
    configurationsByContext.clear();
    throwFirstWithOtherSuppressed(exceptions);
  }

  public void evict(PluginContext context) {
    var removed = new AtomicReference<CachedPluginsConfiguration>();
    configurationsByContext.compute(context, (ignored, current) -> {
      removed.set(current);
      return null;
    });
    var cachedConfiguration = removed.get();
    if (cachedConfiguration != null && cachedConfiguration.isOwnedByRepository()) {
      try {
        cachedConfiguration.configuration().plugins().close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to unload plugins", e);
      }
    }
  }

  private static class CachedPluginsConfiguration {
    private final PluginsConfiguration configuration;
    private volatile boolean ownedByRepository = true;

    private CachedPluginsConfiguration(PluginsConfiguration configuration) {
      this.configuration = configuration;
    }

    private PluginsConfiguration configuration() {
      return configuration;
    }

    private boolean isOwnedByRepository() {
      return ownedByRepository;
    }

    private void transferOwnership() {
      if (!ownedByRepository) {
        throw new IllegalStateException("Plugin configuration ownership was already transferred");
      }
      ownedByRepository = false;
    }
  }

}
