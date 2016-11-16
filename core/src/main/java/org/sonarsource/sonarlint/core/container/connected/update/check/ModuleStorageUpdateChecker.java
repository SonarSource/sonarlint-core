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
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.PropertiesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ModuleStorageUpdateChecker {

  private final StorageManager storageManager;
  private final ModuleConfigurationDownloader moduleConfigurationDownloader;
  private final PropertiesDownloader globalPropertiesDownloader;

  public ModuleStorageUpdateChecker(StorageManager storageManager, ModuleConfigurationDownloader moduleConfigurationDownloader,
    PropertiesDownloader globalPropertiesDownloader) {
    this.storageManager = storageManager;
    this.moduleConfigurationDownloader = moduleConfigurationDownloader;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
  }

  public StorageUpdateCheckResult checkForUpdates(String moduleKey, ProgressWrapper progress) {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    GlobalProperties globalProps = globalPropertiesDownloader.fetchGlobalProperties();

    ModuleConfiguration serverModuleConfiguration = moduleConfigurationDownloader.fetchModuleConfiguration(moduleKey, globalProps);
    ModuleConfiguration storageModuleConfiguration = storageManager.readModuleConfigFromStorage(moduleKey);

    MapDifference<String, String> propDiff = Maps.difference(storageModuleConfiguration.getPropertiesMap(), serverModuleConfiguration.getPropertiesMap());
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

    MapDifference<String, String> qProfileDiff = Maps.difference(storageModuleConfiguration.getQprofilePerLanguageMap(), serverModuleConfiguration.getQprofilePerLanguageMap());
    if (!qProfileDiff.areEqual()) {
      for (Map.Entry<String, String> entry : qProfileDiff.entriesOnlyOnLeft().entrySet()) {
        result.appendToChangelog(String.format("Quality profile for language '%s' removed", entry.getKey()));
      }
      for (Map.Entry<String, String> entry : qProfileDiff.entriesOnlyOnRight().entrySet()) {
        result.appendToChangelog("Quality profile for language '" + entry.getKey() + "' added with value '" + entry.getValue() + "'");
      }
      for (Map.Entry<String, ValueDifference<String>> entry : qProfileDiff.entriesDiffering().entrySet()) {
        result.appendToChangelog(
          "Quality profile for language '" + entry.getKey() + "' changed from '" + entry.getValue().leftValue() + "' to '" + entry.getValue().rightValue() + "'");
      }
    }

    return result;
  }

}
