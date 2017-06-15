/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;

public class PluginsUpdateChecker {

  private final StorageReader storageReader;
  private final PluginReferencesDownloader pluginReferenceDownloader;

  public PluginsUpdateChecker(StorageReader storageReader, PluginReferencesDownloader pluginReferenceDownloader) {
    this.storageReader = storageReader;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
  }

  public void checkForUpdates(DefaultStorageUpdateCheckResult result, List<SonarAnalyzer> pluginList) {
    PluginReferences serverPluginReferences = pluginReferenceDownloader.fetchPlugins(pluginList);
    PluginReferences storagePluginReferences = storageReader.readPluginReferences();
    Map<String, String> serverPluginHashes = serverPluginReferences.getReferenceList().stream().collect(Collectors.toMap(PluginReference::getKey, PluginReference::getHash));
    Map<String, String> storagePluginHashes = storagePluginReferences.getReferenceList().stream().collect(Collectors.toMap(PluginReference::getKey, PluginReference::getHash));
    MapDifference<String, String> pluginDiff = Maps.difference(storagePluginHashes, serverPluginHashes);
    if (!pluginDiff.areEqual()) {
      for (Map.Entry<String, String> entry : pluginDiff.entriesOnlyOnLeft().entrySet()) {
        result.appendToChangelog(String.format("Plugin '%s' removed", entry.getKey()));
      }
      for (Map.Entry<String, String> entry : pluginDiff.entriesOnlyOnRight().entrySet()) {
        result.appendToChangelog("Plugin '" + entry.getKey() + "' added");
      }
      for (Map.Entry<String, ValueDifference<String>> entry : pluginDiff.entriesDiffering().entrySet()) {
        result.appendToChangelog("Plugin '" + entry.getKey() + "' updated");
      }
    }
  }

}
