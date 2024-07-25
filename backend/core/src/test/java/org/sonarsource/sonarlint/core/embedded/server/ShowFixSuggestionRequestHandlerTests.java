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
package org.sonarsource.sonarlint.core.embedded.server;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.BindingCandidatesFinder;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ShowFixSuggestionRequestHandlerTests {

  @Test
  // TODO: Enable when telemetry is done
  @Disabled
  void should_trigger_telemetry() throws URISyntaxException, HttpException, IOException {
    var request = mock(ClassicHttpRequest.class);
    when(request.getUri()).thenReturn(URI.create("http://localhost:8000/issue?project=pk&issue=ik&branch=b&server=s"));
    when(request.getMethod()).thenReturn(Method.GET.name());
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);
    var telemetryService = mock(TelemetryService.class);
    var showFixSuggestionRequestHandler = getShowFixSuggestionRequestHandler();

    showFixSuggestionRequestHandler.handle(request, response, context);

    verify(telemetryService).showIssueRequestReceived();
    verifyNoMoreInteractions(telemetryService);
  }

  @Test
  void should_extract_query_from_sc_request_without_token() throws HttpException, IOException {
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    var featureFlagsDto = mock(FeatureFlagsDto.class);
    when(featureFlagsDto.canOpenFixSuggestion()).thenReturn(true);
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.getFeatureFlags()).thenReturn(featureFlagsDto);
    var showFixSuggestionRequestHandler = new ShowFixSuggestionRequestHandler(null, null, initializeParams, null, null, sonarCloudActiveEnvironment);
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr" +
      "&organizationKey=sample-organization");
    request.addHeader("Origin", SonarCloudActiveEnvironment.PRODUCTION_URI);
    request.setEntity(new StringEntity("{\n" +
      "\"fileEdit\": {\n" +
      "\"path\": \"src/main/java/Main.java\",\n" +
      "\"changes\": [{\n" +
      "\"beforeLineRange\": {\n" +
      "\"startLine\": 0,\n" +
      "\"endLine\": 1\n" +
      "},\n" +
      "\"before\": \"\",\n" +
      "\"after\": \"var fix = 1;\"\n" +
      "}]\n" +
      "},\n" +
      "\"suggestionId\": \"eb93b2b4-f7b0-4b5c-9460-50893968c264\",\n" +
      "\"explanation\": \"Modifying the variable name is good\"\n" +
      "}\n"));
    var showFixSuggestionQuery = showFixSuggestionRequestHandler.extractQuery(request);
    assertThat(showFixSuggestionQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(showFixSuggestionQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(showFixSuggestionQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(showFixSuggestionQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(showFixSuggestionQuery.getTokenName()).isNull();
    assertThat(showFixSuggestionQuery.getTokenValue()).isNull();
    assertThat(showFixSuggestionQuery.getFixSuggestion().getSuggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getExplanation()).isEqualTo("Modifying the variable name is good");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getPath()).isEqualTo("src/main/java/Main.java");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getBefore()).isEmpty();
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getAfter()).isEqualTo("var fix = 1;");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getBeforeLineRange().getStartLine()).isZero();
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getBeforeLineRange().getEndLine()).isEqualTo(1);
  }

  @Test
  void should_extract_query_from_sc_request_with_token() throws HttpException, IOException {
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    var featureFlagsDto = mock(FeatureFlagsDto.class);
    when(featureFlagsDto.canOpenFixSuggestion()).thenReturn(true);
    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.getFeatureFlags()).thenReturn(featureFlagsDto);
    var showFixSuggestionRequestHandler = new ShowFixSuggestionRequestHandler(null, null, initializeParams, null, null, sonarCloudActiveEnvironment);
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.addHeader("Origin", SonarCloudActiveEnvironment.PRODUCTION_URI);
    request.setEntity(new StringEntity("{\n" +
      "\"fileEdit\": {\n" +
      "\"path\": \"src/main/java/Main.java\",\n" +
      "\"changes\": [{\n" +
      "\"beforeLineRange\": {\n" +
      "\"startLine\": 0,\n" +
      "\"endLine\": 1\n" +
      "},\n" +
      "\"before\": \"\",\n" +
      "\"after\": \"var fix = 1;\"\n" +
      "}]\n" +
      "},\n" +
      "\"suggestionId\": \"eb93b2b4-f7b0-4b5c-9460-50893968c264\",\n" +
      "\"explanation\": \"Modifying the variable name is good\"\n" +
      "}\n"));
    var showFixSuggestionQuery = showFixSuggestionRequestHandler.extractQuery(request);
    assertThat(showFixSuggestionQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(showFixSuggestionQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(showFixSuggestionQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(showFixSuggestionQuery.getTokenName()).isEqualTo("abc");
    assertThat(showFixSuggestionQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(showFixSuggestionQuery.getTokenValue()).isEqualTo("123");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getSuggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getExplanation()).isEqualTo("Modifying the variable name is good");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getPath()).isEqualTo("src/main/java/Main.java");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getBefore()).isEmpty();
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getAfter()).isEqualTo("var fix = 1;");
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getBeforeLineRange().getStartLine()).isZero();
    assertThat(showFixSuggestionQuery.getFixSuggestion().getFileEdit().getChanges().get(0).getBeforeLineRange().getEndLine()).isEqualTo(1);
  }

  @Test
  void should_validate_fix_suggestion_query_for_sc() {
    assertThat(new ShowFixSuggestionRequestHandler.ShowFixSuggestionQuery(null, "project", "issue", "branch", "name", "value",
      "organizationKey", true, generateFixSuggestionPayload()).isValid()).isTrue();
    assertThat(new ShowFixSuggestionRequestHandler.ShowFixSuggestionQuery(null, "project", "issue", "branch", "name", "value", null, true
      , generateFixSuggestionPayload()).isValid()).isFalse();
  }

  private static ShowFixSuggestionRequestHandler.FixSuggestionPayload generateFixSuggestionPayload() {
    return new ShowFixSuggestionRequestHandler.FixSuggestionPayload(
      new ShowFixSuggestionRequestHandler.FileEditPayload(
        List.of(new ShowFixSuggestionRequestHandler.ChangesPayload(
          new ShowFixSuggestionRequestHandler.TextRangePayload(0, 1),
          "before",
          "after"
        )),
        "path"
      ),
      "suggestionId",
      "explanation"
    );
  }

  private static ShowFixSuggestionRequestHandler getShowFixSuggestionRequestHandler() {
    var telemetryService = mock(TelemetryService.class);
    var repository = mock(ConnectionConfigurationRepository.class);
    var configurationRepository = mock(ConfigurationRepository.class);
    var bindingSuggestionProvider = mock(BindingSuggestionProvider.class);
    var bindingCandidatesFinder = mock(BindingCandidatesFinder.class);
    var sonarLintClient = mock(SonarLintRpcClient.class);
    var pathTranslationService = mock(PathTranslationService.class);
    var userTokenService = mock(UserTokenService.class);
    var initializeParams = mock(InitializeParams.class);
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();

    return new ShowFixSuggestionRequestHandler(sonarLintClient, telemetryService, initializeParams,
      new RequestHandlerBindingAssistant(bindingSuggestionProvider, bindingCandidatesFinder, sonarLintClient, repository, configurationRepository, userTokenService, sonarCloudActiveEnvironment), pathTranslationService, sonarCloudActiveEnvironment);
  }
}
