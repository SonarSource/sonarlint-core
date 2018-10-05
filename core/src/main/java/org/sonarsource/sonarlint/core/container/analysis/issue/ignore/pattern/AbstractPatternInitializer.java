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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore.pattern;

import java.util.ArrayList;
import java.util.List;
import org.sonar.api.config.Configuration;
import org.sonarsource.sonarlint.core.container.analysis.ServerConfigurationProvider;

import static org.apache.commons.lang.StringUtils.defaultIfBlank;

public abstract class AbstractPatternInitializer {

  private Configuration serverConfig;

  private List<IssuePattern> multicriteriaPatterns;

  protected AbstractPatternInitializer(ServerConfigurationProvider serverConfigProvider) {
    this.serverConfig = serverConfigProvider.getServerConfig();
    initPatterns();
  }

  public List<IssuePattern> getMulticriteriaPatterns() {
    return multicriteriaPatterns;
  }

  protected final void initPatterns() {
    // Patterns Multicriteria
    multicriteriaPatterns = new ArrayList<>();
    for (String id : serverConfig.getStringArray(getMulticriteriaConfigurationKey())) {
      String propPrefix = getMulticriteriaConfigurationKey() + "." + id + ".";
      String resourceKeyPattern = serverConfig.get(propPrefix + "resourceKey").orElse(null);
      String ruleKeyPattern = serverConfig.get(propPrefix + "ruleKey").orElse(null);
      IssuePattern pattern = new IssuePattern(defaultIfBlank(resourceKeyPattern, "*"), defaultIfBlank(ruleKeyPattern, "*"));
      multicriteriaPatterns.add(pattern);
    }
  }

  protected abstract String getMulticriteriaConfigurationKey();
}
