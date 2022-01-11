/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.plugin.PluginIndex;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

/**
 * List of plugins is in the local storage, plus add the embedded ones
 */
public class StoragePluginIndexProvider implements PluginIndex {

  private final PluginReferenceStore pluginReferenceStore;
  private final ConnectedGlobalConfiguration configuration;
  private final PluginCache fileCache;

  public StoragePluginIndexProvider(PluginReferenceStore pluginReferenceStore, ConnectedGlobalConfiguration configuration, PluginCache fileCache) {
    this.pluginReferenceStore = pluginReferenceStore;
    this.configuration = configuration;
    this.fileCache = fileCache;
  }

  @Override
  public List<PluginReference> references() {
    if (pluginReferenceStore.isEmpty()) {
      return Collections.emptyList();
    }
    Sonarlint.PluginReferences protoReferences = pluginReferenceStore.getAll();
    Map<String, URL> extraPluginsUrlsByKey = configuration.getExtraPluginsUrlsByKey();
    List<PluginReference> pluginsRefs = protoReferences.getReferenceList().stream()
      .map(r -> {
        if (configuration.getEmbeddedPluginUrlsByKey().containsKey(r.getKey())) {
          return fileCache.getFromCacheOrCopy(configuration.getEmbeddedPluginUrlsByKey().get(r.getKey()));
        } else {
          return new PluginReference(r.getHash(), r.getFilename(), false);
        }
      })
      .collect(Collectors.toList());

    List<PluginReference> extraPluginReferences = extraPluginsUrlsByKey.values().stream().map(fileCache::getFromCacheOrCopy).collect(Collectors.toList());
    pluginsRefs.addAll(extraPluginReferences);
    return pluginsRefs;
  }
}
