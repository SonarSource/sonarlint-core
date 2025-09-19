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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AutomaticAnalysisEnablementRequestHandlerTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);

  private final Gson gson = new Gson();
  private AnalysisService analysisService;
  private AutomaticAnalysisEnablementRequestHandler automaticAnalysisEnablementRequestHandler;
  private HttpContext context;

  @BeforeEach
  void setup() {
    analysisService = mock(AnalysisService.class);
    context = mock(HttpContext.class);
    automaticAnalysisEnablementRequestHandler = new AutomaticAnalysisEnablementRequestHandler(analysisService);
  }

  @Test
  void should_reject_non_post_requests() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest(Method.GET, "/analysis/automatic/config");
    var response = new BasicClassicHttpResponse(200);

    automaticAnalysisEnablementRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    verifyNoInteractions(analysisService);
  }

  @Test
  void should_reject_invalid_enabled_parameter() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest(Method.POST, "/analysis/automatic/config?invalid=param");
    var response = new BasicClassicHttpResponse(200);

    automaticAnalysisEnablementRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    verifyNoInteractions(analysisService);
  }

  @Test
  void should_handle_analysis_service_exception() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest(Method.POST, "/analysis/automatic/config?enabled=true");
    var response = new BasicClassicHttpResponse(200);
    var exception = new RuntimeException("Analysis service failed");
    doThrow(exception).when(analysisService).didChangeAutomaticAnalysisSetting(anyBoolean());

    automaticAnalysisEnablementRequestHandler.handle(request, response, context);

    assertThat(response.getCode()).isEqualTo(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    assertThat(response.getEntity()).isNotNull();
    var responseContent = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    var errorMessage = gson.fromJson(responseContent, AutomaticAnalysisEnablementRequestHandler.ErrorMessage.class);
    assertThat(errorMessage.message()).contains("Failed to change automatic analysis");
  }

}
