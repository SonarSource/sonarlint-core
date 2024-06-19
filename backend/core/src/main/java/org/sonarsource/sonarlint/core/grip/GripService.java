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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.grip.suggest.FixSuggestion;
import org.sonarsource.sonarlint.core.grip.suggest.parsing.BeforeAfterParser;
import org.sonarsource.sonarlint.core.grip.suggest.parsing.JsonDiffParser;
import org.sonarsource.sonarlint.core.grip.suggest.parsing.GripSuggestionParser;
import org.sonarsource.sonarlint.core.grip.web.api.GripWebApi;
import org.sonarsource.sonarlint.core.grip.web.api.SuggestFixWebApiRequest;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.ProvideFeedbackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestionError;

@Named
@Singleton
public class GripService {
  private final GripWebApi gripWebApi;
  private final ClientFileSystemService fileSystemService;
  private final Map<String, GripSuggestionParser> parsersByPromptId = Map.of(
    "openai.generic.20240614", new BeforeAfterParser(),
    "openai.json-diff.20240619", new JsonDiffParser()
    );
  private final Map<UUID, FixSuggestion> pastSuggestionsById = new HashMap<>();

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
    var request = new SuggestFixWebApiRequest(params.getServiceURI(), params.getAuthenticationToken(), params.getPromptId(), fileContent, params.getIssueMessage(),
      params.getIssueRange(), params.getRuleKey());
    var webApiResponse = gripWebApi.suggestFix(request, fileContent);
    if (webApiResponse.isLeft()) {
      return new SuggestFixResponse(new SuggestionError(webApiResponse.getLeft()));
    }
    var apiResponse = webApiResponse.getRight();
    var parser = parsersByPromptId.get(params.getPromptId());
    if (parser == null) {
      return new SuggestFixResponse(new SuggestionError("The requested prompt does not exist"));
    }
    var parsedSuggestion = parser.parse(request, apiResponse);
    if (parsedSuggestion.isLeft()) {
      return new SuggestFixResponse(new SuggestionError(parsedSuggestion.getLeft()));
    }
    var suggestion = parsedSuggestion.getRight();
    pastSuggestionsById.put(suggestion.getCorrelationId(), suggestion);
    return new SuggestFixResponse(new SuggestionDto(suggestion.getCorrelationId(), suggestion.getApiRawText(), suggestion.getFix()));
  }

  public void provideFeedback(ProvideFeedbackParams params) {
    var correlationId = params.getCorrelationId();
    var pastSuggestion = pastSuggestionsById.get(correlationId);
    if (pastSuggestion == null) {
      throw new IllegalArgumentException("Cannot find previous suggestion with ID=" + correlationId);
    }
    gripWebApi.provideFeedback(params, pastSuggestion);
  }
}
