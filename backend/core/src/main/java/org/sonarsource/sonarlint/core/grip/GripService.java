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
package org.sonarsource.sonarlint.core.grip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.grip.web.api.GripWebApi;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.DiffDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.LineRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestedFixDto;

@Named
@Singleton
public class GripService {
  private final GripWebApi gripWebApi;
  private final ClientFileSystemService fileSystemService;

  public GripService(HttpClientProvider httpClientProvider, ClientFileSystemService fileSystemService) {
    this.gripWebApi = new GripWebApi(httpClientProvider);
    this.fileSystemService = fileSystemService;
  }

  public SuggestFixResponse suggestFix(SuggestFixParams params) {
    var fileUri = params.getFileUri();
    var clientFile = fileSystemService.getClientFiles(params.getConfigurationScopeId(), fileUri);
    if (clientFile == null) {
      throw new IllegalStateException("Cannot find the file with URI: " + fileUri);
    }
    var fileContent = clientFile.getContent();
    var response = gripWebApi.suggestFix(params, fileContent);
    var responseMessage = response.choices.get(response.choices.size() - 1).message.content;
    var diff = parseDiff(responseMessage);
    var snippetLineRange = locateSnippet(fileContent, diff.before);
    SuggestedFixDto suggestedFix = null;
    // if no match, we could probably search better
    if (snippetLineRange != null) {
      var originalSnippet = fileContent.lines().skip(snippetLineRange.start - 1L).limit(snippetLineRange.size()).collect(Collectors.joining("\n"));
      suggestedFix = new SuggestedFixDto(List.of(new DiffDto(originalSnippet, diff.after, new LineRangeDto(snippetLineRange.start, snippetLineRange.end))));
    }
    return new SuggestFixResponse(responseMessage, suggestedFix);
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

  private static Diff parseDiff(String content) {
    var lines = content.lines().collect(Collectors.toCollection(ArrayList::new));
    if (lines.isEmpty()) {
      return new Diff("", "");
    }
    var relevantLinesForDiff = new ArrayList<String>();
    for (String line : lines) {
      if (!line.startsWith("```") && !line.startsWith("---") && !line.startsWith("+++") && !line.startsWith("@@")) {
        relevantLinesForDiff.add(line);
      }
    }
    var previousContent = new StringBuilder();
    var newContent = new StringBuilder();
    var previousContentLineSeparator = "";
    var newContentLineSeparator = "";
    for (String relevantLine : relevantLinesForDiff) {
      if (relevantLine.startsWith("-")) {
        previousContent.append(previousContentLineSeparator);
        previousContent.append(relevantLine.substring(1));
        previousContentLineSeparator = "\n";
      } else if (relevantLine.startsWith("+")) {
        newContent.append(newContentLineSeparator);
        newContent.append(relevantLine.substring(1));
        newContentLineSeparator = "\n";
      } else {
        previousContent.append(previousContentLineSeparator);
        previousContent.append(relevantLine);
        previousContentLineSeparator = "\n";
        newContent.append(newContentLineSeparator);
        newContent.append(relevantLine);
        newContentLineSeparator = "\n";
      }
    }
    return new Diff(previousContent.toString(), newContent.toString());
  }

  private static class Diff {
    private final String before;
    private final String after;

    private Diff(String before, String after) {
      this.before = before;
      this.after = after;
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
