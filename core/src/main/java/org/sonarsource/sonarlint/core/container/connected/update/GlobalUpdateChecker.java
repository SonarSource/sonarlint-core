/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class GlobalUpdateChecker {

  private final StorageManager storageManager;
  private final PluginReferencesDownloader pluginReferenceDownloader;
  private final GlobalPropertiesDownloader globalPropertiesDownloader;
  private final RulesDownloader rulesDownloader;
  private final ServerVersionAndStatusChecker statusChecker;
  private final PluginVersionChecker pluginsChecker;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public GlobalUpdateChecker(StorageManager storageManager, PluginVersionChecker pluginsChecker, ServerVersionAndStatusChecker statusChecker,
    PluginReferencesDownloader pluginReferenceDownloader, GlobalPropertiesDownloader globalPropertiesDownloader, RulesDownloader rulesDownloader,
    QualityProfilesDownloader qualityProfilesDownloader) {
    this.storageManager = storageManager;
    this.pluginsChecker = pluginsChecker;
    this.statusChecker = statusChecker;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
    this.rulesDownloader = rulesDownloader;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
  }

  public GlobalStorageUpdateCheckResult checkForUpdate(ProgressWrapper progress) {
    DefaultGlobalStorageUpdateCheckResult result = new DefaultGlobalStorageUpdateCheckResult();

    progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
    ServerInfos serverStatus = statusChecker.checkVersionAndStatus();
    progress.setProgressAndCheckCancel("Checking plugins versions", 0.15f);
    pluginsChecker.checkPlugins(serverStatus.getVersion());

    // Currently with don't check server version change since it is unlikely to have impact on SL

    progress.setProgressAndCheckCancel("Checking global properties", 0.2f);
    GlobalProperties serverGlobalProperties = globalPropertiesDownloader.fetchGlobalProperties();
    GlobalProperties storageGlobalProperties = storageManager.readGlobalPropertiesFromStorage();
    MapDifference<String, String> propDiff = Maps.difference(storageGlobalProperties.getPropertiesMap(), serverGlobalProperties.getPropertiesMap());
    if (!propDiff.areEqual()) {
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnLeft().entrySet()) {
        result.appendToChangelog(String.format("Property '%s' removed", entry.getKey()));
      }
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnRight().entrySet()) {
        result.appendToChangelog("Property '" + entry.getKey() + "' added with value '" + entry.getValue() + "'");
      }
      for (Map.Entry<String, ValueDifference<String>> entry : propDiff.entriesDiffering().entrySet()) {
        result.appendToChangelog("Value of property '" + entry.getKey() + "' changed from '" + entry.getValue().leftValue() + "' to '" + entry.getValue().rightValue() + "'");
      }
    }

    // progress.setProgressAndCheckCancel("Fetching plugins", 0.3f);
    // pluginReferenceDownloader.fetchPluginsTo(temp, serverStatus.getVersion());
    //
    // progress.setProgressAndCheckCancel("Fetching rules", 0.4f);
    // rulesDownloader.fetchRulesTo(temp);
    //
    // progress.setProgressAndCheckCancel("Fetching quality profiles", 0.4f);
    // qualityProfilesDownloader.fetchQualityProfiles(temp);
    //
    // progress.setProgressAndCheckCancel("Fetching list of modules", 0.8f);
    // moduleListDownloader.fetchModulesList(temp);
    //
    // progress.setProgressAndCheckCancel("Finalizing...", 1.0f);

    return result;
  }
}
