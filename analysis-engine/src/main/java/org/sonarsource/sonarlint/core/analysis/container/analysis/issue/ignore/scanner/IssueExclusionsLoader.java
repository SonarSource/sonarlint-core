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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.scanner;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata.CharHandler;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.IgnoreIssuesFilter;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.BlockIssuePattern;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueExclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssuePattern;

public class IssueExclusionsLoader {

  private final List<java.util.regex.Pattern> allFilePatterns;
  private final List<DoubleRegexpMatcher> blockMatchers;
  private final IgnoreIssuesFilter ignoreIssuesFilter;
  private final IssueExclusionPatternInitializer patternsInitializer;
  private final boolean enableCharHandler;

  public IssueExclusionsLoader(IssueExclusionPatternInitializer patternsInitializer, IgnoreIssuesFilter ignoreIssuesFilter) {
    this.patternsInitializer = patternsInitializer;
    this.ignoreIssuesFilter = ignoreIssuesFilter;
    this.allFilePatterns = new ArrayList<>();
    this.blockMatchers = new ArrayList<>();

    for (String pattern : patternsInitializer.getAllFilePatterns()) {
      allFilePatterns.add(java.util.regex.Pattern.compile(pattern));
    }
    for (BlockIssuePattern pattern : patternsInitializer.getBlockPatterns()) {
      blockMatchers.add(new DoubleRegexpMatcher(
        java.util.regex.Pattern.compile(pattern.getBeginBlockRegexp()),
        java.util.regex.Pattern.compile(pattern.getEndBlockRegexp())));
    }
    enableCharHandler = !allFilePatterns.isEmpty() || !blockMatchers.isEmpty();
  }

  public void addMulticriteriaPatterns(SonarLintInputFile inputFile) {
    for (IssuePattern pattern : patternsInitializer.getMulticriteriaPatterns()) {
      if (pattern.matchFile(inputFile.relativePath())) {
        ignoreIssuesFilter.addRuleExclusionPatternForComponent(inputFile, pattern.getRulePattern());
      }
    }
  }

  @CheckForNull
  public CharHandler createCharHandlerFor(SonarLintInputFile inputFile) {
    if (enableCharHandler) {
      return new IssueExclusionsRegexpScanner(inputFile, allFilePatterns, blockMatchers);
    }
    return null;
  }

  public static class DoubleRegexpMatcher {

    private final java.util.regex.Pattern firstPattern;
    private final java.util.regex.Pattern secondPattern;

    DoubleRegexpMatcher(java.util.regex.Pattern firstPattern, java.util.regex.Pattern secondPattern) {
      this.firstPattern = firstPattern;
      this.secondPattern = secondPattern;
    }

    boolean matchesFirstPattern(String line) {
      return firstPattern.matcher(line).find();
    }

    boolean matchesSecondPattern(String line) {
      return hasSecondPattern() && secondPattern.matcher(line).find();
    }

    boolean hasSecondPattern() {
      return StringUtils.isNotEmpty(secondPattern.toString());
    }
  }

  @Override
  public String toString() {
    return "Issues Exclusions - Source Scanner";
  }
}
