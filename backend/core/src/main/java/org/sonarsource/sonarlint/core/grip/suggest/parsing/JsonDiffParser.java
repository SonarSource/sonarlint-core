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
import java.util.List;
import java.util.stream.Collectors;
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
    return fixes.stream().map(JsonDiffParser::convertDiff).collect(Collectors.toList());
  }

  private static DiffDto convertDiff(FixPayload fix) {
    var beforeSnippet = new StringBuilder();
    var lineSeparator = "";
    var beforeFirstLine = -1;
    var beforeLastLine = -1;
    for (String lineBefore : fix.before) {
      var fragments = lineBefore.split(":", 2);
      var lineNumber = Integer.parseInt(fragments[0]);
      if (beforeFirstLine == -1) {
        beforeFirstLine = lineNumber;
      }
      beforeLastLine = lineNumber;
      if (fragments.length > 1) {
        var line = fragments[1];
        beforeSnippet.append(lineSeparator).append(line);
        lineSeparator = "\n";
      }
    }
    if (beforeFirstLine == -1 || beforeFirstLine > beforeLastLine) {
      throw new IllegalStateException("Provided line numbers are invalid");
    }

    var afterSnippet = new StringBuilder();
    lineSeparator = "";
    if (fix.after != null && !fix.after.isEmpty()) {
      for (String lineAfter : fix.after) {
        var fragments = lineAfter.split(":", 2);
        if (fragments.length > 1) {
          var line = fragments[1];
          afterSnippet.append(lineSeparator).append(line);
          lineSeparator = "\n";
        }
      }
    }
    return new DiffDto(beforeSnippet.toString(), afterSnippet.toString(), new LineRangeDto(beforeFirstLine, beforeLastLine));
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
