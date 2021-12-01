/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfileApi;

public class QualityProfilesUpdateChecker {
  private final QualityProfileApi qualityProfileApi;

  public QualityProfilesUpdateChecker(ServerApiHelper serverApiHelper) {
    this.qualityProfileApi = new ServerApi(serverApiHelper).qualityProfile();
  }

  public void checkForUpdates(QualityProfileStore qualityProfileStore, DefaultStorageUpdateCheckResult result) {
    List<QualityProfile> serverQualityProfiles = qualityProfileApi.getQualityProfiles();
    List<QualityProfile> storageQProfiles = qualityProfileStore.getAll();
    Map<String, QualityProfile> serverPluginHashes = serverQualityProfiles.stream().collect(Collectors.toMap(QualityProfile::getKey, Function.identity()));
    Map<String, QualityProfile> storagePluginHashes = storageQProfiles.stream().collect(Collectors.toMap(QualityProfile::getKey, Function.identity()));
    MapDifference<String, QualityProfile> pluginDiff = Maps.difference(storagePluginHashes, serverPluginHashes);
    if (!pluginDiff.areEqual()) {
      for (var entry : pluginDiff.entriesOnlyOnLeft().entrySet()) {
        result.appendToChangelog(String.format("Quality profile '%s' for language '%s' removed", entry.getValue().getName(), entry.getValue().getLanguageName()));
      }
      for (var entry : pluginDiff.entriesOnlyOnRight().entrySet()) {
        result.appendToChangelog(String.format("Quality profile '%s' for language '%s' added", entry.getValue().getName(), entry.getValue().getLanguageName()));
      }
      for (var entry : pluginDiff.entriesDiffering().entrySet()) {
        result.appendToChangelog(
          String.format("Quality profile '%s' for language '%s' updated", entry.getValue().rightValue().getName(), entry.getValue().rightValue().getLanguageName()));
      }
    }
  }

}
