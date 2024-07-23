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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.BindingCandidatesFinder;
import org.sonarsource.sonarlint.core.BindingSuggestionProvider;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ShowIssueRequestHandlerTests {

  @Test
  void should_transform_ServerIssueDetail_to_ShowIssueParams() {
    var connectionId = "connectionId";
    var configScopeId = "configScopeId";
    var issueKey = "issueKey";
    var issueCreationDate = "2023-05-13T17:55:39+0200";
    var issueMessage = "issue message";
    var issuePath = Paths.get("home/file.java");
    var issueRuleKey = "javasecurity:S3649";
    var flowLocationPath_1 = "home/file_1.java";
    var flowLocationPath_2 = "home/file_2.java";
    var issueTextRange = Common.TextRange.newBuilder().setStartLine(1).setEndLine(2).setStartOffset(3).setEndOffset(4).build();
    var locationTextRange_1 = Common.TextRange.newBuilder().setStartLine(5).setEndLine(5).setStartOffset(10).setEndOffset(20).build();
    var locationTextRange_2 = Common.TextRange.newBuilder().setStartLine(50).setEndLine(50).setStartOffset(42).setEndOffset(52).build();
    var locationMessage_1 = "locationMessage_1";
    var locationComponentKey_1 = "LocationComponentKey_1";
    var locationComponentKey_2 = "LocationComponentKey_2";
    var locationCodeSnippet_1 = "//todo comment";
    var issueComponentKey = "IssueComponentKey";
    var codeSnippet = "//todo remove this";

    var showIssueRequestHandler = getShowIssueRequestHandler(locationComponentKey_1, locationCodeSnippet_1);

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
      Issues.Component.newBuilder().setKey(issueComponentKey).setPath(issuePath.toString()).build(),
      Issues.Component.newBuilder().setKey(locationComponentKey_1).setPath(flowLocationPath_1).build(),
      Issues.Component.newBuilder().setKey(locationComponentKey_2).setPath(flowLocationPath_2).build());
    var serverIssueDetails = new IssueApi.ServerIssueDetails(issue, issuePath, components, codeSnippet);

    var showIssueParams = showIssueRequestHandler.getShowIssueParams(serverIssueDetails, connectionId, configScopeId, "branch", "",
      new FilePathTranslation(Path.of("ide"), Path.of("home")), new SonarLintCancelMonitor());
    assertThat(showIssueParams.getConfigurationScopeId()).isEqualTo(configScopeId);
    var issueDetails = showIssueParams.getIssueDetails();
    assertThat(issueDetails.getIssueKey()).isEqualTo(issueKey);
    assertThat(issueDetails.getCreationDate()).isEqualTo(issueCreationDate);
    assertThat(issueDetails.getRuleKey()).isEqualTo(issueRuleKey);
    assertThat(issueDetails.isTaint()).isTrue();
    assertThat(issueDetails.getMessage()).isEqualTo(issueMessage);
    assertThat(issueDetails.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(issueDetails.getTextRange().getEndLine()).isEqualTo(2);
    assertThat(issueDetails.getTextRange().getStartLineOffset()).isEqualTo(3);
    assertThat(issueDetails.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(issueDetails.getIdeFilePath()).isEqualTo(Paths.get("ide/file.java"));
    assertThat(issueDetails.getFlows()).hasSize(1);
    assertThat(issueDetails.getCodeSnippet()).isEqualTo(codeSnippet);

    var locations = issueDetails.getFlows().get(0).getLocations();
    assertThat(locations).hasSize(2);
    assertThat(locations.get(0).getTextRange().getStartLine()).isEqualTo(5);
    assertThat(locations.get(0).getTextRange().getEndLine()).isEqualTo(5);
    assertThat(locations.get(0).getTextRange().getStartLineOffset()).isEqualTo(10);
    assertThat(locations.get(0).getTextRange().getEndLineOffset()).isEqualTo(20);
    assertThat(locations.get(0).getIdeFilePath()).isEqualTo(Paths.get("ide/file_1.java"));
    assertThat(locations.get(0).getMessage()).isEqualTo(locationMessage_1);
    assertThat(locations.get(0).getCodeSnippet()).isEqualTo(locationCodeSnippet_1);
    assertThat(locations.get(1).getIdeFilePath()).isEqualTo(Paths.get("ide/file_2.java"));
    assertThat(locations.get(1).getCodeSnippet()).isEmpty();
  }

  @Test
  void should_trigger_telemetry() throws HttpException, IOException, URISyntaxException {
    var request = mock(ClassicHttpRequest.class);
    when(request.getUri()).thenReturn(URI.create("http://localhost:8000/issue?project=pk&issue=ik&branch=b&server=s"));
    when(request.getMethod()).thenReturn(Method.GET.name());
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);
    var telemetryService = mock(TelemetryService.class);
    var showIssueRequestHandler = getShowIssueRequestHandler(telemetryService, "comp", "snippet");

    showIssueRequestHandler.handle(request, response, context);

    verify(telemetryService).showIssueRequestReceived();
    verifyNoMoreInteractions(telemetryService);
  }

  @Test
  void should_extract_query_from_sq_request_without_token() throws ProtocolException {
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    var showIssueRequestHandler = new ShowIssueRequestHandler(null, null, null, null, null, sonarCloudActiveEnvironment);
    var issueQuery = showIssueRequestHandler.extractQuery(new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr" +
      "&organizationKey=sample-organization"));
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://next.sonarqube.com/sonarqube");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenName()).isNull();
    assertThat(issueQuery.getTokenValue()).isNull();
  }

  @Test
  void should_extract_query_from_sq_request_with_token() throws ProtocolException {
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    var showIssueRequestHandler = new ShowIssueRequestHandler(null, null, null, null, null, sonarCloudActiveEnvironment);
    var issueQuery = showIssueRequestHandler.extractQuery(new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123"));
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://next.sonarqube.com/sonarqube");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getTokenName()).isEqualTo("abc");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenValue()).isEqualTo("123");
  }

  @Test
  void should_extract_query_from_sc_request_without_token() throws ProtocolException {
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    var showIssueRequestHandler = new ShowIssueRequestHandler(null, null, null, null, null, sonarCloudActiveEnvironment);
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr" +
      "&organizationKey=sample-organization");
    request.addHeader("Origin", SonarCloudActiveEnvironment.PRODUCTION_URI);
    var issueQuery = showIssueRequestHandler.extractQuery(request);
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenName()).isNull();
    assertThat(issueQuery.getTokenValue()).isNull();
  }

  @Test
  void should_extract_query_from_sc_request_with_token() throws ProtocolException {
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    var showIssueRequestHandler = new ShowIssueRequestHandler(null, null, null, null, null, sonarCloudActiveEnvironment);
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.addHeader("Origin", SonarCloudActiveEnvironment.PRODUCTION_URI);
    var issueQuery = showIssueRequestHandler.extractQuery(request);
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getTokenName()).isEqualTo("abc");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenValue()).isEqualTo("123");
  }

  @Test
  void should_validate_issue_query_for_sq() {
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", null, null, null, false).isValid()).isTrue();

    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("", "project", "issue", "branch", "pullRequest", null, null, null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "", "issue", "branch", "pullRequest", null, null, null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "", "branch", "pullRequest", null, null, null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "", "", null, null, null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", null, null, null, null, false).isValid()).isTrue();

    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "name", null, null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", null, "value", null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "name", "", null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "", "value", null, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "name", "value", null, false).isValid()).isTrue();
  }

  @Test
  void should_validate_issue_query_for_sc() {
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery(null, "project", "issue", "branch", "pullRequest", "name", "value", "organizationKey", true).isValid()).isTrue();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery(null, "project", "issue", "branch", "pullRequest", "name", "value", null, true).isValid()).isFalse();
  }

  @Test
  void should_detect_taint_issues() {
    assertThat(ShowIssueRequestHandler.isIssueTaint("java:S1144")).isFalse();
    assertThat(ShowIssueRequestHandler.isIssueTaint("javasecurity:S3649")).isTrue();
  }

  private static ShowIssueRequestHandler getShowIssueRequestHandler(String locationComponentKey, String locationCodeSnippet) {
    var telemetryService = mock(TelemetryService.class);
    return getShowIssueRequestHandler(telemetryService, locationComponentKey, locationCodeSnippet);
  }

  private static ShowIssueRequestHandler getShowIssueRequestHandler(TelemetryService telemetryService, String locationComponentKey, String locationCodeSnippet) {
    var serverApi = mock(ServerApi.class);
    var serverApiProvider = mock(ServerApiProvider.class);
    var issueApi = mock(IssueApi.class);
    when(serverApiProvider.getServerApi(any())).thenReturn(Optional.of(serverApi));
    when(serverApi.issue()).thenReturn(issueApi);
    when(issueApi.getCodeSnippet(eq(locationComponentKey), any(), any(), any(), any())).thenReturn(Optional.of(locationCodeSnippet));
    var repository = mock(ConnectionConfigurationRepository.class);
    var configurationRepository = mock(ConfigurationRepository.class);
    var bindingSuggestionProvider = mock(BindingSuggestionProvider.class);
    var bindingCandidatesFinder = mock(BindingCandidatesFinder.class);

    var sonarLintClient = mock(SonarLintRpcClient.class);
    var pathTranslationService = mock(PathTranslationService.class);
    var userTokenService = mock(UserTokenService.class);
    SonarCloudActiveEnvironment sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    return new ShowIssueRequestHandler(sonarLintClient, serverApiProvider, telemetryService,
      new RequestHandlerBindingAssistant(bindingSuggestionProvider, bindingCandidatesFinder, sonarLintClient, repository, configurationRepository, userTokenService, sonarCloudActiveEnvironment), pathTranslationService, sonarCloudActiveEnvironment);
  }
}
