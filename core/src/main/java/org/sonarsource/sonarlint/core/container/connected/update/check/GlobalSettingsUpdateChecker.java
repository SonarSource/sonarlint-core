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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.CoreProperties;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.settings.SettingsApi;

public class GlobalSettingsUpdateChecker {

  private static final int MAX_VALUE_LENGTH = 20;

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final Set<String> WHITELIST = ImmutableSet.of(
    CoreProperties.PROJECT_INCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_TEST_INCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_EXCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_TEST_EXCLUSIONS_PROPERTY,
    CoreProperties.GLOBAL_EXCLUSIONS_PROPERTY,
    CoreProperties.GLOBAL_TEST_EXCLUSIONS_PROPERTY);

  private final SettingsApi settingsApi;

  public GlobalSettingsUpdateChecker(ServerApiHelper serverApiHelper) {
    this.settingsApi = new ServerApi(serverApiHelper).settings();
  }

  public void checkForUpdates(GlobalSettingsStore globalSettingsStore, DefaultStorageUpdateCheckResult result) {
    Map<String, String> serverGlobalProperties = settingsApi.getGlobalSettings();
    Map<String, String> storageGlobalProperties = globalSettingsStore.getAll();
    MapDifference<String, String> propDiff = Maps.difference(filter(storageGlobalProperties), filter(serverGlobalProperties));
    if (!propDiff.areEqual()) {
      result.appendToChangelog("Global settings updated");
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnLeft().entrySet()) {
        LOG.debug("Property '{}' removed", entry.getKey());
      }
      for (Map.Entry<String, String> entry : propDiff.entriesOnlyOnRight().entrySet()) {
        LOG.debug("Property '{}' added with value '{}'", entry.getKey(), formatValue(entry.getKey(), entry.getValue()));
      }
      for (Map.Entry<String, ValueDifference<String>> entry : propDiff.entriesDiffering().entrySet()) {
        LOG.debug("Value of property '{}' changed from '{}' to '{}'", entry.getKey(),
          formatLeftDiff(entry.getKey(), entry.getValue().leftValue(), entry.getValue().rightValue()),
          formatRightDiff(entry.getKey(), entry.getValue().leftValue(), entry.getValue().rightValue()));
      }
    }
  }

  static String formatValue(String key, String value) {
    if (key.endsWith(".secured")) {
      return "******";
    }
    return StringUtils.abbreviate(value, MAX_VALUE_LENGTH);
  }

  static String formatRightDiff(String key, String left, String right) {
    if (right.length() <= MAX_VALUE_LENGTH) {
      return formatValue(key, right);
    }
    String diff = StringUtils.difference(left, right);
    if (right.startsWith(diff)) {
      return formatValue(key, diff);
    } else {
      return formatValue(key, "..." + diff);
    }
  }

  static String formatLeftDiff(String key, String left, String right) {
    if (left.length() <= MAX_VALUE_LENGTH) {
      return formatValue(key, left);
    }
    String diff = StringUtils.difference(right, left);
    if (left.startsWith(diff)) {
      return formatValue(key, diff);
    } else {
      return formatValue(key, "..." + diff);
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
