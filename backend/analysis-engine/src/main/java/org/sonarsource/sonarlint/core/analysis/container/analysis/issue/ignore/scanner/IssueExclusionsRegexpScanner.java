/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata.CharHandler;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.scanner.IssueExclusionsLoader.DoubleRegexpMatcher;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class IssueExclusionsRegexpScanner extends CharHandler {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final StringBuilder sb = new StringBuilder();
  private final List<Pattern> allFilePatterns;
  private final List<DoubleRegexpMatcher> blockMatchers;
  private final SonarLintInputFile inputFile;

  private int lineIndex = 1;
  private final List<LineExclusion> lineExclusions = new ArrayList<>();
  private LineExclusion currentLineExclusion = null;
  private int fileLength = 0;
  private DoubleRegexpMatcher currentMatcher;
  private boolean ignoreAllIssues;

  IssueExclusionsRegexpScanner(SonarLintInputFile inputFile, List<Pattern> allFilePatterns, List<DoubleRegexpMatcher> blockMatchers) {
    this.allFilePatterns = allFilePatterns;
    this.blockMatchers = blockMatchers;
    this.inputFile = inputFile;
    LOG.debug("Evaluate issue exclusions for '{}'", inputFile.relativePath());
  }

  @Override
  public void handleIgnoreEoL(char c) {
    if (ignoreAllIssues) {
      // Optimization
      return;
    }
    sb.append(c);
  }

  @Override
  public void newLine() {
    if (ignoreAllIssues) {
      // Optimization
      return;
    }
    processLine(sb.toString());
    sb.setLength(0);
    lineIndex++;
  }

  @Override
  public void eof() {
    if (ignoreAllIssues) {
      // Optimization
      return;
    }
    processLine(sb.toString());

    if (currentMatcher != null && !currentMatcher.hasSecondPattern()) {
      // this will happen when there is a start block regexp but no end block regexp
      endExclusion(lineIndex + 1);
    }

    // now create the new line-based pattern for this file if there are exclusions
    fileLength = lineIndex;
    if (!lineExclusions.isEmpty()) {
      var lineRanges = convertLineExclusionsToLineRanges();
      LOG.debug("  - Line exclusions found: {}", lineRanges.stream().map(LineRange::toString).collect(Collectors.joining(",")));
      inputFile.addIgnoreIssuesOnLineRanges(lineRanges.stream().map(r -> new int[] {r.from(), r.to()}).toList());
    }
  }

  private void processLine(String line) {
    if (line.trim().length() == 0) {
      return;
    }

    // first check the single regexp patterns that can be used to totally exclude a file
    for (Pattern pattern : allFilePatterns) {
      if (pattern.matcher(line).find()) {
        // nothing more to do on this file
        LOG.debug("  - Exclusion pattern '{}': all issues in this file will be ignored.", pattern);
        ignoreAllIssues = true;
        inputFile.setIgnoreAllIssues(true);
        return;
      }
    }

    // then check the double regexps if we're still here
    checkDoubleRegexps(line, lineIndex);
  }

  private Set<LineRange> convertLineExclusionsToLineRanges() {
    Set<LineRange> lineRanges = new HashSet<>(lineExclusions.size());
    for (LineExclusion lineExclusion : lineExclusions) {
      lineRanges.add(lineExclusion.toLineRange(fileLength));
    }
    return lineRanges;
  }

  private void checkDoubleRegexps(String line, int lineIndex) {
    if (currentMatcher == null) {
      for (DoubleRegexpMatcher matcher : blockMatchers) {
        if (matcher.matchesFirstPattern(line)) {
          startExclusion(lineIndex);
          currentMatcher = matcher;
          break;
        }
      }
    } else {
      if (currentMatcher.matchesSecondPattern(line)) {
        endExclusion(lineIndex);
        currentMatcher = null;
      }
    }
  }

  private void startExclusion(int lineIndex) {
    currentLineExclusion = new LineExclusion(lineIndex);
    lineExclusions.add(currentLineExclusion);
  }

  private void endExclusion(int lineIndex) {
    currentLineExclusion.setEnd(lineIndex);
    currentLineExclusion = null;
  }

  private static class LineExclusion {
    private final int start;
    private int end;

    LineExclusion(int start) {
      this.start = start;
      this.end = -1;
    }

    void setEnd(int end) {
      this.end = end;
    }

    public LineRange toLineRange(int fileLength) {
      return new LineRange(start, end == -1 ? fileLength : end);
    }
  }
}
