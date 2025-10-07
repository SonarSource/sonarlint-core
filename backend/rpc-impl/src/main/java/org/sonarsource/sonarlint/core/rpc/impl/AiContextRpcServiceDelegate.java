/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AiContextRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AskCodebaseQuestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AskCodebaseQuestionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.CodeLocation;

public class AiContextRpcServiceDelegate extends AbstractRpcServiceDelegate implements AiContextRpcService {
  
  private static final Logger LOG = LoggerFactory.getLogger(AiContextRpcServiceDelegate.class);
  private static final Random RANDOM = new Random();
  
  public AiContextRpcServiceDelegate(SonarLintRpcServerImpl sonarLintRpcServer) {
    super(sonarLintRpcServer);
  }

  @Override
  public CompletableFuture<AskCodebaseQuestionResponse> askCodebaseQuestion(AskCodebaseQuestionParams params) {
    LOG.info("AI Context: Received codebase question for scope '{}': '{}'", 
             params.getConfigurationScopeId(), params.getQuestion());
             
    return requestAsync(cancelMonitor -> {
      LOG.debug("AI Context: Processing question for scope '{}'", params.getConfigurationScopeId());
      
      // TODO: Replace this mock implementation with actual AI Context service
      var mockResults = generateMockCodebaseResults(params.getQuestion());
      var response = new AskCodebaseQuestionResponse(mockResults);
      
      LOG.info("AI Context: Returning {} location(s) for scope '{}'", 
               response.getLocations().size(), params.getConfigurationScopeId());
      
      return response;
    }, params.getConfigurationScopeId());
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
      int endLine = startLine + RANDOM.nextInt(50) + 1; // +1 to +50 lines
      
      results.set(i, new CodeLocation(filePath, startLine, endLine));
    }
    
    LOG.debug("AI Context: Generated {} mock results", results.size());
    return results;
  }
}
