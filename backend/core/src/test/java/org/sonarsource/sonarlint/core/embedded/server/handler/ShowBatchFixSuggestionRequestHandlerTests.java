/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.embedded.server.handler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.BindingCandidatesFinder;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.embedded.server.AttributeUtils;
import org.sonarsource.sonarlint.core.embedded.server.RequestHandlerBindingAssistant;
import org.sonarsource.sonarlint.core.event.FixSuggestionReceivedEvent;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.sync.SonarProjectBranchesSynchronizationService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShowBatchFixSuggestionRequestHandlerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);

  private ShowBatchFixSuggestionRequestHandler showBatchFixSuggestionRequestHandler;

  private ConnectionConfigurationRepository connectionConfigurationRepository;
  private ConfigurationRepository configurationRepository;
  private SonarLintRpcClient sonarLintRpcClient;
  private ApplicationEventPublisher eventPublisher;
  private ClientFile clientFile1;
  private ClientFile clientFile2;
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
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    eventPublisher = mock(ApplicationEventPublisher.class);
    var sonarProjectBranchesSynchronizationService = mock(SonarProjectBranchesSynchronizationService.class);
    when(sonarProjectBranchesSynchronizationService.getProjectBranches(any(), any(), any())).thenReturn(new ProjectBranches(Set.of(), "main"));
    clientFile1 = mock(ClientFile.class);
    clientFile2 = mock(ClientFile.class);
    var clientFs = mock(ClientFileSystemService.class);
    when(clientFs.getFiles(any())).thenReturn(List.of(clientFile1, clientFile2));
    var connectionConfiguration = mock(ConnectionConfigurationRepository.class);
    when(connectionConfiguration.hasConnectionWithOrigin(SonarCloudRegion.EU.getProductionUri().toString())).thenReturn(true);

    showBatchFixSuggestionRequestHandler = new ShowBatchFixSuggestionRequestHandler(sonarLintRpcClient, eventPublisher,
      new RequestHandlerBindingAssistant(bindingSuggestionProvider, bindingCandidatesFinder, sonarLintRpcClient, connectionConfigurationRepository, configurationRepository,
        sonarCloudActiveEnvironment, connectionConfiguration),
      pathTranslationService, sonarCloudActiveEnvironment, clientFs);
  }

  @Test
  void should_trigger_telemetry() throws URISyntaxException, HttpException, IOException {
    var request = mock(ClassicHttpRequest.class);
    when(request.getUri()).thenReturn(URI.create("/sonarlint/api/fix/batch/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&branch=branch" +
      "&organizationKey=sample-organization"));
    when(request.getMethod()).thenReturn(Method.POST.name());
    when(request.getEntity()).thenReturn(new StringEntity("""
      {
        "fileEdits": [
          {
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
          {
            "path": "src/main/java/Other.java",
            "changes": [{
              "beforeLineRange": {
                "startLine": 5,
                "endLine": 8
              },
              "before": "old code",
              "after": "new code"
            }]
          }
        ],
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);
    when(context.getAttribute(AttributeUtils.PARAMS_ATTRIBUTE))
      .thenReturn(Map.of(
        "project", "org.sonarsource.sonarlint.core:sonarlint-core-parent",
        "issue", "AX2VL6pgAvx3iwyNtLyr",
        "branch", "branch",
        "organizationKey", "sample-organization"
      ));
    when(context.getAttribute(AttributeUtils.ORIGIN_ATTRIBUTE))
      .thenReturn(SonarCloudRegion.EU.getProductionUri().toString());

    showBatchFixSuggestionRequestHandler.handle(request, response, context);

    verify(eventPublisher, times(1)).publishEvent(any(FixSuggestionReceivedEvent.class));
  }

  @Test
  void should_extract_query_from_sc_request_without_token() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/batch/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&branch=branch" +
      "&organizationKey=sample-organization");
    request.setEntity(new StringEntity("""
      {
        "fileEdits": [
          {
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
          {
            "path": "src/main/java/Other.java",
            "changes": [{
              "beforeLineRange": {
                "startLine": 5,
                "endLine": 8
              },
              "before": "old code",
              "after": "new code"
            }]
          }
        ],
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var params = Map.of(
      "project", "org.sonarsource.sonarlint.core:sonarlint-core-parent",
      "issue", "AX2VL6pgAvx3iwyNtLyr",
      "branch", "branch",
      "organizationKey", "sample-organization"
    );

    var showBatchFixSuggestionQuery = showBatchFixSuggestionRequestHandler.extractQuery(request, SonarCloudRegion.EU.getProductionUri().toString(), params);

    assertThat(showBatchFixSuggestionQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(showBatchFixSuggestionQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(showBatchFixSuggestionQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(showBatchFixSuggestionQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(showBatchFixSuggestionQuery.getBranch()).isEqualTo("branch");
    assertThat(showBatchFixSuggestionQuery.getTokenName()).isNull();
    assertThat(showBatchFixSuggestionQuery.getTokenValue()).isNull();
    assertThat(showBatchFixSuggestionQuery.getBatchFixSuggestion().suggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
    assertThat(showBatchFixSuggestionQuery.getBatchFixSuggestion().explanation()).isEqualTo("Modifying the variable name is good");
    assertThat(showBatchFixSuggestionQuery.getBatchFixSuggestion().fileEdits()).hasSize(2);
    assertThat(showBatchFixSuggestionQuery.getBatchFixSuggestion().fileEdits().get(0).path()).isEqualTo("src/main/java/Main.java");
    assertThat(showBatchFixSuggestionQuery.getBatchFixSuggestion().fileEdits().get(1).path()).isEqualTo("src/main/java/Other.java");
  }

  @Test
  void should_extract_query_from_sc_request_with_token() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/batch/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.setEntity(new StringEntity("""
      {
        "fileEdits": [
          {
            "path": "src/main/java/Main.java",
            "changes": [{
              "beforeLineRange": {
                "startLine": 0,
                "endLine": 1
              },
              "before": "",
              "after": "var fix = 1;"
            }]
          }
        ],
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var params = Map.of(
      "project", "org.sonarsource.sonarlint.core:sonarlint-core-parent",
      "issue", "AX2VL6pgAvx3iwyNtLyr",
      "tokenName", "abc",
      "organizationKey", "sample-organization",
      "tokenValue", "123");

    var showBatchFixSuggestionQuery = showBatchFixSuggestionRequestHandler.extractQuery(request, SonarCloudRegion.EU.getProductionUri().toString(), params);

    assertThat(showBatchFixSuggestionQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(showBatchFixSuggestionQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(showBatchFixSuggestionQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(showBatchFixSuggestionQuery.getTokenName()).isEqualTo("abc");
    assertThat(showBatchFixSuggestionQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(showBatchFixSuggestionQuery.getTokenValue()).isEqualTo("123");
    assertThat(showBatchFixSuggestionQuery.getBatchFixSuggestion().suggestionId()).isEqualTo("eb93b2b4-f7b0-4b5c-9460-50893968c264");
  }

  @Test
  void should_validate_batch_fix_suggestion_query_for_sc() {
    assertThat(new ShowBatchFixSuggestionRequestHandler.ShowBatchFixSuggestionQuery(null, "project", "issue", "branch", "name", "value",
      "organizationKey", true, generateBatchFixSuggestionPayload()).isValid()).isTrue();
    assertThat(
      new ShowBatchFixSuggestionRequestHandler.ShowBatchFixSuggestionQuery(null, "project", "issue", "branch", "name", "value", null, true, generateBatchFixSuggestionPayload()).isValid())
        .isFalse();
  }

  @Test
  void should_validate_batch_fix_suggestion_with_empty_file_edits() {
    var emptyPayload = new ShowBatchFixSuggestionRequestHandler.BatchFixSuggestionPayload(
      List.of(),
      "suggestionId",
      "explanation");
    assertThat(new ShowBatchFixSuggestionRequestHandler.ShowBatchFixSuggestionQuery(null, "project", "issue", "branch", "name", "value",
      "organizationKey", true, emptyPayload).isValid()).isFalse();
  }

  @Test
  void should_show_batch_fix_suggestion() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("POST", "/sonarlint/api/fix/batch/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr" +
      "&organizationKey=sample-organization");
    request.setEntity(new StringEntity("""
      {
        "fileEdits": [
          {
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
          {
            "path": "src/main/java/Other.java",
            "changes": [{
              "beforeLineRange": {
                "startLine": 5,
                "endLine": 8
              },
              "before": "old code",
              "after": "new code"
            }]
          }
        ],
        "suggestionId": "eb93b2b4-f7b0-4b5c-9460-50893968c264",
        "explanation": "Modifying the variable name is good"
      }
      """));
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);
    when(context.getAttribute(AttributeUtils.PARAMS_ATTRIBUTE))
      .thenReturn(Map.of(
        "project", "org.sonarsource.sonarlint.core:sonarlint-core-parent",
        "issue", "AX2VL6pgAvx3iwyNtLyr",
        "branch", "branch",
        "organizationKey", "sample-organization"
      ));
    when(context.getAttribute(AttributeUtils.ORIGIN_ATTRIBUTE))
      .thenReturn(SonarCloudRegion.EU.getProductionUri().toString());

    when(clientFile1.getUri()).thenReturn(URI.create("file:///src/main/java/Main.java"));
    when(clientFile2.getUri()).thenReturn(URI.create("file:///src/main/java/Other.java"));
    when(filePathTranslation.serverToIdePath(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(connectionConfigurationRepository.findByOrganization(any())).thenReturn(List.of(
      new SonarCloudConnectionConfiguration(SonarCloudRegion.EU.getProductionUri(), SonarCloudRegion.EU.getApiProductionUri(), "name", "organizationKey", SonarCloudRegion.EU,
        false)));
    when(configurationRepository.getBoundScopesToConnectionAndSonarProject(any(), any())).thenReturn(List.of(new BoundScope("configScope", "connectionId", "projectKey")));

    showBatchFixSuggestionRequestHandler.handle(request, response, context);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(sonarLintRpcClient).showBatchFixSuggestion(any()));
  }

  private static ShowBatchFixSuggestionRequestHandler.BatchFixSuggestionPayload generateBatchFixSuggestionPayload() {
    return new ShowBatchFixSuggestionRequestHandler.BatchFixSuggestionPayload(
      List.of(
        new ShowBatchFixSuggestionRequestHandler.FileEditPayload(
          List.of(new ShowBatchFixSuggestionRequestHandler.ChangesPayload(
            new ShowBatchFixSuggestionRequestHandler.TextRangePayload(0, 1),
            "before",
            "after")),
          "path1"),
        new ShowBatchFixSuggestionRequestHandler.FileEditPayload(
          List.of(new ShowBatchFixSuggestionRequestHandler.ChangesPayload(
            new ShowBatchFixSuggestionRequestHandler.TextRangePayload(5, 10),
            "before2",
            "after2")),
          "path2")),
      "suggestionId",
      "explanation");
  }

}
