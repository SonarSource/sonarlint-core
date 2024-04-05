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
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.config.Configuration;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class IssueExclusionPatternInitializer extends AbstractPatternInitializer {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final String EXCLUSION_KEY_PREFIX = "sonar.issue.ignore";
  public static final String BLOCK_SUFFIX = ".block";
  public static final String PATTERNS_BLOCK_KEY = EXCLUSION_KEY_PREFIX + BLOCK_SUFFIX;
  public static final String BEGIN_BLOCK_REGEXP = "beginBlockRegexp";
  public static final String END_BLOCK_REGEXP = "endBlockRegexp";
  public static final String ALLFILE_SUFFIX = ".allfile";
  public static final String PATTERNS_ALLFILE_KEY = EXCLUSION_KEY_PREFIX + ALLFILE_SUFFIX;
  public static final String FILE_REGEXP = "fileRegexp";
  private List<BlockIssuePattern> blockPatterns;
  private List<String> allFilePatterns;

  public IssueExclusionPatternInitializer(Configuration config) {
    super(config);
    loadFileContentPatterns();
  }

  @Override
  protected String getMulticriteriaConfigurationKey() {
    return EXCLUSION_KEY_PREFIX + ".multicriteria";
  }

  @Override
  public boolean hasConfiguredPatterns() {
    return hasFileContentPattern() || hasMulticriteriaPatterns();
  }

  private void loadFileContentPatterns() {
    // Patterns Block
    blockPatterns = new ArrayList<>();
    for (String id : getSettings().getStringArray(PATTERNS_BLOCK_KEY)) {
      var propPrefix = PATTERNS_BLOCK_KEY + "." + id + ".";
      var beginBlockRegexp = getSettings().get(propPrefix + BEGIN_BLOCK_REGEXP).orElse(null);
      if (StringUtils.isBlank(beginBlockRegexp)) {
        LOG.debug("Issue exclusions are misconfigured. Start block regexp is mandatory for each entry of '" + PATTERNS_BLOCK_KEY + "'");
        continue;
      }
      var endBlockRegexp = getSettings().get(propPrefix + END_BLOCK_REGEXP).orElse(null);
      // As per configuration help, missing second field means: from start regexp to EOF
      var pattern = new BlockIssuePattern(nullToEmpty(beginBlockRegexp), nullToEmpty(endBlockRegexp));
      blockPatterns.add(pattern);
    }
    blockPatterns = Collections.unmodifiableList(blockPatterns);

    // Patterns All File
    allFilePatterns = new ArrayList<>();
    for (String id : getSettings().getStringArray(PATTERNS_ALLFILE_KEY)) {
      var propPrefix = PATTERNS_ALLFILE_KEY + "." + id + ".";
      var allFileRegexp = getSettings().get(propPrefix + FILE_REGEXP).orElse(null);
      if (StringUtils.isBlank(allFileRegexp)) {
        LOG.debug("Issue exclusions are misconfigured. Remove blank entries from '" + PATTERNS_ALLFILE_KEY + "'");
        continue;
      }
      allFilePatterns.add(nullToEmpty(allFileRegexp));
    }
    allFilePatterns = Collections.unmodifiableList(allFilePatterns);
  }

  private static String nullToEmpty(@Nullable String str) {
    if (str == null) {
      return "";
    }
    return str;
  }

  public List<BlockIssuePattern> getBlockPatterns() {
    return blockPatterns;
  }

  public List<String> getAllFilePatterns() {
    return allFilePatterns;
  }

  public boolean hasFileContentPattern() {
    return !(blockPatterns.isEmpty() && allFilePatterns.isEmpty());
  }

}
