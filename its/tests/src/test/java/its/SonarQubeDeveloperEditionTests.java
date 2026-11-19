/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) SonarSource Sàrl
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
package its;

import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.AnalysisUtils;
import its.utils.PluginLocator;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.issues.DoTransitionRequest;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static its.utils.AnalysisUtils.analyzeAndAwaitIssues;
import static its.utils.AnalysisUtils.analyzeFile;
import static its.utils.AnalysisUtils.analyzeUntilIssuesAreRaised;
import static its.utils.AnalysisUtils.registerFiles;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.CS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.HTML;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.XML;

class SonarQubeDeveloperEditionTests extends AbstractSonarQubeDeveloperEditionTests {

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = newDeveloperEditionOrchestrator();

  @BeforeAll
  static void start() throws IOException {
    startBackend(ORCHESTRATOR);
  }

  @Nested
  class CSharp {

    @TempDir
    private Path csharpUserHome;

    private final Map<String, Boolean> csharpAnalysisReadinessByConfigScopeId = new ConcurrentHashMap<>();
    private MockSonarLintRpcClientDelegate csharpClient;
    private SonarLintRpcServer csharpBackend;

    @BeforeEach
    void startCSharpBackend() throws IOException {
      var clientToServerOutputStream = new PipedOutputStream();
      var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

      var serverToClientOutputStream = new PipedOutputStream();
      var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

      new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
      csharpClient = newCSharpClient();
      var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, csharpClient);

      csharpBackend = clientLauncher.getServerProxy();
      try {
        csharpBackend.initialize(
          new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null,
            Set.of(BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.PROJECT_SYNCHRONIZATION),
            csharpUserHome.resolve("storage"),
            csharpUserHome.resolve("work"),
            emptySet(), Map.of("omnisharp", PluginLocator.getOmnisharpPluginPath()),
            Set.of(CS), emptySet(), emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), false)),
            emptyList(),
            csharpUserHome.toString(),
            Map.of(), false, null, false, null))
          .get();
      } catch (Exception e) {
        throw new IllegalStateException("Cannot initialize the backend", e);
      }
    }

    @AfterEach
    void stopCSharpBackend() throws ExecutionException, InterruptedException {
      csharpBackend.shutdown().get();
    }

    @Test
    void shouldRaiseIssuesOnACSharpProject() throws IOException {
      var configScopeId = "shouldRaiseIssuesOnACSharpProject";
      var projectKey = "sample-csharp";
      provisionProject(ORCHESTRATOR, projectKey, "Sample C#");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/csharp-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cs", "SonarLint IT C#");

      csharpBackend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(configScopeId, null, true, "My " + configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertReadyForAnalysis(configScopeId));

      var baseDir = Path.of("projects", "sample-csharp").toRealPath();
      registerFiles(csharpBackend, configScopeId, baseDir, "Foo.cs", "Enterprise.cs", "Foo.csproj", "Foo.sln");

      var fooIssues = analyzeUntilIssuesAreRaised(csharpBackend, csharpClient, configScopeId, baseDir.resolve("Foo.cs"));
      var enterpriseIssues = analyzeFile(csharpBackend, csharpClient, configScopeId, baseDir.resolve("Enterprise.cs"));

      assertThat(fooIssues)
        .extracting(RaisedIssueDto::getRuleKey, AnalysisUtils::startLine)
        .containsOnly(
          tuple("csharpsquid:S3903", 1),
          tuple("csharpsquid:S2325", 2),
          tuple("csharpsquid:S1481", 3));
      assertThat(enterpriseIssues)
        .extracting(RaisedIssueDto::getRuleKey, AnalysisUtils::startLine)
        .containsOnly(tuple("csharpsquid:S2259", 5));
    }

    private void assertReadyForAnalysis(String configScopeId) {
      assertThat(csharpAnalysisReadinessByConfigScopeId).containsEntry(configScopeId, true);
    }

    private MockSonarLintRpcClientDelegate newCSharpClient() {
      return new MockSonarLintRpcClientDelegate() {

        @Override
        public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) {
          return Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD));
        }

        @Override
        public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
          configurationScopeIds.forEach(id -> csharpAnalysisReadinessByConfigScopeId.put(id, areReadyForAnalysis));
        }

        @Override
        public void log(LogParams params) {
          rpcClientLogs.add(params);
        }
      };
    }
  }

  @Nested
  class ServerSentEvents {

    @Test
    @OnlyOnSonarQube(from = "2025.1")
    void shouldUpdateQualityProfileInLocalStorageWhenProfileChangedOnServer() {
      var configScopeId = "shouldUpdateQualityProfileInLocalStorageWhenProfileChangedOnServer";
      var projectKey = "projectKey-sse";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var qualityProfile = getQualityProfile(adminWsClient, "SonarLint IT Java");
      deactivateRule(adminWsClient, qualityProfile, "java:S106");
      waitAtMost(1, TimeUnit.MINUTES).pollDelay(Duration.ofSeconds(10)).untilAsserted(() -> {
        var rawIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");

        assertThat(rawIssues)
          .extracting(RaisedIssueDto::getRuleKey)
          .containsOnly("java:S2325");
      });
    }

    @Test
    void shouldUpdateIssueInLocalStorageWhenIssueResolvedOnServer() {
      var configScopeId = "shouldUpdateIssueInLocalStorageWhenIssueResolvedOnServer";
      var projectKey = "projectKey-sse2";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      analyzeMavenProject("sample-java", projectKey);

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var issueKey = getIssueKeys(adminWsClient, "java:S106").get(0);
      resolveIssueAsWontFix(adminWsClient, issueKey);

      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        var fooIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");

        assertThat(fooIssues)
          .extracting(RaisedFindingDto::getRuleKey, RaisedFindingDto::isResolved)
          .contains(tuple("java:S106", true));
      });
    }
  }

  @Nested
  class BranchTests {

    @Test
    void should_sync_branches_from_server() throws ExecutionException, InterruptedException {
      var configScopeId = "should_sync_branches_from_server";
      var short_branch = "feature/short_living";
      var long_branch = "branch-1.x";
      var projectKey = "sample-branch";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Branch");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xml-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "xml", "SonarLint IT XML");
      // Use the pattern of long living branches in older SQ versions, else we only have issues on changed files

      // main branch
      analyzeProject("sample-xml", projectKey);
      // short living branch
      analyzeProject("sample-xml", projectKey, "sonar.branch.name", short_branch);
      // long living branch
      analyzeProject("sample-xml", projectKey, "sonar.branch.name", long_branch);

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForSync(configScopeId);

      var sonarProjectBranch = backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId)).get();
      assertThat(sonarProjectBranch.getMatchedSonarProjectBranch()).isEqualTo(MAIN_BRANCH_NAME);

      matchedBranchNameForProject.set(short_branch);
      backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(configScopeId));

      await().untilAsserted(() -> assertThat(backend.getSonarProjectBranchService()
        .getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId))
        .get().getMatchedSonarProjectBranch())
          .isEqualTo(short_branch));

      await().untilAsserted(() -> assertThat(allBranchNamesForProject).contains(MAIN_BRANCH_NAME, short_branch, long_branch));
    }

    @Test
    void should_match_issues_from_branch() {
      var configScopeId = "should_match_issues_from_branch";
      var projectKey = "sample-java";
      var projectName = "my-sample-java";
      var featureBranch = "branch-1.x";

      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");
      analyzeProject(projectKey, projectKey);
      analyzeProject(projectKey, projectKey, "sonar.branch.name", featureBranch);

      var issuesBranch = adminWsClient.issues().search(new SearchRequest().setBranch(featureBranch).setComponentKeys(List.of(projectKey)));
      var issueToMarkFP = issuesBranch.getIssuesList().stream().filter(issue -> issue.getRule().equals("java:S106")).findFirst().orElseThrow();
      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(issueToMarkFP.getKey()).setTransition("falsepositive"));

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var raisedIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");

      assertThat(raisedIssues)
        .extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::isResolved)
        .contains(tuple("java:S106", false));

      didSynchronizeConfigurationScopes.clear();
      matchedBranchNameForProject.set(featureBranch);
      backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(configScopeId));
      await().untilAsserted(() -> assertThat(backend.getSonarProjectBranchService()
        .getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId))
        .get().getMatchedSonarProjectBranch())
          .isEqualTo(featureBranch));
      waitForSync(configScopeId);

      raisedIssues = analyzeAndAwaitIssues(backend, client, configScopeId, Path.of("projects", "sample-java"), "src/main/java/foo/Foo.java");

      assertThat(raisedIssues)
        .extracting(RaisedIssueDto::getRuleKey, RaisedIssueDto::isResolved)
        .contains(tuple("java:S106", true));
    }
  }

  @Nested
  class TaintVulnerabilities {

    private String projectKey;
    private String configScopeId;

    @BeforeEach
    void prepare() {
      var testId = UUID.randomUUID();
      projectKey = "sample-java-taint-" + testId;
      configScopeId = "sample-java-taint-in-ide-" + testId;
      provisionProject(ORCHESTRATOR, projectKey, "Java With Taint Vulnerabilities");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint Taint Java");
    }

    @AfterEach
    void stop() {
      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(configScopeId));
      var request = new PostRequest("api/projects/bulk_delete");
      request.setParam("projects", projectKey);
      try (var response = adminWsClient.wsConnector().call(request)) {
        var code = response.code();
        assertThat(code)
          .withFailMessage(() -> "Failed to delete project '" + projectKey + "', got HTTP " + code
            + ". Leftover taint issues on the server can cause the next run of this test to see duplicated/stale vulnerabilities.")
          .isBetween(200, 399);
      }
    }

    @Test
    void shouldSyncTaintVulnerabilities() throws ExecutionException, InterruptedException {
      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      analyzeMavenProject("sample-java-taint", projectKey);

      var serverIssue = getServerTaintVulnerability();

      var taintVulnerabilities = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(configScopeId, true)).get().getTaintVulnerabilities();

      assertThat(taintVulnerabilities)
        .extracting(TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::getMessage)
        .containsExactly(tuple(serverIssue.getKey(), serverIssue.getMessage()));

      var taintVulnerability = taintVulnerabilities.get(0);
      assertThat(taintVulnerability.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      var serverVersion = ORCHESTRATOR.getServer().version();
      var ruleDescriptionContextKey = serverVersion.isGreaterThanOrEquals(2025, 3) ? "java_jdbc_api" : "java_se";
      assertThat(taintVulnerability.getRuleDescriptionContextKey()).isEqualTo(ruleDescriptionContextKey);
      assertThat(taintVulnerability.getSeverityMode().isRight()).isTrue();
      assertThat(taintVulnerability.getSeverityMode().getRight().getImpacts().get(0)).extracting("softwareQuality", "impactSeverity").containsExactly(SoftwareQuality.SECURITY,
        ImpactSeverity.MEDIUM);
      assertThat(taintVulnerability.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
      assertThat(taintVulnerability.getFlows()).isNotEmpty();
      assertThat(taintVulnerability.isOnNewCode()).isTrue();
      var flow = taintVulnerability.getFlows().get(0);
      assertThat(flow.getLocations()).isNotEmpty();
      assertThat(flow.getLocations().get(0).getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(flow.getLocations().get(flow.getLocations().size() - 1).getTextRange().getHash()).isIn(hash("request.getParameter(\"user\")"),
        hash("request.getParameter(\"pass\")"));
    }

    @Test
    @OnlyOnSonarQube(from = "2025.1")
    void shouldUpdateTaintVulnerabilityInLocalStorageWhenChangedOnServer() throws ExecutionException, InterruptedException {
      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      assertThat(backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(configScopeId)).get().getTaintVulnerabilities()).isEmpty();

      // check TaintVulnerabilityRaised is received
      analyzeMavenProject("sample-java-taint", projectKey);

      var serverIssue = getServerTaintVulnerability();
      var firstTaintChangedEvent = nextTaintChangedEvent();
      assertThat(firstTaintChangedEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getClosedTaintVulnerabilityIds,
          DidChangeTaintVulnerabilitiesParams::getUpdatedTaintVulnerabilities)
        .containsExactly(configScopeId, emptySet(), emptyList());
      assertThat(firstTaintChangedEvent.getAddedTaintVulnerabilities())
        .extracting(TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::getMessage, TaintVulnerabilityDto::isResolved, TaintVulnerabilityDto::getRuleKey,
          TaintVulnerabilityDto::getIdeFilePath, TaintVulnerabilityDto::isOnNewCode)
        .containsExactly(tuple(serverIssue.getKey(), serverIssue.getMessage(), false, "javasecurity:S3649", Paths.get("src/main/java/foo/DbHelper.java"), true));
      assertThat(firstTaintChangedEvent.getAddedTaintVulnerabilities())
        .flatExtracting("flows")
        .flatExtracting("locations")
        .extracting("message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
        .contains(
          // flow 1 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", Paths.get("src/main/java/foo/DbHelper.java"), 11, 35, 11, 64,
            "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", Paths.get("src/main/java/foo/Endpoint.java"), 9, 18, 9, 46, "a2b69949119440a24e900f15c0939c30"),
          // flow 2 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", Paths.get("src/main/java/foo/DbHelper.java"), 11, 35, 11, 64,
            "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", Paths.get("src/main/java/foo/Endpoint.java"), 8, 18, 8, 46, "2ef54227b849e317e7104dc550be8146"));
      var raisedIssueId = firstTaintChangedEvent.getAddedTaintVulnerabilities().get(0).getId();

      var taintIssues = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(configScopeId)).get().getTaintVulnerabilities();
      assertThat(taintIssues)
        .extracting(TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::getMessage, TaintVulnerabilityDto::isResolved, TaintVulnerabilityDto::getRuleKey,
          TaintVulnerabilityDto::getIdeFilePath, TaintVulnerabilityDto::isOnNewCode)
        .containsExactly(tuple(serverIssue.getKey(), serverIssue.getMessage(), false, "javasecurity:S3649", Paths.get("src/main/java/foo/DbHelper.java"), true));
      assertThat(taintIssues)
        .flatExtracting("flows")
        .flatExtracting("locations")
        .extracting("message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
        .contains(
          // flow 1 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", Paths.get("src/main/java/foo/DbHelper.java"), 11, 35, 11, 64,
            "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", Paths.get("src/main/java/foo/Endpoint.java"), 9, 18, 9, 46, "a2b69949119440a24e900f15c0939c30"),
          // flow 2 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", Paths.get("src/main/java/foo/DbHelper.java"), 11, 35, 11, 64,
            "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", Paths.get("src/main/java/foo/Endpoint.java"), 8, 18, 8, 46, "2ef54227b849e317e7104dc550be8146"));

      resolveIssueAsWontFix(adminWsClient, serverIssue.getKey());

      // check IssueChangedEvent is received
      var secondTaintEvent = nextTaintChangedEvent();
      assertThat(secondTaintEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getClosedTaintVulnerabilityIds,
          DidChangeTaintVulnerabilitiesParams::getAddedTaintVulnerabilities)
        .containsExactly(configScopeId, emptySet(), emptyList());
      assertThat(secondTaintEvent.getUpdatedTaintVulnerabilities())
        .extracting(TaintVulnerabilityDto::getId, TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::getMessage, TaintVulnerabilityDto::isResolved)
        .containsExactly(tuple(raisedIssueId, serverIssue.getKey(), serverIssue.getMessage(), true));

      reopenIssue(adminWsClient, serverIssue.getKey());

      // check IssueChangedEvent is received
      var thirdTaintEvent = nextTaintChangedEvent();
      assertThat(thirdTaintEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getClosedTaintVulnerabilityIds,
          DidChangeTaintVulnerabilitiesParams::getAddedTaintVulnerabilities)
        .containsExactly(configScopeId, emptySet(), emptyList());
      assertThat(thirdTaintEvent.getUpdatedTaintVulnerabilities())
        .extracting(TaintVulnerabilityDto::getId, TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::getMessage, TaintVulnerabilityDto::isResolved)
        .containsExactly(tuple(raisedIssueId, serverIssue.getKey(), serverIssue.getMessage(), false));

      // analyze another project under the same project key to close the taint issue
      analyzeMavenProject("sample-java", projectKey);

      // check TaintVulnerabilityClosed is received
      var fourthTaintEvent = nextTaintChangedEvent();
      assertThat(fourthTaintEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getUpdatedTaintVulnerabilities,
          DidChangeTaintVulnerabilitiesParams::getAddedTaintVulnerabilities)
        .containsExactly(configScopeId, emptyList(), emptyList());
      assertThat(fourthTaintEvent.getClosedTaintVulnerabilityIds())
        .containsExactly(raisedIssueId);
      assertThat(taintEventsForCurrentScope()).isEmpty();
    }

    private Issues.Issue getServerTaintVulnerability() {
      var issues = adminWsClient.issues().search(new SearchRequest()
        .setComponentKeys(List.of(projectKey))
        .setRules(List.of("javasecurity:S3649"))).getIssuesList();
      assertThat(issues).hasSize(1);
      return issues.get(0);
    }

    private DidChangeTaintVulnerabilitiesParams nextTaintChangedEvent() {
      waitAtMost(1, TimeUnit.MINUTES).until(() -> !taintEventsForCurrentScope().isEmpty());
      var event = taintEventsForCurrentScope().get(0);
      didChangeTaintVulnerabilitiesEvents.remove(event);
      return event;
    }

    private List<DidChangeTaintVulnerabilitiesParams> taintEventsForCurrentScope() {
      return didChangeTaintVulnerabilitiesEvents.stream()
        .filter(event -> configScopeId.equals(event.getConfigurationScopeId()))
        .toList();
    }
  }

  @Nested
  class RuleDescription {

    @Test
    void shouldFailIfNotAuthenticated() {
      var configScopeId = "shouldFailIfNotAuthenticated";
      var projectKey = "noAuth";
      var projectName = "Sample Javascript";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(configScopeId, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID_WRONG_CREDENTIALS, projectKey, true)))));

      adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
      try {
        var ex = assertThrows(ExecutionException.class,
          () -> backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, javaRuleKey("S106"), null)).get());
        assertThat(ex.getCause()).hasMessage("Could not find rule '" + javaRuleKey("S106") + "' in plugins loaded from '" + CONNECTION_ID_WRONG_CREDENTIALS + "'");
      } finally {
        adminWsClient.settings().reset(new ResetRequest().setKeys(List.of("sonar.forceAuthentication")));
      }
    }

    @Test
    void shouldContainExtendedDescription() throws Exception {
      var configScopeId = "shouldContainExtendedDescription";
      var projectKey = "project-with-extended-description";
      var projectName = "Project With Extended Description";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      var extendedDescription = " = Title\n*my dummy extended description*";

      WsRequest request = new PostRequest("/api/rules/update")
        .setParam("key", javaRuleKey("S106"))
        .setParam("markdown_note", extendedDescription);
      try (var response = adminWsClient.wsConnector().call(request)) {
        assertThat(response.code()).isEqualTo(200);
      }

      String expected;
      if (ORCHESTRATOR.getServer().version().isGreaterThan(7, 9)) {
        expected = "<h1>Title</h1><strong>my dummy extended description</strong>";
      } else {
        // For some reason, there is an extra line break in the generated HTML
        expected = "<h1>Title\n</h1><strong>my dummy extended description</strong>";
      }

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var ruleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId,
        javaRuleKey("S106"), null)).get();
      var ruleDescription = ruleDetailsResponse.details().getDescription();
      if (ORCHESTRATOR.getServer().version().isGreaterThan(10, 1)) {
        var ruleTabs = ruleDescription.getRight().getTabs();
        assertThat(ruleTabs.get(ruleTabs.size() - 1).getContent().getLeft().getHtmlContent()).contains(expected);
      } else {
        // no description sections at that time
        assertThat(ruleDescription.isRight()).isFalse();
      }
    }

    @Test
    void shouldSupportsMarkdownDescription() throws Exception {
      var configScopeId = "shouldSupportsMarkdownDescription";
      var projectKey = "project-with-markdown-description";
      var projectName = "Project With Markdown Description";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-markdown.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Markdown");
      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var ruleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, "mycompany-java:markdown",
        null)).get();

      assertThat(ruleDetailsResponse.details().getDescription().getLeft().getHtmlContent())
        .isEqualTo("<h1>Title</h1><ul><li>one</li>\n"
          + "<li>two</li></ul>");
    }

    @Test
    void shouldReturnAllContextsWithOthersSelectedIfNoContextProvided() throws ExecutionException, InterruptedException {
      var configScopeId = "shouldReturnAllContextsWithOthersSelectedIfNoContextProvided";
      var projectKey = "sample-java-taint-new-backend";

      var projectName = "Java With Taint Vulnerabilities";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, "javasecurity:S2083", null)).get();

      var description = activeRuleDetailsResponse.details().getDescription();
      var serverVersion = ORCHESTRATOR.getServer().version();
      var extendedDescription = description.getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
      var framework = serverVersion.isGreaterThanOrEquals(2025, 3) ? "Java I/O API (java_i_o_api)" : "Java SE (java_se)";

      var link = serverVersion.isGreaterThanOrEquals(2026, 2) ? "OWASP - <a href=\"..."
        : serverVersion.isGreaterThanOrEquals(10, 4) ? " OWASP - <a href=..." : " <a href=\"https:/...";

      assertThat(extendedDescription.getTabs())
        .flatExtracting(this::extractTabContent)
        .containsExactly(
          "Why is this an issue?",
          "<p>Path injections occur when an application us...",
          "How can I fix it?",
          "--> " + framework,
          "    <p>The following code is vulnerable to path inj...",
          "--> Others (others)",
          "    <h4>How can I fix it in another component or fr...",
          "More Info",
          "<h3>Standards</h3>\n"
            + "<ul>\n"
            + "  <li>" + link);

      var howToFixTab = extendedDescription.getTabs().get(1);
      assertThat(howToFixTab.getContent().getRight().getDefaultContextKey()).isEqualTo("others");
    }

    @Test
    void shouldReturnAllContextsWithTheMatchingOneSelectedIfContextProvided() throws ExecutionException, InterruptedException {
      var configScopeId = "shouldReturnAllContextsWithTheMatchingOneSelectedIfContextProvided";
      var projectKey = "sample-java-taint-rule-context-new-backend";
      var projectName = "Java With Taint Vulnerabilities And Multiple Contexts";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, "javasecurity:S5131", "spring")).get();

      var description = activeRuleDetailsResponse.details().getDescription();
      var serverVersion = ORCHESTRATOR.getServer().version();

      var extendedDescription = description.getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent())
        .isEqualTo("<p>This vulnerability makes it possible to temporarily execute JavaScript code in the context of the application, granting access to the session of\n"
          + "the victim. This is possible because user-provided data, such as URL parameters, are copied into the HTML body of the HTTP response that is sent back\n"
          + "to the user.</p>");
      var iterator = extendedDescription.getTabs().iterator();
      iterator.next();

      var documentation = serverVersion.isGreaterThanOrEquals(2026, 2) ? "OWASP - <a hr..."
        : serverVersion.isGreaterThanOrEquals(2025, 3) ? " OWASP - <a h..." : " <a href=\"htt...";

      assertThat(extendedDescription.getTabs())
        .flatExtracting(this::extractTabContent)
        .contains(
          "Why is this an issue?",
          "<p>Reflected cross-site scripting (XSS) occurs ...",
          "How can I fix it?",
          "--> JSP (jsp)",
          "    <p>The following code is vulnerable to cross-si...",
          "--> Servlet (servlet)",
          "    <p>The following code is vulnerable to cross-si...",
          "--> Spring (spring)",
          "    <p>The following code is vulnerable to cross-si...",
          "--> Thymeleaf (thymeleaf)",
          "    <p>The following code is vulnerable to cross-si...",
          "--> Others (others)",
          "    <h4>How can I fix it in another component or fr...",
          "More Info",
          "<h3>Documentation</h3>\n"
            + "<ul>\n"
            + "  <li>" + documentation);

      var howToFixTab = extendedDescription.getTabs().get(1);
      assertThat(howToFixTab.getContent().getRight().getDefaultContextKey()).isEqualTo("spring");
    }

    private List<String> extractTabContent(RuleDescriptionTabDto tab) {
      List<String> result = new ArrayList<>();
      result.add(tab.getTitle());
      if (tab.getContent().isLeft()) {
        result.add(abbreviate(tab.getContent().getLeft().getHtmlContent(), 50));
      } else {
        tab.getContent().getRight().getContextualSections().forEach(s -> {
          result.add("--> " + s.getDisplayName() + " (" + s.getContextKey() + ")");
          result.add("    " + abbreviate(s.getHtmlContent(), 50));
        });
      }
      return result;
    }

  }

  @Nested
  class SonarProjectService {

    @Test
    void getProject() throws ExecutionException, InterruptedException {
      var projectKey = "sample-project";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Project");

      var allProjects = backend.getConnectionService()
        .getAllProjects(
          new GetAllProjectsParams(new TransientSonarQubeConnectionDto(ORCHESTRATOR.getServer().getUrl(), Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD)))))
        .get().getSonarProjects();

      assertThat(allProjects).extracting(SonarProjectDto::getKey).doesNotContain("non-existing");
      assertThat(allProjects).extracting(SonarProjectDto::getKey).contains(projectKey);
    }

    @Test
    void downloadAllProjects() throws ExecutionException, InterruptedException {
      provisionProject(ORCHESTRATOR, "foo-bar1", "Foo1");
      provisionProject(ORCHESTRATOR, "foo-bar2", "Foo2");
      provisionProject(ORCHESTRATOR, "foo-bar3", "Foo3");
      var getAllProjectsParams = new GetAllProjectsParams(new TransientSonarQubeConnectionDto(ORCHESTRATOR.getServer().getUrl(),
        Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD))));
      var allProjectsResponse = backend.getConnectionService().getAllProjects(getAllProjectsParams).get();
      assertThat(allProjectsResponse.getSonarProjects()).extracting(SonarProjectDto::getKey).contains("foo-bar1", "foo-bar2", "foo-bar3");
    }

  }
}
