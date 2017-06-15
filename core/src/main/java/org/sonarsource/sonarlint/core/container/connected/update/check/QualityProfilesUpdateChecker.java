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

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;

public class QualityProfilesUpdateChecker {

  private final StorageReader storageReader;
  private final QualityProfilesDownloader qualityProfilesDownloader;

  public QualityProfilesUpdateChecker(StorageReader storageReader, QualityProfilesDownloader qualityProfilesDownloader) {
    this.storageReader = storageReader;
    this.qualityProfilesDownloader = qualityProfilesDownloader;
  }

  public void checkForUpdates(DefaultStorageUpdateCheckResult result) {
    QProfiles serverQualityProfiles = qualityProfilesDownloader.fetchQualityProfiles();
    QProfiles storageQProfiles = storageReader.readQProfiles();
    Map<String, QProfile> serverPluginHashes = serverQualityProfiles.getQprofilesByKeyMap();
    Map<String, QProfile> storagePluginHashes = storageQProfiles.getQprofilesByKeyMap();
    MapDifference<String, QProfile> pluginDiff = Maps.difference(storagePluginHashes, serverPluginHashes);
    if (!pluginDiff.areEqual()) {
      for (Map.Entry<String, QProfile> entry : pluginDiff.entriesOnlyOnLeft().entrySet()) {
        result.appendToChangelog(String.format("Quality profile '%s' for language '%s' removed", entry.getValue().getName(), entry.getValue().getLanguageName()));
      }
      for (Map.Entry<String, QProfile> entry : pluginDiff.entriesOnlyOnRight().entrySet()) {
        result.appendToChangelog(String.format("Quality profile '%s' for language '%s' added", entry.getValue().getName(), entry.getValue().getLanguageName()));
      }
      for (Map.Entry<String, ValueDifference<QProfile>> entry : pluginDiff.entriesDiffering().entrySet()) {
        result.appendToChangelog(
          String.format("Quality profile '%s' for language '%s' updated", entry.getValue().rightValue().getName(), entry.getValue().rightValue().getLanguageName()));
      }
    }
  }

}
