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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.grip.suggest.FixSuggestion;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiRequest;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.DiffDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.LineRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestedFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class BeforeAfterParser implements GripSuggestionParser {
  @Override
  public Either<String, FixSuggestion> parse(SuggestFixWebApiRequest request, SuggestFixWebApiResponse webApiResponse) {
    var fixResponse = parseResponse(webApiResponse);
    if (fixResponse.isLeft()) {
      return Either.forLeft(fixResponse.getLeft());
    }
    var fix = fixResponse.getRight();
    var fileContent = request.getFileContent();
    var snippetLineRange = locateSnippet(fileContent, fix.snippets.get(0));
    // if no match, we could probably search better by being even more lenient
    if (snippetLineRange == null) {
      return Either.forLeft("An error occurred: not able to locate the code to fix in the original source code");
    }
    var originalSnippet = fileContent.lines().skip(snippetLineRange.start - 1L).limit(snippetLineRange.size()).collect(Collectors.joining("\n"));
    var suggestedFix = new SuggestedFixDto(List.of(new DiffDto(originalSnippet, fix.snippets.get(1), new LineRangeDto(snippetLineRange.start, snippetLineRange.end))),
      fix.explanation);
    return Either.forRight(new FixSuggestion(webApiResponse, request.getRuleKey(), suggestedFix, fix.snippets.get(0), fix.snippets.get(1)));
  }

  /**
   * The implementation is lenient and accepts differences with the case or spacing.
   * With the default temperature (which seems to be high (1?)), I observed frequent discrepancies between the original code and the diff returned by the API:
   * <ul>
   *   <li>different case, sometimes in the middle of a word</li>
   *   <li>different spacing</li>
   *   <li>completely random words appearing in the middle of the diff. Locating snippets with such anomalies is not supported at the moment.
   *   If the need arises, we could introduce a scoring system and locate the snippet based on the highest score)</li>
   * </ul>
   * It seems much more consistent when using a lower temperature value, so we might get rid of this lenient implementation and instead trust the response from the API.
   */
  @CheckForNull
  private static LineRange locateSnippet(String originalFileContent, String snippetFromApi) {
    var fileLines = originalFileContent.lines().collect(Collectors.toCollection(ArrayList::new));
    var snippetLines = snippetFromApi.lines().collect(Collectors.toCollection(ArrayList::new));
    var snippetStartLineInFile = -1;
    var currentFileLineIndex = 1;
    while (!snippetLines.isEmpty() && !fileLines.isEmpty()) {
      var nextFileLine = fileLines.remove(0);
      var nextSnippetLine = snippetLines.remove(0);
      if (nextSnippetLine.trim().toLowerCase(Locale.getDefault()).equals(nextFileLine.trim().toLowerCase(Locale.getDefault()))) {
        if (snippetStartLineInFile == -1) {
          snippetStartLineInFile = currentFileLineIndex;
        }
      } else {
        snippetLines = snippetFromApi.lines().collect(Collectors.toCollection(ArrayList::new));
        snippetStartLineInFile = -1;
      }
      currentFileLineIndex++;
    }
    return snippetStartLineInFile == -1 ? null : new LineRange(snippetStartLineInFile, currentFileLineIndex - 1);
  }

  private static Either<String, GripResponse> parseResponse(SuggestFixWebApiResponse response) {
    var content = response.getContent();
    var lines = content.lines().collect(Collectors.toCollection(ArrayList::new));
    if (lines.isEmpty()) {
      return Either.forLeft("Unexpected response received from the suggestion service: empty content");
    }
    var snippets = new ArrayList<String>();
    var inSnippet = false;
    StringBuilder currentSnippet = null;
    var freeText = new StringBuilder();
    var currentLineSeparator = "";
    for (String line : lines) {
      if (line.startsWith("```")) {
        if (inSnippet) {
          snippets.add(currentSnippet.toString());
          inSnippet = false;
          currentSnippet = null;
          freeText = new StringBuilder();
        } else {
          inSnippet = true;
          currentSnippet = new StringBuilder();
        }
        currentLineSeparator = "";
      } else if (inSnippet) {
        currentSnippet.append(currentLineSeparator).append(line);
        currentLineSeparator = "\n";
      } else {
        if (!line.trim().equals("\n") && !line.trim().isEmpty()) {
          freeText.append(currentLineSeparator).append(line);
          currentLineSeparator = "\n";
        }
      }
    }
    if (snippets.size() < 2) {
      return Either.forLeft("Unexpected response received from the suggestion service: no before and after");
    }
    return Either.forRight(new GripResponse(snippets, freeText.toString()));
  }

  private static class GripResponse {
    private final List<String> snippets;
    private final String explanation;

    private GripResponse(List<String> snippets, String explanation) {
      this.snippets = snippets;
      this.explanation = explanation;
    }
  }

  // 1-based
  private static class LineRange {
    private final int start;
    private final int end;

    private LineRange(int start, int end) {
      this.start = start;
      this.end = end;
    }

    private int size() {
      return end - start + 1;
    }
  }
}
