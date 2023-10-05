/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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


import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.BindingSuggestionProviderImpl;
import org.sonarsource.sonarlint.core.ConfigurationServiceImpl;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShowIssueRequestHandlerTest {

  @Test
  void should_transform_ServerIssueDetail_to_ShowIssueParams() {
    var repository = mock(ConnectionConfigurationRepository.class);
    var configurationService = mock(ConfigurationServiceImpl.class);
    var bindingSuggestionProvider = mock(BindingSuggestionProviderImpl.class);
    var serverApiProvider = mock(ServerApiProvider.class);
    var telemetryService = mock(TelemetryServiceImpl.class);
    var sonarLintClient = mock(SonarLintClient.class);
    var serverApi = mock(ServerApi.class);
    var issueApi = mock(IssueApi.class);

    var connectionId = "connectionId";
    var configScopeId = "configScopeId";
    var issueKey = "issueKey";
    var issueCreationDate = "2023-05-13T17:55:39+0200";
    var issueMessage = "issue message";
    var issuePath = "/home/file.java";
    var issueRuleKey = "javasecurity:S3649";
    var flowLocationPath_1 = "/home/file_1.java";
    var flowLocationPath_2 = "/home/file_2.java";
    var issueTextRange = Common.TextRange.newBuilder().setStartLine(1).setEndLine(2).setStartOffset(3).setEndOffset(4).build();
    var locationTextRange_1 = Common.TextRange.newBuilder().setStartLine(5).setEndLine(5).setStartOffset(10).setEndOffset(20).build();
    var locationTextRange_2 = Common.TextRange.newBuilder().setStartLine(50).setEndLine(50).setStartOffset(42).setEndOffset(52).build();
    var locationMessage_1 = "locationMessage_1";
    var locationComponentKey_1 = "LocationComponentKey_1";
    var locationComponentKey_2 = "LocationComponentKey_2";
    var locationCodeSnippet_1 = "//todo comment";
    var issueComponentKey = "IssueComponentKey";
    var codeSnippet = "//todo remove this";

    when(serverApiProvider.getServerApi(any())).thenReturn(Optional.of(serverApi));
    when(serverApi.issue()).thenReturn(issueApi);
    when(issueApi.getCodeSnippet(eq(locationComponentKey_1), any())).thenReturn(Optional.of(locationCodeSnippet_1));

    var showIssueRequestHandler = new ShowIssueRequestHandler(sonarLintClient, repository, configurationService,
      bindingSuggestionProvider, serverApiProvider, telemetryService);

    var flow = Common.Flow.newBuilder()
      .addLocations(Common.Location.newBuilder().setTextRange(locationTextRange_1).setComponent(locationComponentKey_1).setMsg(locationMessage_1))
      .addLocations(Common.Location.newBuilder().setTextRange(locationTextRange_2).setComponent(locationComponentKey_2))
      .build();
    var issue = Issues.Issue.newBuilder()
      .setKey(issueKey)
      .setCreationDate(issueCreationDate)
      .setRule(issueRuleKey)
      .setMessage(issueMessage)
      .setTextRange(issueTextRange)
      .setComponent(issueComponentKey)
      .addFlows(flow)
      .build();
    var components = List.of(
      Issues.Component.newBuilder().setKey(issueComponentKey).setPath(issuePath).build(),
      Issues.Component.newBuilder().setKey(locationComponentKey_1).setPath(flowLocationPath_1).build(),
      Issues.Component.newBuilder().setKey(locationComponentKey_2).setPath(flowLocationPath_2).build()
    );
    var serverIssueDetails = new IssueApi.ServerIssueDetails(issue, issuePath, components, codeSnippet);

    var showIssueParams = showIssueRequestHandler.getShowIssueParams(serverIssueDetails, connectionId, configScopeId);
    assertThat(showIssueParams.getConfigScopeId()).isEqualTo(configScopeId);
    assertThat(showIssueParams.getIssueKey()).isEqualTo(issueKey);
    assertThat(showIssueParams.getCreationDate()).isEqualTo(issueCreationDate);
    assertThat(showIssueParams.getRuleKey()).isEqualTo(issueRuleKey);
    assertThat(showIssueParams.isTaint()).isTrue();
    assertThat(showIssueParams.getMessage()).isEqualTo(issueMessage);
    assertThat(showIssueParams.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(showIssueParams.getTextRange().getEndLine()).isEqualTo(2);
    assertThat(showIssueParams.getTextRange().getStartLineOffset()).isEqualTo(3);
    assertThat(showIssueParams.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(showIssueParams.getServerRelativeFilePath()).isEqualTo(issuePath);
    assertThat(showIssueParams.getFlows()).hasSize(1);
    assertThat(showIssueParams.getCodeSnippet()).isEqualTo(codeSnippet);

    var locations = showIssueParams.getFlows().get(0).getLocations();
    assertThat(locations).hasSize(2);
    assertThat(locations.get(0).getTextRange().getStartLine()).isEqualTo(5);
    assertThat(locations.get(0).getTextRange().getEndLine()).isEqualTo(5);
    assertThat(locations.get(0).getTextRange().getStartLineOffset()).isEqualTo(10);
    assertThat(locations.get(0).getTextRange().getEndLineOffset()).isEqualTo(20);
    assertThat(locations.get(0).getFilePath()).isEqualTo(flowLocationPath_1);
    assertThat(locations.get(0).getMessage()).isEqualTo(locationMessage_1);
    assertThat(locations.get(0).getCodeSnippet()).isEqualTo(locationCodeSnippet_1);
    assertThat(locations.get(1).getFilePath()).isEqualTo(flowLocationPath_2);
    assertThat(locations.get(1).getCodeSnippet()).isEmpty();
  }

  @Test
  void should_extract_query_from_request() {
    var issueQuery = ShowIssueRequestHandler.extractQuery(new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show?server=" +
      "https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube&project=org.sonarsource.sonarlint" +
      ".core%3Asonarlint-core-parent&issue=AX2VL6pgAvx3iwyNtLyr"));
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://next.sonarqube.com/sonarqube");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
  }

  @Test
  void should_validate_issue_query() {
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue").isValid()).isTrue();

    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("", "project", "issue").isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "", "issue").isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "").isValid()).isFalse();
  }

  @Test
  void should_assist_creating_connection_if_non_available () {
    var connectionId = "newConnectionId";
    var serverUrl = "wrong url";
    var repository = mock(ConnectionConfigurationRepository.class);
    var configurationService = mock(ConfigurationServiceImpl.class);
    var bindingSuggestionProvider = mock(BindingSuggestionProviderImpl.class);
    var serverApiProvider = mock(ServerApiProvider.class);
    var telemetryService = mock(TelemetryServiceImpl.class);
    var sonarLintClient = mock(SonarLintClient.class);

    when(sonarLintClient.assistCreatingConnection(any())).thenReturn(CompletableFuture.completedFuture(new AssistCreatingConnectionResponse(connectionId)));
    when(sonarLintClient.assistBinding(any())).thenReturn(CompletableFuture.completedFuture(new AssistBindingResponse("configScopeId")));

    var showIssueRequestHandler = new ShowIssueRequestHandler(sonarLintClient, repository, configurationService,
      bindingSuggestionProvider, serverApiProvider, telemetryService);

    showIssueRequestHandler.showIssue(new ShowIssueRequestHandler.ShowIssueQuery(serverUrl, "projectKey", "myIssueKey"));
    verify(sonarLintClient, times(1)).assistCreatingConnection(argThat(assistCreatingConnectionParams -> assistCreatingConnectionParams.getServerUrl().equals(serverUrl)));
    verify(sonarLintClient, times(1)).assistBinding(argThat(assistBindingParams -> assistBindingParams.getConnectionId().equals(connectionId)));
    verify(bindingSuggestionProvider, times(1)).enable();
    verify(bindingSuggestionProvider, times(1)).disable();
  }


  @Test
  void should_detect_taint_issues(){
    assertThat(ShowIssueRequestHandler.isIssueTaint("java:S1144")).isFalse();
    assertThat(ShowIssueRequestHandler.isIssueTaint("javasecurity:S3649")).isTrue();
  }
}
