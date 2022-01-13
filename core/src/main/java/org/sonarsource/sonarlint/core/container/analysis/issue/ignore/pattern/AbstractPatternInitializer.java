/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern;

import java.util.ArrayList;
import java.util.List;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.container.analysis.ServerConfigurationProvider;
import org.sonarsource.sonarlint.core.util.StringUtils;

public abstract class AbstractPatternInitializer {

  private static final Logger LOG = Loggers.get(AbstractPatternInitializer.class);

  private Configuration serverConfig;

  private List<IssuePattern> multicriteriaPatterns;

  protected AbstractPatternInitializer(ServerConfigurationProvider serverConfigProvider) {
    this.serverConfig = serverConfigProvider.getServerConfig();
    initPatterns();
  }

  protected Configuration getSettings() {
    return serverConfig;
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
    for (String id : serverConfig.getStringArray(getMulticriteriaConfigurationKey())) {
      String propPrefix = getMulticriteriaConfigurationKey() + "." + id + ".";
      String filePathPattern = serverConfig.get(propPrefix + "resourceKey").orElse(null);
      if (StringUtils.isBlank(filePathPattern)) {
        LOG.debug("Issue exclusions are misconfigured. File pattern is mandatory for each entry of '" + getMulticriteriaConfigurationKey() + "'");
        continue;
      }
      String ruleKeyPattern = serverConfig.get(propPrefix + "ruleKey").orElse(null);
      if (StringUtils.isBlank(ruleKeyPattern)) {
        LOG.debug("Issue exclusions are misconfigured. Rule key pattern is mandatory for each entry of '" + getMulticriteriaConfigurationKey() + "'");
        continue;
      }
      var pattern = new IssuePattern(filePathPattern != null ? filePathPattern : "*", ruleKeyPattern != null ? ruleKeyPattern : "*");

      multicriteriaPatterns.add(pattern);
    }
  }

  protected abstract String getMulticriteriaConfigurationKey();
}
