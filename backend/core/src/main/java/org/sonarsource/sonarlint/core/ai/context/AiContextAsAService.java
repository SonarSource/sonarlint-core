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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.ai.context.api.IndexRequestBody;
import org.sonarsource.sonarlint.core.ai.context.api.QueryResponseBody;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
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
  private final FileExclusionService fileExclusionService;
  private final boolean isIndexingEnabled;

  public AiContextAsAService(HttpClientProvider httpClientProvider, FileExclusionService fileExclusionService, InitializeParams params) {
    this.httpClientProvider = httpClientProvider;
    this.fileExclusionService = fileExclusionService;
    this.isIndexingEnabled = params.getBackendCapabilities().contains(BackendCapability.CONTEXT_INDEXING_ENABLED);
  }

  @EventListener
  public void onFileSystemInitialized(FileSystemInitialized event) {
    if (!isIndexingEnabled) {
      return;
    }
    var targetForCopy = SonarLintLogger.get().getTargetForCopy();
    ForkJoinPool.commonPool().execute(() -> considerIndexing(event.configurationScopeId(), event.files(), targetForCopy));
  }

  private void considerIndexing(String configurationScopeId, List<ClientFile> files, LogOutput logOutput) {
    SonarLintLogger.get().setTarget(logOutput);
    LOG.info("Considering indexing {} for {} files...", configurationScopeId, files.size());
    var filesByUri = files.stream().collect(Collectors.toMap(ClientFile::getUri, UnaryOperator.identity()));
    var filesStatus = fileExclusionService.getFilesStatus(Map.of(configurationScopeId, new ArrayList<>(filesByUri.keySet())));
    var filesToIndex = filesStatus.entrySet().stream()
      .filter(f -> !f.getValue().isExcluded())
      .toList();
    if (filesToIndex.isEmpty()) {
      LOG.info("Skipping indexing for empty scope '{}'", configurationScopeId);
      return;
    }
    LOG.info("Starting indexing {} files for {}...", files.size(), configurationScopeId);
    var requestBody = new IndexRequestBody(
      filesToIndex.stream()
        .map(f -> {
          var uri = f.getKey();
          var file = filesByUri.get(uri);
          var detectedLanguage = file.getDetectedLanguage();
          var language = detectedLanguage == null ? null : detectedLanguage.name();
          return new IndexRequestBody.File(file.getContent(), file.getClientRelativePath().toString(), new IndexRequestBody.File.Metadata(language));
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
      var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();
      var responseBody = gson.fromJson(response.bodyAsString(), QueryResponseBody.class);
      return new AskCodebaseQuestionResponse(responseBody.text(), merge(responseBody.matches()).stream().map(m -> {
        var startLine = m.startRow();
        var textRange = startLine == null ? null : new TextRangeDto(startLine, m.startColumn(), m.endRow(), m.endColumn());
        return new CodeLocation(m.filename(), textRange);
      }).toList());
    }
  }

  private List<QueryResponseBody.Match> merge(List<QueryResponseBody.Match> matches) {
    var matchesByFile = matches.stream().collect(Collectors.groupingBy(QueryResponseBody.Match::filename, Collectors.toList()));
    return matchesByFile.entrySet().stream()
      .map(entry -> {
        var filename = entry.getKey();
        var ms = entry.getValue();
        if (ms.size() == 1) {
          return ms.get(0);
        }
        var mergedChunk = ms.stream().reduce((m1, m2) -> {
          var firstChunk = m1.startRow() <= m2.startRow() && m1.startColumn() <= m2.startColumn() ? m1 : m2;
          var lastChunk = m1.endRow() <= m2.endRow() && m1.endColumn() <= m2.endColumn() ? m2 : m1;
          return new QueryResponseBody.Match(filename, firstChunk.startRow(), firstChunk.startColumn(), lastChunk.endRow(), lastChunk.endColumn());
        });
        return mergedChunk.get();
      })
      .toList();
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
