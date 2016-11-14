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
import org.sonarsource.sonarlint.core.container.connected.update.GlobalPropertiesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

public class GlobalSettingsUpdateChecker {

  private final StorageManager storageManager;
  private final GlobalPropertiesDownloader globalPropertiesDownloader;

  public GlobalSettingsUpdateChecker(StorageManager storageManager, GlobalPropertiesDownloader globalPropertiesDownloader) {
    this.storageManager = storageManager;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
  }

  public void checkForUpdates(DefaultGlobalStorageUpdateCheckResult result) {
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
