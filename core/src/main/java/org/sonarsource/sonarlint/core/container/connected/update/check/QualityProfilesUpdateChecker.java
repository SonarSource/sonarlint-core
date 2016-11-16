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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

public class QualityProfilesUpdateChecker {

  private final StorageManager storageManager;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public QualityProfilesUpdateChecker(StorageManager storageManager, QualityProfilesDownloader qualityProfilesDownloader) {
    this.storageManager = storageManager;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
  }

  public void checkForUpdates(DefaultStorageUpdateCheckResult result) {
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

}
