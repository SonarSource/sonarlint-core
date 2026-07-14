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
  private final Map<PluginContext, PluginsConfiguration> configurationsByContext = new ConcurrentHashMap<>();

  public record CacheLookup(PluginsConfiguration configuration, boolean created) {
  }

  public CacheLookup getOrLoad(PluginContext context, Supplier<PluginsConfiguration> loader) {
    var created = new AtomicBoolean();
    var configuration = configurationsByContext.computeIfAbsent(context, ignored -> {
      created.set(true);
      return loader.get();
    });
    return new CacheLookup(configuration, created.get());
  }

  void unloadAllPlugins() throws IOException {
    Queue<IOException> exceptions = new LinkedList<>();
    configurationsByContext.values().forEach(config -> tryAndCollectIOException(config.plugins()::close, exceptions));
    configurationsByContext.clear();
    throwFirstWithOtherSuppressed(exceptions);
  }

  public void evict(PluginContext context) {
    var removed = new AtomicReference<PluginsConfiguration>();
    configurationsByContext.compute(context, (ignored, current) -> {
      removed.set(current);
      return null;
    });
    var config = removed.get();
    if (config != null) {
      try {
        config.plugins().close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to unload plugins", e);
      }
    }
  }

}
