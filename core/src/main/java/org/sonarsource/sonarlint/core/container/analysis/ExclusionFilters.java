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
package org.sonarsource.sonarlint.core.container.analysis;

import org.apache.commons.lang.ArrayUtils;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Configuration;
import org.sonar.api.scan.filesystem.FileExclusions;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class ExclusionFilters {

  private static final Logger LOG = Loggers.get(ExclusionFilters.class);

  private final FileExclusions exclusionSettings;

  private SonarLintPathPattern[] mainInclusions;
  private SonarLintPathPattern[] mainExclusions;
  private SonarLintPathPattern[] testInclusions;
  private SonarLintPathPattern[] testExclusions;

  public ExclusionFilters(Configuration configuration) {
    this.exclusionSettings = new FileExclusions(configuration);
  }

  public void prepare() {
    mainInclusions = prepareMainInclusions();
    mainExclusions = prepareMainExclusions();
    testInclusions = prepareTestInclusions();
    testExclusions = prepareTestExclusions();
    log("Server included sources: ", mainInclusions);
    log("Server excluded sources: ", mainExclusions);
    log("Server included tests: ", testInclusions);
    log("Server excluded tests: ", testExclusions);
  }

  private static void log(String title, SonarLintPathPattern[] patterns) {
    if (patterns.length > 0) {
      LOG.debug(title);
      for (SonarLintPathPattern pattern : patterns) {
        LOG.debug("  {}", pattern);
      }
    }
  }

  public boolean accept(String relativePath, InputFile.Type type) {
    SonarLintPathPattern[] inclusionPatterns;
    SonarLintPathPattern[] exclusionPatterns;
    if (InputFile.Type.MAIN == type) {
      inclusionPatterns = mainInclusions;
      exclusionPatterns = mainExclusions;
    } else if (InputFile.Type.TEST == type) {
      inclusionPatterns = testInclusions;
      exclusionPatterns = testExclusions;
    } else {
      throw new IllegalArgumentException("Unknown file type: " + type);
    }

    if (inclusionPatterns.length > 0) {
      boolean matchInclusion = false;
      for (SonarLintPathPattern pattern : inclusionPatterns) {
        matchInclusion |= pattern.match(relativePath);
      }
      if (!matchInclusion) {
        return false;
      }
    }
    if (exclusionPatterns.length > 0) {
      for (SonarLintPathPattern pattern : exclusionPatterns) {
        if (pattern.match(relativePath)) {
          return false;
        }
      }
    }
    return true;
  }

  SonarLintPathPattern[] prepareMainInclusions() {
    if (exclusionSettings.sourceInclusions().length > 0) {
      // User defined params
      return SonarLintPathPattern.create(exclusionSettings.sourceInclusions());
    }
    return new SonarLintPathPattern[0];
  }

  SonarLintPathPattern[] prepareTestInclusions() {
    return SonarLintPathPattern.create(computeTestInclusions());
  }

  private String[] computeTestInclusions() {
    if (exclusionSettings.testInclusions().length > 0) {
      // User defined params
      return exclusionSettings.testInclusions();
    }
    return ArrayUtils.EMPTY_STRING_ARRAY;
  }

  SonarLintPathPattern[] prepareMainExclusions() {
    String[] patterns = (String[]) ArrayUtils.addAll(
      exclusionSettings.sourceExclusions(), computeTestInclusions());
    return SonarLintPathPattern.create(patterns);
  }

  SonarLintPathPattern[] prepareTestExclusions() {
    return SonarLintPathPattern.create(exclusionSettings.testExclusions());
  }

}
