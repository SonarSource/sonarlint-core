/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.config.Configuration;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public abstract class AbstractPatternInitializer {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Configuration config;

  private List<IssuePattern> multicriteriaPatterns;

  protected AbstractPatternInitializer(Configuration config) {
    this.config = config;
    initPatterns();
  }

  protected Configuration getSettings() {
    return config;
  }

  public List<IssuePattern> getMulticriteriaPatterns() {
    return multicriteriaPatterns;
  }

  public boolean hasConfiguredPatterns() {
    return hasMulticriteriaPatterns();
  }

  public boolean hasMulticriteriaPatterns() {
    return !multicriteriaPatterns.isEmpty();
  }

  protected final void initPatterns() {
    // Patterns Multicriteria
    multicriteriaPatterns = new ArrayList<>();
    for (String id : config.getStringArray(getMulticriteriaConfigurationKey())) {
      var propPrefix = getMulticriteriaConfigurationKey() + "." + id + ".";
      var filePathPattern = config.get(propPrefix + "resourceKey").orElse(null);
      if (StringUtils.isBlank(filePathPattern)) {
        LOG.debug("Issue exclusions are misconfigured. File pattern is mandatory for each entry of '" + getMulticriteriaConfigurationKey() + "'");
        continue;
      }
      var ruleKeyPattern = config.get(propPrefix + "ruleKey").orElse(null);
      if (StringUtils.isBlank(ruleKeyPattern)) {
        LOG.debug("Issue exclusions are misconfigured. Rule key pattern is mandatory for each entry of '" + getMulticriteriaConfigurationKey() + "'");
        continue;
      }
      var pattern = new IssuePattern(filePathPattern, ruleKeyPattern);

      multicriteriaPatterns.add(pattern);
    }
  }

  protected abstract String getMulticriteriaConfigurationKey();
}
