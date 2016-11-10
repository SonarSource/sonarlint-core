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
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class GlobalUpdateChecker {

  private final StorageManager storageManager;
  private final PluginReferencesDownloader pluginReferenceDownloader;
  private final GlobalPropertiesDownloader globalPropertiesDownloader;
  private final ServerVersionAndStatusChecker statusChecker;
  private final PluginVersionChecker pluginsChecker;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public GlobalUpdateChecker(StorageManager storageManager, PluginVersionChecker pluginsChecker, ServerVersionAndStatusChecker statusChecker,
    PluginReferencesDownloader pluginReferenceDownloader, GlobalPropertiesDownloader globalPropertiesDownloader,
    QualityProfilesDownloader qualityProfilesDownloader) {
    this.storageManager = storageManager;
    this.pluginsChecker = pluginsChecker;
    this.statusChecker = statusChecker;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
  }

  public GlobalStorageUpdateCheckResult checkForUpdate(ProgressWrapper progress) {
    DefaultGlobalStorageUpdateCheckResult result = new DefaultGlobalStorageUpdateCheckResult();

    progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
    ServerInfos serverStatus = statusChecker.checkVersionAndStatus();
    progress.setProgressAndCheckCancel("Checking plugins versions", 0.15f);
    pluginsChecker.checkPlugins(serverStatus.getVersion());

    // Currently with don't check server version change since it is unlikely to have impact on SL
    progress.setProgressAndCheckCancel("Checking global properties", 0.4f);
    checkGlobalProperties(result);

    progress.setProgressAndCheckCancel("Checking plugins", 0.6f);
    checkPlugins(result, serverStatus);

    progress.setProgressAndCheckCancel("Checking quality profiles", 0.8f);
    checkQualityProfiles(result);

    progress.setProgressAndCheckCancel("Done", 1.0f);

    return result;
  }

  private void checkQualityProfiles(DefaultGlobalStorageUpdateCheckResult result) {
    QProfiles serverQualityProfiles = qualityProfilesDownloader.fetchQualityProfiles();
    QProfiles storageQProfiles = storageManager.readQProfilesFromStorage();
    Map<String, String> serverPluginHashes = serverQualityProfiles.getQprofilesByKeyMap().values().stream()
      .collect(Collectors.toMap(QProfile::getKey, QProfile::getRulesUpdatedAt));
    Map<String, String> storagePluginHashes = storageQProfiles.getQprofilesByKeyMap().values().stream()
      .collect(Collectors.toMap(QProfile::getKey, QProfile::getRulesUpdatedAt));
    MapDifference<String, String> pluginDiff = Maps.difference(storagePluginHashes, serverPluginHashes);
    if (!pluginDiff.areEqual()) {
      for (Map.Entry<String, String> entry : pluginDiff.entriesOnlyOnLeft().entrySet()) {
        result.appendToChangelog(String.format("Quality profile '%s' removed", entry.getKey()));
      }
      for (Map.Entry<String, String> entry : pluginDiff.entriesOnlyOnRight().entrySet()) {
        result.appendToChangelog("Quality profile '" + entry.getKey() + "' added");
      }
      for (Map.Entry<String, ValueDifference<String>> entry : pluginDiff.entriesDiffering().entrySet()) {
        result.appendToChangelog("Quality profile '" + entry.getKey() + "' updated");
      }
    }
  }

  private void checkPlugins(DefaultGlobalStorageUpdateCheckResult result, ServerInfos serverStatus) {
    PluginReferences serverPluginReferences = pluginReferenceDownloader.fetchPlugins(serverStatus.getVersion());
    PluginReferences storagePluginReferences = storageManager.readPluginReferencesFromStorage();
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

  private void checkGlobalProperties(DefaultGlobalStorageUpdateCheckResult result) {
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
  }
}
