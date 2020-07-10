/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.container.analysis.ServerConfigurationProvider;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class IssueExclusionPatternInitializer extends AbstractPatternInitializer {

  private static final Logger LOG = Loggers.get(IssueExclusionPatternInitializer.class);

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

  public IssueExclusionPatternInitializer(ServerConfigurationProvider settingsProvider) {
    super(settingsProvider);
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

  private final void loadFileContentPatterns() {
    // Patterns Block
    blockPatterns = new ArrayList<>();
    for (String id : getSettings().getStringArray(PATTERNS_BLOCK_KEY)) {
      String propPrefix = PATTERNS_BLOCK_KEY + "." + id + ".";
      String beginBlockRegexp = getSettings().get(propPrefix + BEGIN_BLOCK_REGEXP).orElse(null);
      if (StringUtils.isBlank(beginBlockRegexp)) {
        LOG.debug("Issue exclusions are misconfigured. Start block regexp is mandatory for each entry of '" + PATTERNS_BLOCK_KEY + "'");
        continue;
      }
      String endBlockRegexp = getSettings().get(propPrefix + END_BLOCK_REGEXP).orElse(null);
      // As per configuration help, missing second field means: from start regexp to EOF
      BlockIssuePattern pattern = new BlockIssuePattern(nullToEmpty(beginBlockRegexp), nullToEmpty(endBlockRegexp));
      blockPatterns.add(pattern);
    }
    blockPatterns = Collections.unmodifiableList(blockPatterns);

    // Patterns All File
    allFilePatterns = new ArrayList<>();
    for (String id : getSettings().getStringArray(PATTERNS_ALLFILE_KEY)) {
      String propPrefix = PATTERNS_ALLFILE_KEY + "." + id + ".";
      String allFileRegexp = getSettings().get(propPrefix + FILE_REGEXP).orElse(null);
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
