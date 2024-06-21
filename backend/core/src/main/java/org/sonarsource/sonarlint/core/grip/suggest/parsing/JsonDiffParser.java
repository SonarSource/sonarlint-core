/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.grip.suggest.parsing;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.grip.suggest.FixSuggestion;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiRequest;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.DiffDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.LineRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestedFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class JsonDiffParser implements GripSuggestionParser {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  @Override
  public Either<String, FixSuggestion> parse(SuggestFixWebApiRequest request, SuggestFixWebApiResponse webApiResponse) {
    ResponsePayload suggestFixResponsePayload;
    try {
      suggestFixResponsePayload = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().fromJson(webApiResponse.getContent(),
        ResponsePayload.class);
    } catch (Exception e) {
      LOG.error("Unexpected response received from the suggestion service: invalid JSON", e);
      return Either.forLeft("Unexpected response received from the suggestion service: invalid JSON");
    }
    List<DiffDto> diffs;
    try {
      diffs = convertDiffs(suggestFixResponsePayload.fix);
    } catch (Exception e) {
      LOG.error("Unexpected response received from the suggestion service: invalid diff", e);
      return Either.forLeft("Unexpected response received from the suggestion service: invalid diff");
    }
    var suggestedFixDto = new SuggestedFixDto(diffs, suggestFixResponsePayload.message);
    return Either.forRight(new FixSuggestion(webApiResponse, request.getRuleKey(), suggestedFixDto));
  }

  private static List<DiffDto> convertDiffs(List<FixPayload> fixes) {
    var rawDiffs = fixes.stream().map(JsonDiffParser::readDiff).collect(Collectors.toList());
    var fixedDiffs = fixDiffs(rawDiffs);
    return fixedDiffs.stream().map(JsonDiffParser::convertDiff).collect(Collectors.toList());
  }

  private static List<SnippetDiff> fixDiffs(List<RawDiff> rawDiffs) {
    return rawDiffs.stream().flatMap(rawDiff -> fixDiff(rawDiff).stream()).collect(Collectors.toList());
  }

  private static List<SnippetDiff> fixDiff(RawDiff rawDiff) {
    var newDiffs = new ArrayList<SnippetDiff>();
    var beforeLinesBlocks = groupByConsecutiveLines(rawDiff.before);
    var afterLinesBlocks = groupByConsecutiveLines(rawDiff.after);
    if (beforeLinesBlocks.size() == 1 && afterLinesBlocks.size() == 1) {
      // if all lines are consecutive, consider the snippet to be ok, and ignore lines in the after block
      var beforeBlock = beforeLinesBlocks.get(0);
      var afterBlock = afterLinesBlocks.get(0);
      return List.of(new SnippetDiff(beforeBlock.startLine, beforeBlock.lines, afterBlock.lines));
    }
    var beforeIterator = beforeLinesBlocks.iterator();
    var afterIterator = afterLinesBlocks.iterator();
    var currentBefore = beforeIterator.hasNext() ? beforeIterator.next() : null;
    var currentAfter = afterIterator.hasNext() ? afterIterator.next() : null;
    while (currentBefore != null || currentAfter != null) {
      if (currentBefore == null || (currentAfter != null && currentBefore.startLine < currentAfter.startLine)) {
        newDiffs.add(new SnippetDiff(currentAfter.startLine, List.of(), currentAfter.lines));
        currentAfter = afterIterator.hasNext() ? afterIterator.next() : null;
      } else if (currentAfter == null || (currentBefore.startLine > currentAfter.startLine)) {
        newDiffs.add(new SnippetDiff(currentBefore.startLine, currentBefore.lines, List.of()));
        currentBefore = beforeIterator.hasNext() ? beforeIterator.next() : null;
      } else {
        newDiffs.add(new SnippetDiff(currentBefore.startLine, currentBefore.lines, currentAfter.lines));
        currentBefore = beforeIterator.hasNext() ? beforeIterator.next() : null;
        currentAfter = afterIterator.hasNext() ? afterIterator.next() : null;
      }
    }
    return newDiffs;
  }

  private static class SnippetDiff {
    private final int startLine;
    private final List<String> before;
    private final List<String> after;

    private SnippetDiff(int startLine, List<String> before, List<String> after) {
      this.startLine = startLine;
      this.before = before;
      this.after = after;
    }

    private LineRangeDto lineRange() {
      return new LineRangeDto(startLine, startLine + (before.isEmpty() ? 0 : (before.size() - 1)));
    }
  }

  private static List<LinesBlock> groupByConsecutiveLines(List<DiffLine> lines) {
    int previousLine = -1;
    int startLine = -1;
    var blocksOfConsecutiveLines = new ArrayList<LinesBlock>();
    var currentBlock = new ArrayList<String>();
    for (DiffLine lineDiff : lines) {
      if (startLine == -1) {
        previousLine = startLine = lineDiff.number;
        currentBlock.add(lineDiff.content);
      } else if (previousLine != lineDiff.number - 1) {
        blocksOfConsecutiveLines.add(new LinesBlock(startLine, currentBlock));
        previousLine = startLine = lineDiff.number;
        currentBlock = new ArrayList<>();
        currentBlock.add(lineDiff.content);
      } else {
        previousLine = lineDiff.number;
        currentBlock.add(lineDiff.content);
      }
    }
    if (startLine != -1) {
      blocksOfConsecutiveLines.add(new LinesBlock(startLine, currentBlock));
    }
    return blocksOfConsecutiveLines;
  }

  private static DiffDto convertDiff(SnippetDiff diff) {
    var beforeSnippet = new StringBuilder();
    var lineSeparator = "";
    for (var line : diff.before) {
      beforeSnippet.append(lineSeparator).append(line);
      lineSeparator = "\n";
    }
    var afterSnippet = new StringBuilder();
    lineSeparator = "";
    for (var line : diff.after) {
      afterSnippet.append(lineSeparator).append(line);
      lineSeparator = "\n";
    }
    return new DiffDto(beforeSnippet.toString(), afterSnippet.toString(), diff.lineRange());
  }

  private static RawDiff readDiff(FixPayload fix) {
    return new RawDiff(convertLines(fix.before), convertLines(fix.after));
  }

  private static List<DiffLine> convertLines(@Nullable List<String> stringDiffs) {
    var lines = new ArrayList<DiffLine>();
    if (stringDiffs != null && !stringDiffs.isEmpty()) {
      for (String lineAfter : stringDiffs) {
        var fragments = lineAfter.split(":", 2);
        if (fragments.length > 1) {
          var lineNumber = Integer.parseInt(fragments[0]);
          var line = fragments[1];
          var actualLine = line.startsWith(" ") ? line.substring(1) : line;
          lines.add(new DiffLine(lineNumber, actualLine));
        }
      }
    }
    return lines;
  }

  private static class RawDiff {
    private final List<DiffLine> before;
    private final List<DiffLine> after;

    private RawDiff(List<DiffLine> before, List<DiffLine> after) {
      this.before = before;
      this.after = after;
    }
  }

  private static class LinesBlock {
    private final int startLine;
    private final List<String> lines;

    private LinesBlock(int startLine, List<String> lines) {
      this.startLine = startLine;
      this.lines = lines;
    }
  }

  private static class DiffLine {
    private final int number;
    private final String content;

    private DiffLine(int number, String content) {
      this.number = number;
      this.content = content;
    }
  }

  private static class ResponsePayload {
    private List<FixPayload> fix;
    private String message;
  }

  private static class FixPayload {
    private List<String> before;
    private List<String> after;
  }
}
