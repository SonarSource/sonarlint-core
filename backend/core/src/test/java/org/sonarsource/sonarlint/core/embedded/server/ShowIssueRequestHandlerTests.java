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
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
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
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.connection.ServerConnection;
import org.sonarsource.sonarlint.core.file.FilePathTranslation;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranchesStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.SonarProjectBranchesSynchronizationService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static testutils.TestUtils.mockServerApiProvider;

class ShowIssueRequestHandlerTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);
  private ConnectionConfigurationRepository connectionConfigurationRepository;
  private ConfigurationRepository configurationRepository;
  private SonarLintRpcClient sonarLintRpcClient;
  private ShowIssueRequestHandler showIssueRequestHandler;
  private ProjectBranchesStorage branchesStorage;
  private IssueApi issueApi;
  private TelemetryService telemetryService;
  @BeforeEach
  void setup() {
    connectionConfigurationRepository = mock(ConnectionConfigurationRepository.class);
    configurationRepository = mock(ConfigurationRepository.class);
    var bindingSuggestionProvider = mock(BindingSuggestionProvider.class);
    var bindingCandidatesFinder = mock(BindingCandidatesFinder.class);
    sonarLintRpcClient = mock(SonarLintRpcClient.class);
    var filePathTranslation = mock(FilePathTranslation.class);
    var pathTranslationService = mock(PathTranslationService.class);
    when(pathTranslationService.getOrComputePathTranslation(any())).thenReturn(Optional.of(filePathTranslation));
    var userTokenService = mock(UserTokenService.class);
    var sonarCloudActiveEnvironment = SonarCloudActiveEnvironment.prod();
    telemetryService = mock(TelemetryService.class);
    issueApi = mock(IssueApi.class);
    var serverApi = mock(ServerApi.class);
    when(serverApi.issue()).thenReturn(issueApi);
    var connection = new ServerConnection("connectionId", serverApi, sonarLintRpcClient);
    var serverApiProvider = mockServerApiProvider();
    doReturn(Optional.of(connection)).when(serverApiProvider).tryGetConnection(any());
    doReturn(connection).when(serverApiProvider).getConnectionOrThrow(any());
    doReturn(Optional.of(serverApi)).when(serverApiProvider).getServerApi(any());
    branchesStorage = mock(ProjectBranchesStorage.class);
    var storageService = mock(StorageService.class);
    var sonarStorage = mock(SonarProjectStorage.class);
    var eventPublisher = mock(ApplicationEventPublisher.class);
    var sonarProjectBranchesSynchronizationService = spy(new SonarProjectBranchesSynchronizationService(storageService, serverApiProvider, eventPublisher));
    doReturn(new ProjectBranches(Set.of(), "main")).when(sonarProjectBranchesSynchronizationService).getProjectBranches(any(), any(),
      any());
    when(storageService.binding(any())).thenReturn(sonarStorage);
    when(sonarStorage.branches()).thenReturn(branchesStorage);
    var connectionConfiguration = mock(ConnectionConfigurationRepository.class);
    when(connectionConfiguration.hasConnectionWithOrigin(SonarCloudRegion.EU.getProductionUri().toString())).thenReturn(true);

    showIssueRequestHandler = spy(new ShowIssueRequestHandler(sonarLintRpcClient, serverApiProvider, telemetryService,
      new RequestHandlerBindingAssistant(bindingSuggestionProvider, bindingCandidatesFinder, sonarLintRpcClient,
        connectionConfigurationRepository, configurationRepository, userTokenService,
        sonarCloudActiveEnvironment, connectionConfiguration), pathTranslationService, sonarCloudActiveEnvironment, sonarProjectBranchesSynchronizationService));
  }

  @Test
  void should_transform_ServerIssueDetail_to_ShowIssueParams() {
    var connectionId = "connectionId";
    var configScopeId = "configScopeId";
    var issueKey = "issueKey";
    var issueCreationDate = "2023-05-13T17:55:39+0200";
    var issueMessage = "issue message";
    var issuePath = Paths.get("home/file.java");
    var issueRuleKey = "javasecurity:S3649";
    var flowLocationPath1 = "home/file_1.java";
    var flowLocationPath2 = "home/file_2.java";
    var issueTextRange = Common.TextRange.newBuilder().setStartLine(1).setEndLine(2).setStartOffset(3).setEndOffset(4).build();
    var locationTextRange1 = Common.TextRange.newBuilder().setStartLine(5).setEndLine(5).setStartOffset(10).setEndOffset(20).build();
    var locationTextRange2 = Common.TextRange.newBuilder().setStartLine(50).setEndLine(50).setStartOffset(42).setEndOffset(52).build();
    var locationMessage1 = "locationMessage_1";
    var locationComponentKey1 = "LocationComponentKey_1";
    var locationComponentKey2 = "LocationComponentKey_2";
    var locationCodeSnippet1 = "//todo comment";
    var issueComponentKey = "IssueComponentKey";
    var codeSnippet = "//todo remove this";

    when(issueApi.getCodeSnippet(eq(locationComponentKey1), any(), any(), any(), any())).thenReturn(Optional.of(locationCodeSnippet1));

    var flow = Common.Flow.newBuilder()
      .addLocations(Common.Location.newBuilder().setTextRange(locationTextRange1).setComponent(locationComponentKey1).setMsg(locationMessage1))
      .addLocations(Common.Location.newBuilder().setTextRange(locationTextRange2).setComponent(locationComponentKey2))
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
      Issues.Component.newBuilder().setKey(locationComponentKey1).setPath(flowLocationPath1).build(),
      Issues.Component.newBuilder().setKey(locationComponentKey2).setPath(flowLocationPath2).build());
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
    assertThat(locations.get(0).getMessage()).isEqualTo(locationMessage1);
    assertThat(locations.get(0).getCodeSnippet()).isEqualTo(locationCodeSnippet1);
    assertThat(locations.get(1).getIdeFilePath()).isEqualTo(Paths.get("ide/file_2.java"));
    assertThat(locations.get(1).getCodeSnippet()).isEmpty();
  }

  @Test
  void should_trigger_telemetry() throws HttpException, IOException, URISyntaxException {
    var request = mock(ClassicHttpRequest.class);
    when(request.getUri()).thenReturn(URI.create("http://localhost:8000/issue?project=pk&issue=ik&branch=b&server=s"));
    when(request.getHeader("Origin")).thenReturn(new BasicHeader("Origin", "s"));
    when(request.getMethod()).thenReturn(Method.GET.name());
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);
    when(issueApi.getCodeSnippet(eq("comp"), any(), any(), any(), any())).thenReturn(Optional.of("snippet"));

    showIssueRequestHandler.handle(request, response, context);

    verify(telemetryService).showIssueRequestReceived();
    verifyNoMoreInteractions(telemetryService);
  }

  @Test
  void should_extract_query_from_sq_request_with_branch() throws ProtocolException {
    var issueQuery = showIssueRequestHandler.extractQuery(new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123" +
      "&branch=branch"));
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://next.sonarqube.com/sonarqube");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getTokenName()).isEqualTo("abc");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenValue()).isEqualTo("123");
    assertThat(issueQuery.getBranch()).isEqualTo("branch");
  }

  @Test
  void should_extract_query_from_sq_request_without_token() throws ProtocolException {
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
    assertThat(issueQuery.getBranch()).isNull();
  }

  @Test
  void should_extract_query_from_sq_request_with_token() throws ProtocolException {
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
    assertThat(issueQuery.getBranch()).isNull();
  }

  @Test
  void should_extract_query_from_sc_request_without_token() throws ProtocolException {
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr" +
      "&organizationKey=sample-organization");
    request.addHeader("Origin", SonarCloudRegion.EU.getProductionUri());
    var issueQuery = showIssueRequestHandler.extractQuery(request);
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenName()).isNull();
    assertThat(issueQuery.getTokenValue()).isNull();
    assertThat(issueQuery.getBranch()).isNull();
  }

  @Test
  void should_extract_query_from_sc_request_with_token() throws ProtocolException {
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.addHeader("Origin", SonarCloudRegion.EU.getProductionUri());
    var issueQuery = showIssueRequestHandler.extractQuery(request);
    assertThat(issueQuery.getServerUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(issueQuery.getProjectKey()).isEqualTo("org.sonarsource.sonarlint.core:sonarlint-core-parent");
    assertThat(issueQuery.getIssueKey()).isEqualTo("AX2VL6pgAvx3iwyNtLyr");
    assertThat(issueQuery.getTokenName()).isEqualTo("abc");
    assertThat(issueQuery.getOrganizationKey()).isEqualTo("sample-organization");
    assertThat(issueQuery.getTokenValue()).isEqualTo("123");
    assertThat(issueQuery.getBranch()).isNull();
  }

  @Test
  void should_validate_issue_query_for_sq() {
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", null, null, null,
      SonarCloudRegion.US, false).isValid()).isTrue();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "", "pullRequest", null, null, null, SonarCloudRegion.EU, false).isValid()).isTrue();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", null, "pullRequest", null, null, null, SonarCloudRegion.EU, false).isValid()).isTrue();

    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("", "project", "issue", "branch", "pullRequest", null, null, null, SonarCloudRegion.EU, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "", "issue", "branch", "pullRequest", null, null, null, SonarCloudRegion.EU, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "", "branch", "pullRequest", null, null, null, SonarCloudRegion.EU, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "", "", null, null, null, SonarCloudRegion.EU, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", null, null, null, null, SonarCloudRegion.EU, false).isValid()).isTrue();

    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "name", null, null,
      SonarCloudRegion.US, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", null, "value", null,
      SonarCloudRegion.US, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "name", "", null,
      SonarCloudRegion.US, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "", "value", null,
      SonarCloudRegion.US, false).isValid()).isFalse();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery("serverUrl", "project", "issue", "branch", "pullRequest", "name", "value", null
      , SonarCloudRegion.US, false).isValid()).isTrue();
  }

  @Test
  void should_validate_issue_query_for_sc() {
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery(null, "project", "issue", "branch", "pullRequest", "name", "value",
      "organizationKey", SonarCloudRegion.US, true).isValid()).isTrue();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery(null, "project", "issue", "", "pullRequest", "name", "value", "organizationKey"
      , SonarCloudRegion.US, true).isValid()).isTrue();
    assertThat(new ShowIssueRequestHandler.ShowIssueQuery(null, "project", "issue", null, "pullRequest", "name", "value",
      "organizationKey", SonarCloudRegion.US, true).isValid()).isTrue();

    assertThat(new ShowIssueRequestHandler.ShowIssueQuery(null, "project", "issue", "branch", "pullRequest", "name", "value", null, SonarCloudRegion.EU, true).isValid()).isFalse();
  }

  @Test
  void should_detect_taint_issues() {
    assertThat(ShowIssueRequestHandler.isIssueTaint("java:S1144")).isFalse();
    assertThat(ShowIssueRequestHandler.isIssueTaint("javasecurity:S3649")).isTrue();
  }

  @Test
  void should_cancel_flow_when_branch_does_not_match() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123" +
      "&branch=branch");
    request.addHeader("Origin", SonarCloudRegion.EU.getProductionUri());
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    when(connectionConfigurationRepository.findByOrganization(any())).thenReturn(List.of(
      new SonarCloudConnectionConfiguration(SonarCloudRegion.EU.getProductionUri(), SonarCloudRegion.EU.getApiProductionUri(), "name", "organizationKey", SonarCloudRegion.EU, false)));
    when(configurationRepository.getBoundScopesToConnectionAndSonarProject(any(), any())).thenReturn(List.of(new BoundScope("configScope"
      , "connectionId", "projectKey")));
    when(sonarLintRpcClient.matchProjectBranch(any())).thenReturn(CompletableFuture.completedFuture(new MatchProjectBranchResponse(false)));

    showIssueRequestHandler.handle(request, response, context);
    var showMessageArgumentCaptor = ArgumentCaptor.forClass(ShowMessageParams.class);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(sonarLintRpcClient).showMessage(showMessageArgumentCaptor.capture()));
    assertThat(showMessageArgumentCaptor.getValue().getType()).isEqualTo(MessageType.ERROR);
    assertThat(showMessageArgumentCaptor.getValue().getText()).isEqualTo("Attempted to show an issue from branch 'branch', " +
      "which is different from the currently checked-out branch.\nPlease switch to the correct branch and try again.");
    verify(sonarLintRpcClient).matchProjectBranch(any());
    verifyNoMoreInteractions(sonarLintRpcClient);
  }

  @Test
  void should_find_main_branch_when_branch_is_not_provided() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.addHeader("Origin", SonarCloudRegion.EU.getProductionUri());
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    when(connectionConfigurationRepository.findByOrganization(any())).thenReturn(List.of(
      new SonarCloudConnectionConfiguration(SonarCloudRegion.EU.getProductionUri(), SonarCloudRegion.EU.getApiProductionUri(), "name", "organizationKey", SonarCloudRegion.EU, false)));
    when(configurationRepository.getBoundScopesToConnectionAndSonarProject(any(), any())).thenReturn(List.of(new BoundScope("configScope"
      , "connectionId", "projectKey")));
    when(sonarLintRpcClient.matchProjectBranch(any())).thenReturn(CompletableFuture.completedFuture(new MatchProjectBranchResponse(true)));
    when(branchesStorage.exists()).thenReturn(true);
    when(branchesStorage.read()).thenReturn(new ProjectBranches(Set.of(), "main"));
    var serverIssueDetails = mock(IssueApi.ServerIssueDetails.class);
    when(issueApi.fetchServerIssue(any(), any(), any(), any(), any())).thenReturn(Optional.of(serverIssueDetails));
    var issueDetails = mock(IssueDetailsDto.class);
    doReturn(new ShowIssueParams("configScope", issueDetails)).when(showIssueRequestHandler).getShowIssueParams(any(), any(), any(),
      any(), any(), any(), any());

    showIssueRequestHandler.handle(request, response, context);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(sonarLintRpcClient).showIssue(any()));
  }

  @Test
  void should_find_main_branch_when_not_provided_and_not_stored() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    request.addHeader("Origin", SonarCloudRegion.EU.getProductionUri());
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    when(connectionConfigurationRepository.findByOrganization(any())).thenReturn(List.of(
      new SonarCloudConnectionConfiguration(SonarCloudRegion.EU.getProductionUri(), SonarCloudRegion.EU.getApiProductionUri(), "name", "organizationKey", SonarCloudRegion.EU, false)));
    when(configurationRepository.getBoundScopesToConnectionAndSonarProject(any(), any())).thenReturn(List.of(new BoundScope("configScope"
      , "connectionId", "projectKey")));
    when(sonarLintRpcClient.matchProjectBranch(any())).thenReturn(CompletableFuture.completedFuture(new MatchProjectBranchResponse(true)));
    when(branchesStorage.exists()).thenReturn(false);
    var serverIssueDetails = mock(IssueApi.ServerIssueDetails.class);
    when(issueApi.fetchServerIssue(any(), any(), any(), any(), any())).thenReturn(Optional.of(serverIssueDetails));
    var issueDetails = mock(IssueDetailsDto.class);
    doReturn(new ShowIssueParams("configScope", issueDetails)).when(showIssueRequestHandler).getShowIssueParams(any(), any(), any(),
      any(), any(), any(), any());

    showIssueRequestHandler.handle(request, response, context);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(sonarLintRpcClient).showIssue(any()));
  }

  @Test
  void should_verify_missing_origin() throws HttpException, IOException {
    var request = new BasicClassicHttpRequest("GET", "/sonarlint/api/issues/show" +
      "?server=https%3A%2F%2Fnext.sonarqube.com%2Fsonarqube" +
      "&project=org.sonarsource.sonarlint.core%3Asonarlint-core-parent" +
      "&issue=AX2VL6pgAvx3iwyNtLyr&tokenName=abc" +
      "&organizationKey=sample-organization" +
      "&tokenValue=123");
    var response = mock(ClassicHttpResponse.class);
    var context = mock(HttpContext.class);

    showIssueRequestHandler.handle(request, response, context);

    verifyNoMoreInteractions(sonarLintRpcClient);
  }

}
