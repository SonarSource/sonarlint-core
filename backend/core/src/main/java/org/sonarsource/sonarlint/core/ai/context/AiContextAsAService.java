/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.context;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.sonarsource.sonarlint.core.ai.context.api.IndexRequestBody;
import org.sonarsource.sonarlint.core.ai.context.api.QueryResponseBody;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.fs.FileSystemInitialized;
import org.sonarsource.sonarlint.core.fs.FileSystemUpdatedEvent;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AskCodebaseQuestionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.CodeLocation;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.springframework.context.event.EventListener;

public class AiContextAsAService {
  private static final Random RANDOM = new Random();
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String CONTEXT_SERVER_URL = "http://localhost:8080";
  private static final String INDEX_API_PATH = "/index";
  private static final String QUERY_API_PATH = "/query";
  private final HttpClientProvider httpClientProvider;
  private final boolean isIndexingEnabled;

  public AiContextAsAService(HttpClientProvider httpClientProvider, InitializeParams params) {
    this.httpClientProvider = httpClientProvider;
    this.isIndexingEnabled = params.getBackendCapabilities().contains(BackendCapability.CONTEXT_INDEXING_ENABLED);
  }

  @EventListener
  public void onFileSystemInitialized(FileSystemInitialized event) {
    if (!isIndexingEnabled) {
      return;
    }
    LOG.info("Starting indexing {} files...", event.files().size());
    var requestBody = new IndexRequestBody(
      event.files().stream()
        .map(f -> {
          var detectedLanguage = f.getDetectedLanguage();
          var language = detectedLanguage == null ? null : detectedLanguage.name();
          return new IndexRequestBody.File(f.getContent(), f.getClientRelativePath().toString(), new IndexRequestBody.File.Metadata(language));
        }).toList());
    var body = new Gson().toJson(requestBody);
    httpClientProvider.getHttpClient()
      .postAsync(CONTEXT_SERVER_URL + INDEX_API_PATH, HttpClient.JSON_CONTENT_TYPE, body)
      .thenAccept(response -> {
        if (response.isSuccessful()) {
          LOG.info("Indexed files successfully!");
        } else {
          LOG.error("Error indexing files: status={}, body={}", response.code(), response.bodyAsString());
        }
      })
      .exceptionally(error -> {
        LOG.error("Error when sending the index request", error);
        return null;
      });
  }

  @EventListener
  public void onFileSystemUpdated(FileSystemUpdatedEvent event) {
    if (!isIndexingEnabled) {
      return;
    }
    // TODO
  }

  public AskCodebaseQuestionResponse search(String configurationScopeId, String question) {
    if (!isIndexingEnabled) {
      return mockSearch(configurationScopeId, question);
    }
    try (var response = httpClientProvider.getHttpClient()
      .get(CONTEXT_SERVER_URL + QUERY_API_PATH + "?question=" + UrlUtils.urlEncode(question))) {
      var responseBody = new Gson().fromJson(response.bodyAsString(), QueryResponseBody.class);
      return new AskCodebaseQuestionResponse(responseBody.text(), responseBody.matches().stream().map(m -> {
        var startLine = m.startLine();
        var textRange = startLine == null ? null : new TextRangeDto(startLine, m.startColumn(), m.endLine(), m.endColumn());
        return new CodeLocation(m.fileRelativePath(), textRange);
      }).toList());
    }
  }

  private AskCodebaseQuestionResponse mockSearch(String configurationScopeId, String question) {
    LOG.info("AI Context: Received codebase question for scope '{}': '{}'", configurationScopeId, question);
    LOG.debug("AI Context: Processing question for scope '{}'", configurationScopeId);

    // TODO: Replace this mock implementation with actual AI Context service
    var mockResults = generateMockCodebaseResults(question);
    var response = new AskCodebaseQuestionResponse("Here is what I found", mockResults);

    LOG.info("AI Context: Returning {} location(s) for scope '{}'",
      response.getLocations().size(), configurationScopeId);

    return response;
  }

  /**
   * Generate mock codebase search results for testing purposes.
   * TODO: Replace this method with actual AI-powered codebase search implementation.
   *
   * @param question The user's question about the codebase
   * @return A list of mock code locations relevant to the question
   */
  private List<CodeLocation> generateMockCodebaseResults(String question) {
    LOG.debug("AI Context: Generating mock results for question: '{}'", question);

    // Return empty results for some questions to test that scenario
    if (question != null && (question.toLowerCase().contains("nothing") || question.toLowerCase().contains("empty"))) {
      return Collections.emptyList();
    }

    // Generate different numbers of results (1-6) randomly
    int numResults = RANDOM.nextInt(6) + 1;

    // Mock file paths with realistic structures
    String[] mockFilePaths = {
      // Authentication-related
      "src/main/java/com/example/security/AuthenticationService.java",
      "src/main/java/com/example/security/AuthController.java",
      "src/main/java/com/example/security/JwtTokenProvider.java",
      "src/main/java/com/example/security/SecurityConfig.java",
      "src/main/java/com/example/model/User.java",

      // Database-related
      "src/main/java/com/example/repository/UserRepository.java",
      "src/main/java/com/example/service/DatabaseService.java",
      "src/main/resources/db/migration/V1__Create_users_table.sql",
      "src/main/java/com/example/config/DatabaseConfig.java",

      // API-related
      "src/main/java/com/example/controller/ApiController.java",
      "src/main/java/com/example/dto/ApiResponse.java",
      "src/main/java/com/example/exception/ApiExceptionHandler.java",
      "api/openapi.yml",

      // Frontend-related
      "src/frontend/components/LoginForm.tsx",
      "src/frontend/services/authService.ts",
      "src/frontend/utils/apiClient.ts",
      "src/frontend/hooks/useAuth.ts",

      // Configuration-related
      "src/main/resources/application.yml",
      "docker/Dockerfile",
      "src/main/java/com/example/config/AppConfig.java",

      // Testing-related
      "src/test/java/com/example/security/AuthenticationServiceTest.java",
      "src/test/java/com/example/integration/AuthIntegrationTest.java",

      // Utility classes
      "src/main/java/com/example/util/ValidationUtils.java",
      "src/main/java/com/example/util/CryptoUtils.java",
      "src/main/java/com/example/util/DateUtils.java"
    };

    List<CodeLocation> results = Arrays.asList(new CodeLocation[numResults]);

    for (int i = 0; i < numResults; i++) {
      // Pick a random file path
      String filePath = mockFilePaths[RANDOM.nextInt(mockFilePaths.length)];

      // Generate random but realistic line numbers
      int startLine = RANDOM.nextInt(200) + 1; // 1-200
      int startLineOffset = RANDOM.nextInt(5) + 1; // 1-200
      int endLine = startLine + RANDOM.nextInt(50) + 1; // +1 to +50 lines
      int endLineOffset = RANDOM.nextInt(5) + 1; // 1-200

      results.set(i, new CodeLocation(filePath, new TextRangeDto(startLine, startLineOffset, endLine, endLineOffset)));
    }

    LOG.debug("AI Context: Generated {} mock results", results.size());
    return results;
  }
}
