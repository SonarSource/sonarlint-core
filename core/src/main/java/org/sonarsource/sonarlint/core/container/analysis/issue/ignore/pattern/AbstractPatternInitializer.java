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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.config.Settings;
import org.sonarsource.sonarlint.core.container.analysis.ServerSettingsProvider;

import static com.google.common.base.MoreObjects.firstNonNull;

public abstract class AbstractPatternInitializer {

  private Settings settings;

  private List<IssuePattern> multicriteriaPatterns;

  protected AbstractPatternInitializer(ServerSettingsProvider settingsProvider) {
    this.settings = settingsProvider.getServerSettings();
    initPatterns();
  }

  public List<IssuePattern> getMulticriteriaPatterns() {
    return multicriteriaPatterns;
  }

  @VisibleForTesting
  protected final void initPatterns() {
    // Patterns Multicriteria
    multicriteriaPatterns = Lists.newArrayList();
    String patternConf = StringUtils.defaultIfBlank(settings.getString(getMulticriteriaConfigurationKey()), "");
    for (String id : StringUtils.split(patternConf, ',')) {
      String propPrefix = getMulticriteriaConfigurationKey() + "." + id + ".";
      String resourceKeyPattern = settings.getString(propPrefix + "resourceKey");
      String ruleKeyPattern = settings.getString(propPrefix + "ruleKey");
      IssuePattern pattern = new IssuePattern(firstNonNull(resourceKeyPattern, "*"), firstNonNull(ruleKeyPattern, "*"));
      multicriteriaPatterns.add(pattern);
    }
  }

  protected abstract String getMulticriteriaConfigurationKey();
}
