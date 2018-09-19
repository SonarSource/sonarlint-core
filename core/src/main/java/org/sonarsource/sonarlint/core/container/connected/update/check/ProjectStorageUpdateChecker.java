/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ProjectStorageUpdateChecker {

  private static final Logger LOG = Loggers.get(ProjectStorageUpdateChecker.class);

  private final StorageReader storageReader;
  private final ProjectConfigurationDownloader projectConfigurationDownloader;
  private final SettingsDownloader settingsDownloader;

  public ProjectStorageUpdateChecker(StorageReader storageReader, ProjectConfigurationDownloader projectConfigurationDownloader, SettingsDownloader settingsDownloader) {
    this.storageReader = storageReader;
    this.projectConfigurationDownloader = projectConfigurationDownloader;
    this.settingsDownloader = settingsDownloader;
  }

  public StorageUpdateCheckResult checkForUpdates(String projectKey, ProgressWrapper progress) {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    Version serverVersion = Version.create(storageReader.readServerInfos().getVersion());
    GlobalProperties globalProps = settingsDownloader.fetchGlobalSettings(serverVersion);

    ProjectConfiguration serverProjectConfiguration = projectConfigurationDownloader
      .fetch(serverVersion, projectKey, globalProps, progress);
    ProjectConfiguration storageProjectConfiguration = storageReader.readProjectConfig(projectKey);

    checkForSettingsUpdates(result, serverProjectConfiguration, storageProjectConfiguration);

    checkForQualityProfilesUpdates(result, serverProjectConfiguration, storageProjectConfiguration);

    return result;
  }

  private static void checkForQualityProfilesUpdates(DefaultStorageUpdateCheckResult result, ProjectConfiguration serverProjectConfiguration,
    ProjectConfiguration storageProjectConfiguration) {
    MapDifference<String, String> qProfileDiff = Maps.difference(storageProjectConfiguration.getQprofilePerLanguageMap(), serverProjectConfiguration.getQprofilePerLanguageMap());
    if (!qProfileDiff.areEqual()) {
      for (Map.Entry<String, String> entry : qProfileDiff.entriesOnlyOnLeft().entrySet()) {
        LOG.debug("Quality profile for language '{}' removed", entry.getKey());
      }
      for (Map.Entry<String, String> entry : qProfileDiff.entriesOnlyOnRight().entrySet()) {
        LOG.debug("Quality profile for language '{}' added with value '{}'", entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, ValueDifference<String>> entry : qProfileDiff.entriesDiffering().entrySet()) {
        LOG.debug(
          "Quality profile for language '{}' changed from '{}' to '{}'", entry.getKey(), entry.getValue().leftValue(), entry.getValue().rightValue());
      }
      // Don't report update when QP removed since this is harmless for the analysis
      if (!qProfileDiff.entriesOnlyOnRight().isEmpty() || !qProfileDiff.entriesDiffering().isEmpty()) {
        result.appendToChangelog("Quality profiles configuration changed");
      }
    }
  }

  private static void checkForSettingsUpdates(DefaultStorageUpdateCheckResult result, ProjectConfiguration serverProjectConfiguration,
    ProjectConfiguration storageProjectConfiguration) {
    MapDifference<String, String> propDiff = Maps.difference(GlobalSettingsUpdateChecker.filter(storageProjectConfiguration.getPropertiesMap()),
      GlobalSettingsUpdateChecker.filter(serverProjectConfiguration.getPropertiesMap()));
    if (!propDiff.areEqual()) {
      result.appendToChangelog("Project settings updated");
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnLeft().entrySet()) {
        LOG.debug("Property '{}' removed", entry.getKey());
      }
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnRight().entrySet()) {
        LOG.debug("Property '{}' added with value '{}'", entry.getKey(), GlobalSettingsUpdateChecker.formatValue(entry.getKey(), entry.getValue()));
      }
      for (Map.Entry<String, ValueDifference<String>> entry : propDiff.entriesDiffering().entrySet()) {
        LOG.debug("Value of property '{}' changed from '{}' to '{}'", entry.getKey(),
          GlobalSettingsUpdateChecker.formatLeftDiff(entry.getKey(), entry.getValue().leftValue(), entry.getValue().rightValue()),
          GlobalSettingsUpdateChecker.formatRightDiff(entry.getKey(), entry.getValue().leftValue(), entry.getValue().rightValue()));
      }
    }
  }

}
