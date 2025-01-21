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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.BindingCandidatesFinder;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.sync.SonarProjectBranchesSynchronizationService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment.PRODUCTION_EU_URI;

class ShowFixSuggestionRequestHandlerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);
  private ConnectionConfigurationRepository connectionConfigurationRepository;
  private ConfigurationRepository configurationRepository;
  private SonarLintRpcClient sonarLintRpcClient;
  private FeatureFlagsDto featureFlagsDto;
  private InitializeParams initializeParams;
  private ShowFixSuggestionRequestHandler showFixSuggestionRequestHandler;
  private TelemetryService telemetryService;
  private ClientFile clientFile;
  private FilePathTranslation filePathTranslation;

  @BeforeEach
  void setup() {
    connectionConfigurationRepository = mock(ConnectionConfigurationRepository.class);
    configurationRepository = mock(ConfigurationRepository.class);
    var bindingSuggestionProvider = mock(BindingSuggestionProvider.class);
    var bindingCandidatesFinder = mock(BindingCandidatesFinder.class);
    sonarLintRpcClient = mock(SonarLintRpcClient.class);
    filePathTranslation = mock(FilePathTranslation.class);
    var pathTranslationService = mock(PathTranslationService.class);
    when(pathTranslationService.getOrComputePathTranslation(any())).thenReturn(Optional.of(filePathTranslation));
    var userTokenService = mock(UserTokenService.class);
    featureFlagsDto = mock(FeatureFlagsDto.class);
    when(featureFlagsDto.canOpenFixSuggestion()).thenReturn(true);
    initializeParams = mock(InitializeParams.class);
    when(initializeParams.getFeatureFlags()).thenReturn(featureFlagsDto);
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prodEu();
    telemetryService = mock(TelemetryService.class);
    var sonarProjectBranchesSynchronizationService = mock(SonarProjectBranchesSynchronizationService.class);
    when(sonarProjectBranchesSynchronizationService.getProjectBranches(any(), any(), any())).thenReturn(new ProjectBranches(Set.of(), "main"));
    clientFile = mock(ClientFile.class);
    var clientFs = mock(ClientFileSystemService.class);
    when(clientFs.getFiles(any())).thenReturn(List.of(clientFile));
    var connectionConfiguration = mock(ConnectionConfigurationRepository.class);
    when(connectionConfiguration.hasConnectionWithOrigin(PRODUCTION_EU_URI.toString())).thenReturn(true);

    showFixSuggestionRequestHandler = new ShowFixSuggestionRequestHandler(sonarLintRpcClient, telemetryService, initializeParams,
      new RequestHandlerBindingAssistant(bindingSuggestionProvider, bindingCandidatesFinder, sonarLintRpcClient, connectionConfigurationRepository,
        configurationRepository, userTokenService, sonarCloudActiveEnvironment, connectionConfiguration), pathTranslationService,
      sonarCloudActiveEnvironment, sonarProjectBranchesSynchronizationService, clientFs);
  }

  @Test
  void should_trigger_telemetry() throws URISyntaxException, HttpException, IOException {
    var request = mock(ClassicHttpRequest.class);
    when(request.getUri()).thenReturn(URI.create("/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&branch=branch" +
      "&organizationKey=sample-organization"));
    when(request.getMethod()).thenReturn(Method.POST.name());
    when(request.getHeader("Origin")).thenReturn(new BasicHeader("Origin", PRODUCTION_EU_URI));
    when(request.getEntity()).thenReturn(new StringEntity("""
      {
        "fileEdit": {
          "path": "src/main/java/Main.java",
          "changes": [{
            "beforeLineRange": {
              "startLine": 0,
              "endLine": 1
            },
            "before": "",
            "after": "var fix = 1;"
          }]
        },
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    showFixSuggestionRequestHandler.handle(request, response, context);

    verify(telemetryService).fixSuggestionReceived(any());
    verifyNoMoreInteractions(telemetryService);
  }

  @Test
  void should_extract_query_from_sc_request_without_token() throws HttpException, IOException {
    when(featureFlagsDto.canOpenFixSuggestion()).thenReturn(true);
    when(initializeParams.getFeatureFlags()).thenReturn(featureFlagsDto);
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&branch=branch" +
      "&organizationKey=sample-organization");
    request.addHeader("Origin", PRODUCTION_EU_URI);
    request.setEntity(new StringEntity("""
      {
        "fileEdit": {
          "path": "src/main/java/Main.java",
          "changes": [{
            "beforeLineRange": {
              "startLine": 0,
              "endLine": 1
            },
            "before": "",
            "after": "var fix = 1;"
          }]
        },
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var showFixSuggestionQuery = showFixSuggestionRequestHandler.extractQuery(request, request.getHeader("Origin").getValue());
    assertThat(showFixSuggestionQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(showFixSuggestionQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(showFixSuggestionQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(showFixSuggestionQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(showFixSuggestionQuery.getBranch()).isEqualTo("branch");
    assertThat(showFixSuggestionQuery.getTokenName()).isNull();
    assertThat(showFixSuggestionQuery.getTokenValue()).isNull();
    assertThat(showFixSuggestionQuery.getFixSuggestion().suggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
    assertThat(showFixSuggestionQuery.getFixSuggestion().explanation()).isEqualTo("Modifying the variable name is good");
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().path()).isEqualTo("src/main/java/Main.java");
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).before()).isEmpty();
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).after()).isEqualTo("var fix = 1;");
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).beforeLineRange().startLine()).isZero();
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).beforeLineRange().endLine()).isEqualTo(1);
  }

  @Test
  void should_extract_query_from_sc_request_with_token() throws HttpException, IOException {
    when(featureFlagsDto.canOpenFixSuggestion()).thenReturn(true);
    when(initializeParams.getFeatureFlags()).thenReturn(featureFlagsDto);
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.addHeader("Origin", PRODUCTION_EU_URI);
    request.setEntity(new StringEntity("""
      {
        "fileEdit": {
          "path": "src/main/java/Main.java",
          "changes": [{
            "beforeLineRange": {
              "startLine": 0,
              "endLine": 1
            },
            "before": "",
            "after": "var fix = 1;"
          }]
        },
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var showFixSuggestionQuery = showFixSuggestionRequestHandler.extractQuery(request, request.getHeader("Origin").getValue());
    assertThat(showFixSuggestionQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(showFixSuggestionQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(showFixSuggestionQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(showFixSuggestionQuery.getTokenName()).isEqualTo("abc");
    assertThat(showFixSuggestionQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(showFixSuggestionQuery.getTokenValue()).isEqualTo("123");
    assertThat(showFixSuggestionQuery.getFixSuggestion().suggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
    assertThat(showFixSuggestionQuery.getFixSuggestion().explanation()).isEqualTo("Modifying the variable name is good");
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().path()).isEqualTo("src/main/java/Main.java");
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).before()).isEmpty();
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).after()).isEqualTo("var fix = 1;");
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).beforeLineRange().startLine()).isZero();
    assertThat(showFixSuggestionQuery.getFixSuggestion().fileEdit().changes().get(0).beforeLineRange().endLine()).isEqualTo(1);
  }

  @Test
  void should_validate_fix_suggestion_query_for_sc() {
    assertThat(new ShowFixSuggestionRequestHandler.ShowFixSuggestionQuery(null, "project", "issue", "branch", "name", "value",
      "organizationKey", true, generateFixSuggestionPayload()).isValid()).isTrue();
    assertThat(new ShowFixSuggestionRequestHandler.ShowFixSuggestionQuery(null, "project", "issue", "branch", "name", "value", null, true
      , generateFixSuggestionPayload()).isValid()).isFalse();
  }

  @Test
  void should_cancel_flow_when_branch_does_not_match() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&branch=branch" +
      "&organizationKey=sample-organization");
    request.addHeader("Origin", PRODUCTION_EU_URI);
    request.setEntity(new StringEntity("""
      {
        "fileEdit": {
          "path": "src/main/java/Main.java",
          "changes": [{
            "beforeLineRange": {
              "startLine": 0,
              "endLine": 1
            },
            "before": "",
            "after": "var fix = 1;"
          }]
        },
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    when(connectionConfigurationRepository.findByOrganization(any())).thenReturn(List.of(
      new SonarCloudConnectionConfiguration(PRODUCTION_EU_URI, "name", "organizationKey", false)));
    when(configurationRepository.getBoundScopesToConnectionAndSonarProject(any(), any())).thenReturn(List.of(new BoundScope("configScope", "connectionId", "projectKey")));
    when(sonarLintRpcClient.matchProjectBranch(any())).thenReturn(CompletableFuture.completedFuture(new MatchProjectBranchResponse(false)));

    showFixSuggestionRequestHandler.handle(request, response, context);
    var showMessageArgumentCaptor = ArgumentCaptor.forClass(ShowMessageParams.class);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(sonarLintRpcClient).showMessage(showMessageArgumentCaptor.capture()));
    assertThat(showMessageArgumentCaptor.getValue().getType()).isEqualTo(MessageType.ERROR);
    assertThat(showMessageArgumentCaptor.getValue().getText()).isEqualTo("Attempted to show a fix suggestion from branch 'branch', " +
      "which is different from the currently checked-out branch.\nPlease switch to the correct branch and try again.");
    verify(sonarLintRpcClient).matchProjectBranch(any());
    verifyNoMoreInteractions(sonarLintRpcClient);
  }

  @Test
  void should_find_main_branch_when_not_provided_and_not_stored() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr" +
      "&organizationKey=sample-organization");
    request.addHeader("Origin", PRODUCTION_EU_URI);
    request.setEntity(new StringEntity("""
      {
        "fileEdit": {
          "path": "src/main/java/Main.java",
          "changes": [{
            "beforeLineRange": {
              "startLine": 0,
              "endLine": 1
            },
            "before": "",
            "after": "var fix = 1;"
          }]
        },
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    when(clientFile.getUri()).thenReturn(URI.create("file:///src/main/java/Main.java"));
    when(filePathTranslation.serverToIdePath(any())).thenReturn(Path.of("src/main/java/Main.java"));
    when(connectionConfigurationRepository.findByOrganization(any())).thenReturn(List.of(
      new SonarCloudConnectionConfiguration(PRODUCTION_EU_URI, "name", "organizationKey", false)));
    when(configurationRepository.getBoundScopesToConnectionAndSonarProject(any(), any())).thenReturn(List.of(new BoundScope("configScope", "connectionId", "projectKey")));
    when(sonarLintRpcClient.matchProjectBranch(any())).thenReturn(CompletableFuture.completedFuture(new MatchProjectBranchResponse(true)));

    showFixSuggestionRequestHandler.handle(request, response, context);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(sonarLintRpcClient).showFixSuggestion(any()));
  }

  @Test
  void should_verify_missing_origin() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&branch=branch" +
      "&organizationKey=sample-organization");
    request.setEntity(new StringEntity("""
      {
        "fileEdit": {
          "path": "src/main/java/Main.java",
          "changes": [{
            "beforeLineRange": {
              "startLine": 0,
              "endLine": 1
            },
            "before": "",
            "after": "var fix = 1;"
          }]
        },
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    showFixSuggestionRequestHandler.handle(request, response, context);

    verifyNoMoreInteractions(sonarLintRpcClient);
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

}
