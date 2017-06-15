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
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ModuleStorageUpdateChecker {

  private static final Logger LOG = Loggers.get(ModuleStorageUpdateChecker.class);

  private final StorageReader storageReader;;
  private final ModuleConfigurationDownloader moduleConfigurationDownloader;
  private final SettingsDownloader settingsDownloader;

  public ModuleStorageUpdateChecker(StorageReader storageReader, ModuleConfigurationDownloader moduleConfigurationDownloader, SettingsDownloader settingsDownloader) {
    this.storageReader = storageReader;
    this.moduleConfigurationDownloader = moduleConfigurationDownloader;
    this.settingsDownloader = settingsDownloader;
  }

  public StorageUpdateCheckResult checkForUpdates(String moduleKey, ProgressWrapper progress) {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    String serverVersion = storageReader.readServerInfos().getVersion();
    GlobalProperties globalProps = settingsDownloader.fetchGlobalSettings(serverVersion);

    ModuleConfiguration serverModuleConfiguration = moduleConfigurationDownloader.fetchModuleConfiguration(serverVersion, moduleKey, globalProps, progress);
    ModuleConfiguration storageModuleConfiguration = storageReader.readModuleConfig(moduleKey);

    checkForSettingsUpdates(result, serverModuleConfiguration, storageModuleConfiguration);

    checkForQualityProfilesUpdates(result, serverModuleConfiguration, storageModuleConfiguration);

    return result;
  }

  private static void checkForQualityProfilesUpdates(DefaultStorageUpdateCheckResult result, ModuleConfiguration serverModuleConfiguration,
    ModuleConfiguration storageModuleConfiguration) {
    MapDifference<String, String> qProfileDiff = Maps.difference(storageModuleConfiguration.getQprofilePerLanguageMap(), serverModuleConfiguration.getQprofilePerLanguageMap());
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

  private static void checkForSettingsUpdates(DefaultStorageUpdateCheckResult result, ModuleConfiguration serverModuleConfiguration,
    ModuleConfiguration storageModuleConfiguration) {
    MapDifference<String, String> propDiff = Maps.difference(GlobalSettingsUpdateChecker.filter(storageModuleConfiguration.getPropertiesMap()),
      GlobalSettingsUpdateChecker.filter(serverModuleConfiguration.getPropertiesMap()));
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
