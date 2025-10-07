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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.AskCodebaseQuestionParams;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextMockDataTests {

  private SonarLintRpcServerImpl rpcServer;

  @BeforeEach
  void setUp() {
    var in = new ByteArrayInputStream(new byte[0]);
    var out = new ByteArrayOutputStream();
    rpcServer = new SonarLintRpcServerImpl(in, out);
  }

  @Test
  void should_return_mock_data_for_authentication_question() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    var params = new AskCodebaseQuestionParams("test-project", "How does authentication work?");
    
    var future = aiContextService.askCodebaseQuestion(params);
    var response = future.get(5, TimeUnit.SECONDS);
    
    assertThat(response).isNotNull();
    assertThat(response.getLocations()).isNotEmpty();
    assertThat(response.getLocations().size()).isBetween(1, 6);
    
    // Verify each location has valid data
    response.getLocations().forEach(location -> {
      assertThat(location.getFilePath()).isNotEmpty();
      assertThat(location.getStartLine()).isPositive();
      assertThat(location.getEndLine()).isGreaterThanOrEqualTo(location.getStartLine());
    });
  }

  @Test
  void should_return_mock_data_for_database_question() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    var params = new AskCodebaseQuestionParams("test-project", "How does database connection work?");
    
    var future = aiContextService.askCodebaseQuestion(params);
    var response = future.get(5, TimeUnit.SECONDS);
    
    assertThat(response).isNotNull();
    assertThat(response.getLocations()).isNotEmpty();
    
    // Verify each location has valid data
    response.getLocations().forEach(location -> {
      assertThat(location.getFilePath()).isNotEmpty();
      assertThat(location.getStartLine()).isPositive();
      assertThat(location.getEndLine()).isGreaterThanOrEqualTo(location.getStartLine());
    });
  }

  @Test
  void should_return_empty_results_for_nothing_question() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    var params = new AskCodebaseQuestionParams("test-project", "Show me nothing");
    
    var future = aiContextService.askCodebaseQuestion(params);
    var response = future.get(5, TimeUnit.SECONDS);
    
    assertThat(response).isNotNull();
    assertThat(response.getLocations()).isEmpty();
  }

  @Test
  void should_return_empty_results_for_empty_question() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    var params = new AskCodebaseQuestionParams("test-project", "I want empty results");
    
    var future = aiContextService.askCodebaseQuestion(params);
    var response = future.get(5, TimeUnit.SECONDS);
    
    assertThat(response).isNotNull();
    assertThat(response.getLocations()).isEmpty();
  }

  @Test
  void should_return_different_results_for_different_questions() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    
    var authParams = new AskCodebaseQuestionParams("test-project", "How does authentication work?");
    var apiParams = new AskCodebaseQuestionParams("test-project", "Show me the API endpoints");
    
    var authFuture = aiContextService.askCodebaseQuestion(authParams);
    var apiFuture = aiContextService.askCodebaseQuestion(apiParams);
    
    var authResponse = authFuture.get(5, TimeUnit.SECONDS);
    var apiResponse = apiFuture.get(5, TimeUnit.SECONDS);
    
    assertThat(authResponse.getLocations()).isNotEmpty();
    assertThat(apiResponse.getLocations()).isNotEmpty();
    
    // Verify all locations have valid data
    authResponse.getLocations().forEach(location -> {
      assertThat(location.getFilePath()).isNotEmpty();
      assertThat(location.getStartLine()).isPositive();
      assertThat(location.getEndLine()).isGreaterThanOrEqualTo(location.getStartLine());
    });
    
    apiResponse.getLocations().forEach(location -> {
      assertThat(location.getFilePath()).isNotEmpty();
      assertThat(location.getStartLine()).isPositive();
      assertThat(location.getEndLine()).isGreaterThanOrEqualTo(location.getStartLine());
    });
  }

  @Test
  void should_generate_realistic_file_paths() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    var params = new AskCodebaseQuestionParams("test-project", "Show me the code structure");
    
    var future = aiContextService.askCodebaseQuestion(params);
    var response = future.get(5, TimeUnit.SECONDS);
    
    assertThat(response.getLocations()).isNotEmpty();
    
    // Verify file paths look realistic
    response.getLocations().forEach(location -> {
      String filePath = location.getFilePath();
      assertThat(filePath).isNotEmpty();
      
      // Should contain realistic path elements
      boolean hasRealisticPath = filePath.contains("src/") || 
                                filePath.contains("java/") || 
                                filePath.contains("main/") ||
                                filePath.contains("test/") ||
                                filePath.contains("frontend/") ||
                                filePath.contains("api/") ||
                                filePath.contains("docker/");
      
      assertThat(hasRealisticPath).isTrue();
    });
  }

  @Test
  void should_generate_valid_line_numbers() throws Exception {
    var aiContextService = rpcServer.getAiContextRpcService();
    var params = new AskCodebaseQuestionParams("test-project", "Find me some code");
    
    var future = aiContextService.askCodebaseQuestion(params);
    var response = future.get(5, TimeUnit.SECONDS);
    
    assertThat(response.getLocations()).isNotEmpty();
    
    // Verify line numbers are realistic
    response.getLocations().forEach(location -> {
      assertThat(location.getStartLine()).isPositive();
      assertThat(location.getEndLine()).isPositive();
      assertThat(location.getEndLine()).isGreaterThanOrEqualTo(location.getStartLine());
      
      // Line numbers should be reasonable (not too large)
      assertThat(location.getStartLine()).isLessThanOrEqualTo(250);
      assertThat(location.getEndLine()).isLessThanOrEqualTo(300);
      
      // Range should be reasonable (not too large)
      int range = location.getEndLine() - location.getStartLine();
      assertThat(range).isLessThanOrEqualTo(50);
    });
  }
}
