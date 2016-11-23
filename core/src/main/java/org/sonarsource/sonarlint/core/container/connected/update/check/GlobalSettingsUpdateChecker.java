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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.CoreProperties;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.connected.update.PropertiesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

public class GlobalSettingsUpdateChecker {

  private static final Logger LOG = Loggers.get(GlobalSettingsUpdateChecker.class);

  private static final Set<String> WHITELIST = ImmutableSet.of(
    CoreProperties.PROJECT_INCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_TEST_INCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_EXCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_TEST_EXCLUSIONS_PROPERTY,
    CoreProperties.GLOBAL_EXCLUSIONS_PROPERTY,
    CoreProperties.GLOBAL_TEST_EXCLUSIONS_PROPERTY);

  private final StorageManager storageManager;
  private final PropertiesDownloader globalPropertiesDownloader;

  public GlobalSettingsUpdateChecker(StorageManager storageManager, PropertiesDownloader globalPropertiesDownloader) {
    this.storageManager = storageManager;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
  }

  public void checkForUpdates(DefaultStorageUpdateCheckResult result) {
    GlobalProperties serverGlobalProperties = globalPropertiesDownloader.fetchGlobalProperties();
    GlobalProperties storageGlobalProperties = storageManager.readGlobalPropertiesFromStorage();
    MapDifference<String, String> propDiff = Maps.difference(filter(storageGlobalProperties.getPropertiesMap()), filter(serverGlobalProperties.getPropertiesMap()));
    if (!propDiff.areEqual()) {
      result.appendToChangelog("Global settings updated");
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnLeft().entrySet()) {
        LOG.debug(String.format("Property '%s' removed", entry.getKey()));
      }
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnRight().entrySet()) {
        LOG.debug("Property '" + entry.getKey() + "' added with value '" + entry.getValue() + "'");
      }
      for (Map.Entry<String, ValueDifference<String>> entry : propDiff.entriesDiffering().entrySet()) {
        LOG.debug("Value of property '" + entry.getKey() + "' changed from '" + entry.getValue().leftValue() + "' to '" + entry.getValue().rightValue() + "'");
      }
    }
  }

  static Map<String, String> filter(Map<String, String> propertiesMap) {
    return propertiesMap.entrySet().stream()
      .filter(entry -> WHITELIST.contains(entry.getKey())
        || entry.getKey().startsWith(IssueExclusionPatternInitializer.EXCLUSION_KEY_PREFIX)
        || entry.getKey().startsWith(IssueInclusionPatternInitializer.INCLUSION_KEY_PREFIX)
        || entry.getKey().endsWith(".license.secured"))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
