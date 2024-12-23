/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.OrchestratorUtils;
import its.utils.PluginLocator;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.issues.DoTransitionRequest;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static its.utils.ItUtils.SONAR_VERSION;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.CLOUDFORMATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.COBOL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.DOCKER;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.GO;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.HTML;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.KOTLIN;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.KUBERNETES;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PYTHON;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.RUBY;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.SCALA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.TERRAFORM;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.XML;

class SonarQubeDeveloperEditionTests extends AbstractConnectedTests {

  public static final String CONNECTION_ID = "orchestrator";
  public static final String CONNECTION_ID_WRONG_CREDENTIALS = "wrong-credentials";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .addPlugin(MavenLocation.of("org.sonarsource.sonarqube", "sonar-xoo-plugin", SONAR_VERSION))
    .addPlugin(FileLocation.of("../plugins/global-extension-plugin/target/global-extension-plugin.jar"))
    .addPlugin(FileLocation.of("../plugins/custom-sensor-plugin/target/custom-sensor-plugin.jar"))
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    // Ensure SSE are processed correctly just after SQ startup
    .setServerProperty("sonar.pushevents.polling.initial.delay", "2")
    .setServerProperty("sonar.pushevents.polling.period", "1")
    .setServerProperty("sonar.pushevents.polling.last.timestamp", "1")
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private static WsClient adminWsClient;
  private static SonarLintRpcClientDelegate client;

  @BeforeAll
  static void createSonarLintUser() {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }

  private static SonarLintRpcServer backend;

  private static BackendJsonRpcLauncher serverLauncher;

  @TempDir
  private static Path sonarUserHome;

  private static final List<DidChangeTaintVulnerabilitiesParams> didChangeTaintVulnerabilitiesEvents = new CopyOnWriteArrayList<>();
  private static final List<String> allBranchNamesForProject = new CopyOnWriteArrayList<>();
  private static String matchedBranchNameForProject = null;
  private static final List<String> didSynchronizeConfigurationScopes = new CopyOnWriteArrayList<>();
  private static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();


  @BeforeAll
  static void start() throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    client = spy(newDummySonarLintClient());
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

    backend = clientLauncher.getServerProxy();
    try {
      var languages = Set.of(JAVA, GO, PHP, JS, PYTHON, HTML, RUBY, KOTLIN, SCALA, XML, COBOL, CLOUDFORMATION, DOCKER, KUBERNETES, TERRAFORM);
      var featureFlags = new FeatureFlagsDto(true, true, true, true, true, true, true, true, false, true, false);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null, featureFlags,
            sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            emptySet(), PluginLocator.getEmbeddedPluginsByKeyForTests(),
            languages, emptySet(), emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), false),
              new SonarQubeConnectionConfigurationDto(CONNECTION_ID_WRONG_CREDENTIALS, ORCHESTRATOR.getServer().getUrl(), false)),
            emptyList(),
            sonarUserHome.toString(),
            Map.of(), false, null, false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @BeforeEach
  void clearState() {
    rpcClientLogs.clear();
    didSynchronizeConfigurationScopes.clear();
    analysisReadinessByConfigScopeId.clear();
    allBranchNamesForProject.clear();
    matchedBranchNameForProject = null;
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Nested
  class AnalysisTests {


    @BeforeEach
    void start() {
      Map<String, String> globalProps = new HashMap<>();
      globalProps.put("sonar.global.label", "It works");

      // This profile is altered in a test
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
    }

    @AfterEach
    void stop() {
      adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
      ((MockSonarLintRpcClientDelegate) client).clear();
    }

    // TODO should be moved to a separate class, not related to analysis
    @Test
    void shouldRaiseIssuesOnAJavaScriptProject() {
      var configScopeId = "shouldRaiseIssuesOnAJavaScriptProject";
      var projectKey = "sample-javascript";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Javascript");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/javascript-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "js", "SonarLint IT Javascript");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-javascript", "src/Person.js");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesFromACustomRule() {
      var configScopeId = "shouldRaiseIssuesFromACustomRule";
      var projectKey = "sample-java-custom";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java Custom");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-custom.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Custom");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-java-custom", "src/main/java/foo/Foo.java");

      assertThat(rawIssues).extracting("ruleKey", "textRange")
        .usingRecursiveFieldByFieldElementComparator()
        .containsOnly(tuple("mycompany-java:AvoidAnnotation", new TextRangeDto(12, 3, 12, 19)));
    }

    @Test
    void shouldRaiseIssuesOnAPhpProject() {
      var configScopeId = "shouldRaiseIssuesOnAPhpProject";
      var projectKey = "sample-php";
      provisionProject(ORCHESTRATOR, projectKey, "Sample PHP");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/php-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "php", "SonarLint IT PHP");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-php", "src/Math.php");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAPythonProject() {
      var configScopeId = "shouldRaiseIssuesOnAPythonProject";
      var projectKey = "sample-python";
      var projectName = "Sample Python";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/python-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "SonarLint IT Python");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-python", "src/hello.py");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAHtmlProject() {
      var configScopeId = "shouldRaiseIssuesOnAHtmlProject";
      var projectKey = "sample-web";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Web");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/web-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "web", "SonarLint IT Web");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-web", "src/file.html");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAGoProject() {
      var configScopeId = "shouldRaiseIssuesOnAGoProject";
      var projectKey = "sample-go";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Go");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/go-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "go", "SonarLint IT Go");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-go", "src/sample.go");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void shouldRaiseIssuesOnACloudFormationProject() {
      var configScopeId = "shouldRaiseIssuesOnACloudFormationProject";
      var projectKey = "sample-cloudformation";
      provisionProject(ORCHESTRATOR, projectKey, "Sample CloudFormation");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/cloudformation-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cloudformation", "SonarLint IT CloudFormation");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-cloudformation", "src/sample.yaml");

      assertThat(rawIssues).hasSize(2);
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void shouldRaiseIssuesOnADockerProject() {
      var configScopeId = "shouldRaiseIssuesOnADockerProject";
      var projectKey = "sample-docker";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Docker");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/docker-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "docker", "SonarLint IT Docker");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-docker", "src/Dockerfile");

      assertThat(rawIssues).hasSize(2);
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void shouldRaiseIssuesOnAKubernetesProject() {
      var configScopeId = "shouldRaiseIssuesOnAKubernetesProject";
      var projectKey = "sample-kubernetes";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Kubernetes");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/kubernetes-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "kubernetes", "SonarLint IT Kubernetes");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-kubernetes", "src/sample.yaml");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void shouldRaiseIssuesOnATerraformProject() {
      var configScopeId = "shouldRaiseIssuesOnATerraformProject";
      var projectKey = "sample-terraform";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Terraform");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/terraform-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "terraform", "SonarLint IT Terraform");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-terraform", "src/sample.tf");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "10.4")
    void shouldRaiseDataflowIssuesOnAPythonProject() {
      var configScopeId = "shouldRaiseDataflowIssuesOnAPythonProject";
      var projectKey = "sample-dbd";
      provisionProject(ORCHESTRATOR, projectKey, "Sample DBD");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/dbd-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "SonarLint IT DBD");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForSync(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-dbd", "src/hello.py");

      assertThat(rawIssues)
        .extracting(RawIssueDto::getRuleKey, RawIssueDto::getPrimaryMessage)
        .containsOnly(tuple("pythonbugs:S6466", "Fix this access on a collection that may trigger an 'IndexError'."));
    }

    @Test
    void customSensorsShouldNotBeExecuted() {
      var configScopeId = "customSensorsShouldNotBeExecuted";
      var projectKey = "sample-java-custom-sensor";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java Custom");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/custom-sensor.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Custom Sensor");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-java", "src/main/java/foo/Foo.java");

      assertThat(rawIssues).isEmpty();
    }

    // TODO should be moved to a medium test
    @Test
    void globalExtension() {
      var configScopeId = "globalExtension";
      var projectKey = "sample-global-extension";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Global Extension");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/global-extension.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cobol", "SonarLint IT Global Extension");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-global-extension", "src/foo.glob", "sonar.cobol.file.suffixes", "glob");
      assertThat(rawIssues).extracting("ruleKey", "primaryMessage").containsOnly(
        tuple("global:inc", "Issue number 0"));

      rawIssues = analyzeFile(configScopeId, "sample-global-extension", "src/foo.glob", "sonar.cobol.file.suffixes", "glob");
      assertThat(rawIssues).extracting("ruleKey", "primaryMessage").containsOnly(
        tuple("global:inc", "Issue number 1"));
    }

    @Test
    void shouldRaiseIssuesFromATemplateRule() throws Exception {
      var configScopeId = "shouldRaiseIssuesFromATemplateRule";
      var projectKey = "sample-java-template-rule";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      var qp = getQualityProfile(adminWsClient, "SonarLint IT Java");

      PostRequest request = new PostRequest("/api/rules/create")
        .setParam("params", "methodName=echo;className=foo.Foo;argumentTypes=int")
        .setParam("name", "myrule")
        .setParam("severity", "MAJOR");
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 0)) {
        request.setParam("customKey", "myrule")
          .setParam("markdownDescription", "my_rule_description")
          .setParam("templateKey", javaRuleKey("S2253"));
      } else {
        request.setParam("custom_key", "myrule")
          .setParam("markdown_description", "my_rule_description")
          .setParam("template_key", javaRuleKey("S2253"));
      }

      try (var response = adminWsClient.wsConnector().call(request)) {
        assertTrue(response.isSuccessful());
      }

      request = new PostRequest("/api/qualityprofiles/activate_rule")
        .setParam("key", qp.getKey())
        .setParam("rule", javaRuleKey("myrule"));
      try (var response = adminWsClient.wsConnector().call(request)) {
        assertTrue(response.isSuccessful(), "Unable to activate custom rule");
      }

      try {
        openBoundConfigurationScope(configScopeId, projectKey, true);
        waitForAnalysisToBeReady(configScopeId);

        var rawIssues = analyzeFile(configScopeId, "sample-java", "src/main/java/foo/Foo.java");
        assertThat(rawIssues).hasSize(3);

        var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, javaRuleKey("myrule"), null)).get();
        assertThat(ruleDetails.details().getDescription().getLeft().getHtmlContent()).contains("my_rule_description");
        assertThat(ruleDetails.details().getName()).isEqualTo("myrule");

      } finally {

        request = new PostRequest("/api/rules/delete")
          .setParam("key", javaRuleKey("myrule"));
        try (var response = adminWsClient.wsConnector().call(request)) {
          assertTrue(response.isSuccessful(), "Unable to delete custom rule");
        }
      }
    }

    @Test
    void shouldHonorServerSideSettings() {
      var configScopeId = "shouldHonorServerSideSettings";
      var projectKey = "sample-java-configured";
      var projectName = "Sample Java";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-java", "src/main/java/foo/Foo.java");
      assertThat(rawIssues).hasSize(2);

      rpcClientLogs.clear();
      didSynchronizeConfigurationScopes.clear();
      analysisReadinessByConfigScopeId.clear();
      // Override default file suffixes in global props so that input file is not considered as a Java file
      setSettingsMultiValue(null, "sonar.java.file.suffixes", ".foo");
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, false)));
      await().untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
      await().untilAsserted(() -> assertThat(rpcClientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      rawIssues = analyzeFile(configScopeId, "sample-java", "src/main/java/foo/Foo.java");
      assertThat(rawIssues).isEmpty();

      rpcClientLogs.clear();
      analysisReadinessByConfigScopeId.clear();
      // Override default file suffixes in project props so that input file is considered as a Java file again
      setSettingsMultiValue(projectKey, "sonar.java.file.suffixes", ".java");
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)));
      await().untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
      await().untilAsserted(() -> assertThat(rpcClientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      rawIssues = analyzeFile(configScopeId, "sample-java", "src/main/java/foo/Foo.java");
      assertThat(rawIssues).hasSize(2);
    }

    @Test
    void shouldRaiseIssuesOnARubyProject() {
      var configScopeId = "shouldRaiseIssuesOnARubyProject";
      var projectKey = "sample-ruby";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Ruby");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/ruby-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "ruby", "SonarLint IT Ruby");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-ruby", "src/hello.rb");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAKotlinProject() {
      var configScopeId = "shouldRaiseIssuesOnAKotlinProject";
      var projectKey = "sample-kotlin";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Kotlin");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/kotlin-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "kotlin", "SonarLint IT Kotlin");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-kotlin", "src/hello.kt");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAScalaProject() {
      var configScopeId = "shouldRaiseIssuesOnAScalaProject";
      var projectKey = "sample-scala";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Scala");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/scala-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "scala", "SonarLint IT Scala");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-scala", "src/Hello.scala");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAnXmlProject() {
      var configScopeId = "shouldRaiseIssuesOnAnXmlProject";
      var projectKey = "sample-xml";
      provisionProject(ORCHESTRATOR, projectKey, "Sample XML");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xml-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "xml", "SonarLint IT XML");

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var rawIssues = analyzeFile(configScopeId, "sample-xml", "src/foo.xml");

      assertThat(rawIssues).hasSize(1);
    }
  }

  @Nested
  class ServerSentEvents {

    @Test
    @OnlyOnSonarQube(from = "9.9")
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
        var rawIssues = analyzeFile(configScopeId, "sample-java", "src/main/java/foo/Foo.java");

        assertThat(rawIssues)
          .extracting(RawIssueDto::getRuleKey)
          .containsOnly("java:S2325");
      });
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
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
        var issuesResponse = backend.getIssueTrackingService().trackWithServerIssues(new TrackWithServerIssuesParams(configScopeId, Map.of(
          Path.of("src/main/java/foo/Foo.java"),
          List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(14, 4, 14, 14, "hashedHash"),
            null, "java:S106", "Replace this use of System.out by a logger."))),
          true)).get();

        var fooIssues = issuesResponse.getIssuesByIdeRelativePath().get(Path.of("src/main/java/foo/Foo.java"));
        assertThat(fooIssues).hasSize(1);
        assertThat(fooIssues.get(0).isLeft()).isTrue();
        assertThat(fooIssues.get(0).getLeft().isResolved()).isTrue();
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
      var projectKey = "sample-xoo";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Xoo");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xoo-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "xoo", "SonarLint IT Xoo");
      // Use the pattern of long living branches in SQ 9.9, else we only have issues on changed files

      // main branch
      analyzeProject("sample-xoo-v1", projectKey);
      // short living branch
      analyzeProject("sample-xoo-v1", projectKey, "sonar.branch.name", short_branch);
      // long living branch
      analyzeProject("sample-xoo-v1", projectKey, "sonar.branch.name", long_branch);

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForSync(configScopeId);

      var sonarProjectBranch = backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId)).get();
      assertThat(sonarProjectBranch.getMatchedSonarProjectBranch()).isEqualTo(MAIN_BRANCH_NAME);

      matchedBranchNameForProject = short_branch;
      backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(configScopeId));

      await().untilAsserted(() -> assertThat(backend.getSonarProjectBranchService()
        .getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId))
        .get().getMatchedSonarProjectBranch())
        .isEqualTo(short_branch));

      await().untilAsserted(() -> assertThat(allBranchNamesForProject).contains(MAIN_BRANCH_NAME, short_branch, long_branch));
    }

    @Test
    void should_match_issues_from_branch() throws ExecutionException, InterruptedException {
      var configScopeId = "should_match_issues_from_branch";
      var projectKey = "sample-java";
      var projectName = "my-sample-java";
      var featureBranch = "branch-1.x";
      var ruleKey_s1172 = "java:S1172";

      provisionProject(ORCHESTRATOR, projectKey, projectName);
      analyzeProject(projectKey, projectKey);
      analyzeProject(projectKey, projectKey, "sonar.branch.name", featureBranch);

      var issuesBranch = adminWsClient.issues().search(new SearchRequest().setBranch(featureBranch).setComponentKeys(List.of(projectKey)));
      var issue_s1172 = issuesBranch.getIssuesList().stream().filter(issue -> issue.getRule().equals(ruleKey_s1172)).findFirst().orElseThrow();
      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(issue_s1172.getKey()).setTransition("falsepositive"));

      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var clientTrackedDto_s100 = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(4, 14, 4, 23, "hashedHash"),
        null, "java:S100", "Rename this method name to match the regular expression '^[a-z][a-zA-Z0-9]*$'."); // this one is not matched but its on the server
      var clientTrackedDto_s1172 = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(8, 23, 8, 24, "hashedHash"),
        null, ruleKey_s1172, "Remove this unused method parameter \"i\".");
      var clientTrackedDto_s106 = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(14, 4, 14, 14, "hashedHash"),
        null, "java:S106", "Replace this use of System.out by a logger."); // not resolved on both branches
      var trackWithServerIssuesParams = new TrackWithServerIssuesParams(configScopeId, Map.of(Path.of("src/main/java/foo/Foo.java"),
        List.of(clientTrackedDto_s100, clientTrackedDto_s1172, clientTrackedDto_s106)), true);
      var issuesOnMainBranch = backend.getIssueTrackingService().trackWithServerIssues(trackWithServerIssuesParams).get().getIssuesByIdeRelativePath();

      var fooIssuesMainBranch = issuesOnMainBranch.get(Path.of("src/main/java/foo/Foo.java"));
      assertThat(fooIssuesMainBranch).hasSize(3);
      assertThat(fooIssuesMainBranch.stream().filter(Either::isLeft).count()).isEqualTo(3);
      assertThat(fooIssuesMainBranch.stream().filter(issue -> issue.getLeft().isResolved()).count()).isZero();

      didSynchronizeConfigurationScopes.clear();
      matchedBranchNameForProject = featureBranch;
      backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(configScopeId));
      await().untilAsserted(() -> assertThat(backend.getSonarProjectBranchService()
        .getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId))
        .get().getMatchedSonarProjectBranch())
        .isEqualTo(featureBranch));
      waitForSync(configScopeId);

      var issuesOnFeatureBranch = backend.getIssueTrackingService().trackWithServerIssues(trackWithServerIssuesParams).get().getIssuesByIdeRelativePath();

      var fooIssuesFeatureBranch = issuesOnFeatureBranch.get(Path.of("src/main/java/foo/Foo.java"));
      assertThat(fooIssuesFeatureBranch).hasSize(3);
      assertThat(fooIssuesFeatureBranch.stream().filter(Either::isLeft).count()).isEqualTo(3);
      assertThat(fooIssuesFeatureBranch.stream().filter(issue -> issue.getLeft().isResolved()).count()).isEqualTo(1);
    }
  }

  @Nested
  class TaintVulnerabilities {

    private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";
    private static final String CONFIG_SCOPE_ID = "sample-java-taint-in-ide";

    @BeforeEach
    void prepare() {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_TAINT, "java", "SonarLint Taint Java");
    }

    @AfterEach
    void stop() {
      backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(CONFIG_SCOPE_ID));
      var request = new PostRequest("api/projects/bulk_delete");
      request.setParam("projects", PROJECT_KEY_JAVA_TAINT);
      try (var response = adminWsClient.wsConnector().call(request)) {
      }
    }

    @Test
    void shouldSyncTaintVulnerabilities() throws ExecutionException, InterruptedException {
      openBoundConfigurationScope(CONFIG_SCOPE_ID, PROJECT_KEY_JAVA_TAINT, true);
      waitForAnalysisToBeReady(CONFIG_SCOPE_ID);

      analyzeMavenProject("sample-java-taint", PROJECT_KEY_JAVA_TAINT);

      // Ensure a vulnerability has been reported on server side
      var issuesList = adminWsClient.issues().search(new SearchRequest().setTypes(List.of("VULNERABILITY")).setComponentKeys(List.of(PROJECT_KEY_JAVA_TAINT))).getIssuesList();
      assertThat(issuesList).hasSize(1);

      var taintVulnerabilities = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(CONFIG_SCOPE_ID, true)).get().getTaintVulnerabilities();

      assertThat(taintVulnerabilities).hasSize(1);

      var taintVulnerability = taintVulnerabilities.get(0);
      assertThat(taintVulnerability.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));

      assertThat(taintVulnerability.getSeverity()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR);

      assertThat(taintVulnerability.getType()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.VULNERABILITY);
      assertThat(taintVulnerability.getRuleDescriptionContextKey()).isEqualTo("java_se");
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 8)) {
        assertThat(taintVulnerability.getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
        // In SQ 10.8+, old MAJOR severity maps to overridden MEDIUM impact
        assertThat(taintVulnerability.getImpacts()).containsExactly(entry(SoftwareQuality.SECURITY, ImpactSeverity.MEDIUM));
        assertThat(taintVulnerability.getSeverityMode().isRight()).isTrue();
        assertThat(taintVulnerability.getSeverityMode().getRight().getImpacts().get(0)).extracting("softwareQuality", "impactSeverity").containsExactly(SoftwareQuality.SECURITY, ImpactSeverity.MEDIUM);
        assertThat(taintVulnerability.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
      } else if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 2)) {
        assertThat(taintVulnerability.getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
        // In 10.2 <= SQ < 10.8, the impact severity is not overridden
        assertThat(taintVulnerability.getImpacts()).containsExactly(entry(SoftwareQuality.SECURITY, ImpactSeverity.HIGH));
        assertThat(taintVulnerability.getSeverityMode().isRight()).isTrue();
        assertThat(taintVulnerability.getSeverityMode().getRight().getImpacts().get(0)).extracting("softwareQuality", "impactSeverity").containsExactly(SoftwareQuality.SECURITY, ImpactSeverity.HIGH);
        assertThat(taintVulnerability.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
      } else {
        assertThat(taintVulnerability.getCleanCodeAttribute()).isNull();
        assertThat(taintVulnerability.getSeverityMode().isLeft()).isTrue();
        assertThat(taintVulnerability.getSeverityMode().getLeft().getSeverity()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR);
        assertThat(taintVulnerability.getSeverityMode().getLeft().getType()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.VULNERABILITY);
      }
      assertThat(taintVulnerability.getFlows()).isNotEmpty();
      assertThat(taintVulnerability.isOnNewCode()).isTrue();
      var flow = taintVulnerability.getFlows().get(0);
      assertThat(flow.getLocations()).isNotEmpty();
      assertThat(flow.getLocations().get(0).getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(flow.getLocations().get(flow.getLocations().size() - 1).getTextRange().getHash()).isIn(hash("request.getParameter(\"user\")"),
        hash("request.getParameter(\"pass\")"));
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void shouldUpdateTaintVulnerabilityInLocalStorageWhenChangedOnServer() throws ExecutionException, InterruptedException {
      openBoundConfigurationScope(CONFIG_SCOPE_ID, PROJECT_KEY_JAVA_TAINT, true);
      waitForAnalysisToBeReady(CONFIG_SCOPE_ID);

      assertThat(backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(CONFIG_SCOPE_ID)).get().getTaintVulnerabilities()).isEmpty();

      // check TaintVulnerabilityRaised is received
      analyzeMavenProject("sample-java-taint", PROJECT_KEY_JAVA_TAINT);

      waitAtMost(1, TimeUnit.MINUTES).until(() -> !didChangeTaintVulnerabilitiesEvents.isEmpty());
      var issues = getIssueKeys(adminWsClient, "javasecurity:S3649");
      assertThat(issues).isNotEmpty();
      var issueKey = issues.get(0);
      var firstTaintChangedEvent = didChangeTaintVulnerabilitiesEvents.remove(0);
      assertThat(firstTaintChangedEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getClosedTaintVulnerabilityIds,
          DidChangeTaintVulnerabilitiesParams::getUpdatedTaintVulnerabilities)
        .containsExactly(CONFIG_SCOPE_ID, emptySet(), emptyList());
      assertThat(firstTaintChangedEvent.getAddedTaintVulnerabilities())
        .extracting(TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::isResolved, TaintVulnerabilityDto::getRuleKey, TaintVulnerabilityDto::getMessage,
          TaintVulnerabilityDto::getIdeFilePath, TaintVulnerabilityDto::getSeverity, TaintVulnerabilityDto::getType, TaintVulnerabilityDto::isOnNewCode)
        .containsExactly(tuple(issueKey, false, "javasecurity:S3649", "Change this code to not construct SQL queries directly from user-controlled data.",
          Paths.get("src/main/java/foo/DbHelper.java"), org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR,
          org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.VULNERABILITY, true));
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

      var taintIssues = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(CONFIG_SCOPE_ID)).get().getTaintVulnerabilities();
      assertThat(taintIssues)
        .extracting(TaintVulnerabilityDto::getSonarServerKey, TaintVulnerabilityDto::isResolved, TaintVulnerabilityDto::getRuleKey, TaintVulnerabilityDto::getMessage,
          TaintVulnerabilityDto::getIdeFilePath, TaintVulnerabilityDto::getSeverity, TaintVulnerabilityDto::getType, TaintVulnerabilityDto::isOnNewCode)
        .containsExactly(tuple(issueKey, false, "javasecurity:S3649", "Change this code to not construct SQL queries directly from user-controlled data.",
          Paths.get("src/main/java/foo/DbHelper.java"), org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR,
          org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.VULNERABILITY, true));
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

      resolveIssueAsWontFix(adminWsClient, issueKey);

      // check IssueChangedEvent is received
      waitAtMost(1, TimeUnit.MINUTES).until(() -> !didChangeTaintVulnerabilitiesEvents.isEmpty());
      var secondTaintEvent = didChangeTaintVulnerabilitiesEvents.remove(0);
      assertThat(secondTaintEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getClosedTaintVulnerabilityIds,
          DidChangeTaintVulnerabilitiesParams::getAddedTaintVulnerabilities)
        .containsExactly(CONFIG_SCOPE_ID, emptySet(), emptyList());
      assertThat(secondTaintEvent.getUpdatedTaintVulnerabilities())
        .extracting(TaintVulnerabilityDto::isResolved)
        .containsExactly(true);

      reopenIssue(adminWsClient, issueKey);

      // check IssueChangedEvent is received
      waitAtMost(1, TimeUnit.MINUTES).until(() -> !didChangeTaintVulnerabilitiesEvents.isEmpty());
      var thirdTaintEvent = didChangeTaintVulnerabilitiesEvents.remove(0);
      assertThat(thirdTaintEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getClosedTaintVulnerabilityIds,
          DidChangeTaintVulnerabilitiesParams::getAddedTaintVulnerabilities)
        .containsExactly(CONFIG_SCOPE_ID, emptySet(), emptyList());
      assertThat(thirdTaintEvent.getUpdatedTaintVulnerabilities())
        .extracting(TaintVulnerabilityDto::isResolved)
        .containsExactly(false);

      // analyze another project under the same project key to close the taint issue
      analyzeMavenProject("sample-java", PROJECT_KEY_JAVA_TAINT);

      // check TaintVulnerabilityClosed is received
      waitAtMost(1, TimeUnit.MINUTES).until(() -> !didChangeTaintVulnerabilitiesEvents.isEmpty());
      var fourthTaintEvent = didChangeTaintVulnerabilitiesEvents.remove(0);
      assertThat(fourthTaintEvent)
        .extracting(DidChangeTaintVulnerabilitiesParams::getConfigurationScopeId, DidChangeTaintVulnerabilitiesParams::getUpdatedTaintVulnerabilities,
          DidChangeTaintVulnerabilitiesParams::getAddedTaintVulnerabilities)
        .containsExactly(CONFIG_SCOPE_ID, emptyList(), emptyList());
      assertThat(fourthTaintEvent.getClosedTaintVulnerabilityIds())
        .containsExactly(raisedIssueId);
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Hotspots {

    private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";

    @BeforeAll
    void prepare() {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");

      // Build project to have bytecode and analyze
      analyzeMavenProject("sample-java-hotspot", PROJECT_KEY_JAVA_HOTSPOT);
    }

    @BeforeEach
    void start() {
      var globalProps = new HashMap<String, String>();
      globalProps.put("sonar.global.label", "It works");
    }

    @AfterEach
    void stop() {
      adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
    }

    @Test
    // SonarQube should support opening security hotspots
    @OnlyOnSonarQube(from = "9.9")
    @Disabled
    void shouldShowHotspotWhenOpenedFromSonarQube() throws InvalidProtocolBufferException {
      var configScopeId = "shouldShowHotspotWhenOpenedFromSonarQube";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT, true);
      waitForAnalysisToBeReady(configScopeId);
      var hotspotKey = getFirstHotspotKey(PROJECT_KEY_JAVA_HOTSPOT);

      requestOpenHotspotWithParams(PROJECT_KEY_JAVA_HOTSPOT, hotspotKey);

      var captor = ArgumentCaptor.forClass(HotspotDetailsDto.class);
      verify(client, timeout(1000)).showHotspot(eq(configScopeId), captor.capture());

      var actualHotspot = captor.getValue();
      assertThat(actualHotspot.getKey()).isEqualTo(hotspotKey);
      assertThat(actualHotspot.getMessage()).isEqualTo("Make sure that this logger's configuration is safe.");
      assertThat(actualHotspot.getIdeFilePath()).isEqualTo(Path.of("src/main/java/foo/Foo.java"));
      assertThat(actualHotspot.getTextRange()).usingRecursiveComparison().isEqualTo(new TextRangeDto(9, 4, 9, 45));
      assertThat(actualHotspot.getAuthor()).isEmpty();
      assertThat(actualHotspot.getStatus()).isEqualTo("TO_REVIEW");
      assertThat(actualHotspot.getResolution()).isNull();
      assertThat(actualHotspot.getRule().getKey()).isEqualTo("java:S4792");
    }

    private int requestOpenHotspotWithParams(String projectKey, String hotspotKey) {
      var request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create("http://localhost:" + serverLauncher.getServer().getEmbeddedServerPort() + "/sonarlint/api/hotspots/show?server=" + URLEncoder.encode(ORCHESTRATOR.getServer().getUrl(), StandardCharsets.UTF_8) + "&project="
          + projectKey + "&hotspot=" + hotspotKey))
        .build();
      HttpResponse<String> response;
      try {
        response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1;
      }
      return response.statusCode();
    }

    private String getFirstHotspotKey(String projectKey) throws InvalidProtocolBufferException {
      var response = ORCHESTRATOR.getServer()
        .newHttpCall("/api/hotspots/search.protobuf")
        .setParam("projectKey", projectKey)
        .setAdminCredentials()
        .execute();
      var parser = org.sonarqube.ws.Hotspots.SearchWsResponse.parser();
      return parser.parseFrom(response.getBody()).getHotspots(0).getKey();
    }

    @Test
    void reportHotspots() {
      var configScopeId = "reportHotspots";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT, false);
      waitForAnalysisToBeReady(configScopeId);
      await().untilAsserted(() -> assertThat(rpcClientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored server info"))).isTrue());

      var rawIssues = analyzeFile(configScopeId, PROJECT_KEY_JAVA_HOTSPOT, "src/main/java/foo/Foo.java", "sonar.java.binaries",
        new File("projects/sample-java-hotspot/target/classes").getAbsolutePath());

      assertThat(rawIssues)
        .extracting(RawIssueDto::getRuleKey, RawIssueDto::getType)
        .containsExactly(tuple(javaRuleKey("S4792"), RuleType.SECURITY_HOTSPOT));
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void loadHotspotRuleDescription() throws Exception {
      var configScopeId = "loadHotspotRuleDescription";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT, true);
      waitForAnalysisToBeReady(configScopeId);

      var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, "java:S4792", null)).get();
      assertThat(ruleDetails.details().getName()).isEqualTo("Configuring loggers is security-sensitive");
      assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(2).getContent().getLeft().getHtmlContent())
        .contains("Check that your production deployment doesn’t have its loggers in \"debug\" mode");
    }

    @Test
    void shouldMatchServerSecurityHotspots() throws ExecutionException, InterruptedException {
      var configScopeId = "shouldMatchServerSecurityHotspots";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT, true);
      waitForAnalysisToBeReady(configScopeId);

      var textRangeWithHash = new TextRangeWithHashDto(9, 4, 9, 45, "qwer");
      var clientTrackedHotspotsByServerRelativePath = Map.of(
        Path.of("src/main/java/foo/Foo.java"),
        List.of(new ClientTrackedFindingDto(null, null, textRangeWithHash, null, "java:S4792", "Make sure that this logger's configuration is safe.")),
        Path.of("src/main/java/bar/Bar.java"), List.of(new ClientTrackedFindingDto(null, null, textRangeWithHash, null, "java:S1234", "Some other rule")));

      var matchWithServerSecurityHotspotsResponse = backend.getSecurityHotspotMatchingService()
        .matchWithServerSecurityHotspots(new MatchWithServerSecurityHotspotsParams(configScopeId, clientTrackedHotspotsByServerRelativePath, true)).get();
      assertThat(matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByIdeRelativePath()).hasSize(2);
      var fooSecurityHotspots = matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByIdeRelativePath().get(Path.of("src/main/java/foo/Foo.java"));
      assertThat(fooSecurityHotspots).hasSize(1);
      assertThat(fooSecurityHotspots.get(0).isLeft()).isTrue();
      assertThat(fooSecurityHotspots.get(0).getLeft().getStatus()).isEqualTo(HotspotStatus.TO_REVIEW);
      var barSecurityHotspots = matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByIdeRelativePath().get(Path.of("src/main/java/bar/Bar.java"));
      assertThat(barSecurityHotspots).hasSize(1);
      assertThat(barSecurityHotspots.get(0).isRight()).isTrue();

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
      var link = serverVersion.isGreaterThanOrEquals(10, 4) ? "OWASP - <a href=..." : "<a href=\"https:/...";
      assertThat(extendedDescription.getTabs())
        .flatExtracting(this::extractTabContent)
        .containsExactly(
          "Why is this an issue?",
          "<p>Path injections occur when an application us...",
          "How can I fix it?",
          "--> Java SE (java_se)",
          "    <p>The following code is vulnerable to path inj...",
          "--> Others (others)",
          "    <h4>How can I fix it in another component or fr...",
          "More Info",
          "<h3>Standards</h3>\n"
            + "<ul>\n"
            + "  <li> " + link);

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

      var extendedDescription = description.getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent())
        .isEqualTo("<p>This vulnerability makes it possible to temporarily execute JavaScript code in the context of the application, granting access to the session of\n"
          + "the victim. This is possible because user-provided data, such as URL parameters, are copied into the HTML body of the HTTP response that is sent back\n"
          + "to the user.</p>");
      var iterator = extendedDescription.getTabs().iterator();
      iterator.next();
      assertThat(extendedDescription.getTabs())
        .flatExtracting(this::extractTabContent)
        .containsExactly(
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
            + "  <li> <a href=\"htt...");

      var howToFixTab = extendedDescription.getTabs().get(1);
      assertThat(howToFixTab.getContent().getRight().getDefaultContextKey()).isEqualTo("spring");
    }

    @Test
    @OnlyOnSonarQube(from = "9.9")
    void shouldEmulateDescriptionSectionsForHotspotRules() throws ExecutionException, InterruptedException {
      var configScopeId = "shouldEmulateDescriptionSectionsForHotspotRules";
      var projectKey = "sample-java-hotspot-new-backend";
      var projectName = "Java With Security Hotspots";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      openBoundConfigurationScope(configScopeId, projectKey, true);
      waitForAnalysisToBeReady(configScopeId);

      var activeRuleDetailsResponse = backend.getRulesService()
        .getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, "java:S4792", null))
        .get();

      var extendedDescription = activeRuleDetailsResponse.details().getDescription().getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 4)) {
        // SONARJAVA-4739 Rule S4792 is deprecated
        assertThat(extendedDescription.getTabs())
          .flatExtracting(this::extractTabContent)
          .containsOnly(
            "What's the risk?",
            "<p>This rule is deprecated, and will eventually...",
            "Assess the risk",
            "<h2>Ask Yourself Whether</h2>\n"
              + "<ul>\n"
              + "  <li> unaut...",
            "How can I fix it?",
            "<h2>Recommended Secure Coding Practices</h2>\n"
              + "<u...");
      } else {
        assertThat(extendedDescription.getTabs())
          .flatExtracting(this::extractTabContent)
          .containsOnly(
            "What's the risk?",
            "<p>Configuring loggers is security-sensitive. I...",
            "Assess the risk",
            "<h2>Ask Yourself Whether</h2>\n"
              + "<ul>\n"
              + "  <li> unaut...",
            "How can I fix it?",
            "<h2>Recommended Secure Coding Practices</h2>\n"
              + "<u...");
      }

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

  private String javaRuleKey(String key) {
    return "java:" + key;
  }

  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(singletonList(value))
      .setComponent(moduleKey));
  }

  private static void analyzeMavenProject(String projectDirName, String projectKey) {
    analyzeMavenProject(ORCHESTRATOR, projectDirName, Map.of("sonar.projectKey", projectKey));
  }

  private static void openBoundConfigurationScope(String configScopeId, String projectKey, boolean bindingSuggestionDisabled) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configScopeId, null, true, "My " + configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, bindingSuggestionDisabled)))));
  }

  private static void waitForSync(String configScopeId) {
    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(configScopeId));
  }

  private static void waitForAnalysisToBeReady(String configScopeId) {
    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
  }

  private void analyzeProject(String projectDirName, String projectKey, String... properties) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    ORCHESTRATOR.executeBuild(SonarScanner.create(projectDir.toFile())
      .setProjectKey(projectKey)
      .setSourceDirs("src")
      .setProperties(properties)
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
  }

  private List<RawIssueDto> analyzeFile(String configScopeId, String baseDir, String filePathStr, String... properties) {
    var filePath = Path.of("projects").resolve(baseDir).resolve(filePathStr);
    var fileUri = filePath.toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(fileUri, Path.of(filePathStr), configScopeId, false, null, filePath.toAbsolutePath(), null, null, true)),
      List.of(),
      List.of()
    ));

    var analyzeResponse = backend.getAnalysisService().analyzeFiles(
      new AnalyzeFilesParams(configScopeId, UUID.randomUUID(), List.of(fileUri), toMap(properties), System.currentTimeMillis())
    ).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedIssues = ((MockSonarLintRpcClientDelegate) client).getRaisedIssues(configScopeId);
    ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    return raisedIssues != null ? raisedIssues : List.of();
  }

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD));
        } else if (connectionId.equals(CONNECTION_ID_WRONG_CREDENTIALS)) {
          return Either.forRight(new UsernamePasswordDto("foo", "bar"));
        } else {
          return super.getCredentials(connectionId);
        }
      }

      @Override
      public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
        List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
        didChangeTaintVulnerabilitiesEvents
          .add(new DidChangeTaintVulnerabilitiesParams(configurationScopeId, closedTaintVulnerabilityIds, addedTaintVulnerabilities, updatedTaintVulnerabilities));
      }

      @Override
      public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
        didSynchronizeConfigurationScopes.addAll(configurationScopeIds);
      }

      @Override
      public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
        analysisReadinessByConfigScopeId.putAll(configurationScopeIds.stream().collect(Collectors.toMap(Function.identity(), k -> areReadyForAnalysis)));
      }

      @Override
      public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, SonarLintCancelChecker cancelChecker)
        throws ConfigScopeNotFoundException {
        allBranchNamesForProject.addAll(allBranchesNames);
        return matchedBranchNameForProject == null ? super.matchSonarProjectBranch(configurationScopeId, mainBranchName, allBranchesNames, cancelChecker)
          : matchedBranchNameForProject;
      }

      @Override
      public void log(LogParams params) {
        System.out.println(params);
        rpcClientLogs.add(params);
      }
    };
  }
}
