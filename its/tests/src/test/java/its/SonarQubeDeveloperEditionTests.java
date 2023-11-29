/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.ConsoleLogOutput;
import its.utils.OrchestratorUtils;
import its.utils.PluginLocator;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.issues.DoTransitionRequest;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.issues.SetSeverityRequest;
import org.sonarqube.ws.client.issues.SetTypeRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

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
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";

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
  private static final List<LogParams> clientLogs = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void start() throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    try {
      var languages = Set.of(JAVA, GO, PHP, JS, PYTHON, HTML, RUBY, KOTLIN, SCALA, XML,COBOL, CLOUDFORMATION, DOCKER, KUBERNETES, TERRAFORM);
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, true, true);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, featureFlags,
            sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            Collections.emptySet(), Map.of(), languages, Collections.emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), false),
              new SonarQubeConnectionConfigurationDto(CONNECTION_ID_WRONG_CREDENTIALS, ORCHESTRATOR.getServer().getUrl(), false)),
            Collections.emptyList(),
            sonarUserHome.toString(),
            Map.of(), false))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @BeforeEach
  void clearState() {
    didSynchronizeConfigurationScopes.clear();
    allBranchNamesForProject.clear();
    matchedBranchNameForProject = null;
    clientLogs.clear();
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @AfterEach
  void removeConfigScope() {
    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(CONFIG_SCOPE_ID));
  }

  @Nested
  class AnalysisTests {

    private List<String> logs;

    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void start() {
      Map<String, String> globalProps = new HashMap<>();
      globalProps.put("sonar.global.label", "It works");
      logs = new CopyOnWriteArrayList<>();

      var nodeJsHelper = new NodeJsHelper((m, l) -> System.out.println(l + " " + m));
      nodeJsHelper.detect(null);

      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .enableHotspots()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.JAVA)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.PHP)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.JS)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.PYTHON)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.HTML)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.RUBY)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.KOTLIN)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.SCALA)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.XML)
        // Needed to have the global extension plugin loaded
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.COBOL)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.GO)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.CLOUDFORMATION)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.DOCKER)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.KUBERNETES)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.TERRAFORM)
        .useEmbeddedPlugin(org.sonarsource.sonarlint.core.commons.Language.GO.getPluginKey(), PluginLocator.getGoPluginPath())
        .useEmbeddedPlugin(org.sonarsource.sonarlint.core.commons.Language.CLOUDFORMATION.getPluginKey(), PluginLocator.getIacPluginPath())
        .setLogOutput((msg, level) -> {
          logs.add(msg);
          System.out.println(msg);
        })
        .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
        .setExtraProperties(globalProps)
        .build();
      engine = new ConnectedSonarLintEngineImpl(globalConfig);

      // This profile is altered in a test
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
    }

    @AfterEach
    void stop() {
      adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
      engine.stop(false);
    }

    // TODO should be moved to a separate class, not related to analysis
    @Test
    void shouldRaiseIssuesOnAJavaScriptProject() throws Exception {
      var projectKey = "sample-javascript";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Javascript");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/javascript-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "js", "SonarLint IT Javascript");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-javascript", "src/Person.js"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesFromACustomRule() throws Exception {
      var projectKey = "sample-java-custom";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java Custom");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-custom.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Custom");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java-custom", "src/main/java/foo/Foo.java"),
        issueListener, null, null);
      assertThat(issueListener.getIssues()).extracting("ruleKey", "startLine").containsOnly(
        tuple("mycompany-java:AvoidAnnotation", 12));
    }

    @Test
    void shouldRaiseIssuesOnAPhpProject() throws Exception {
      var projectKey = "sample-php";
      provisionProject(ORCHESTRATOR, projectKey, "Sample PHP");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/php-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "php", "SonarLint IT PHP");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-php", "src/Math.php"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAPythonProject() throws Exception {
      var projectKey = "sample-python";
      var projectName = "Sample Python";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/python-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "SonarLint IT Python");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-python", "src/hello.py"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAHtmlProject() throws IOException {
      var projectKey = "sample-web";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Web");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/web-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "web", "SonarLint IT Web");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-web", "src/file.html"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAGoProject() throws IOException {
      var projectKey = "sample-go";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Go");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/go-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "go", "SonarLint IT Go");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-go", "src/sample.go"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "9.2")
    void shouldRaiseIssuesOnACloudFormationProject() throws IOException {
      var projectKey = "sample-cloudformation";
      provisionProject(ORCHESTRATOR, projectKey, "Sample CloudFormation");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/cloudformation-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cloudformation", "SonarLint IT CloudFormation");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-cloudformation", "src/sample.yaml"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(2);
    }

    @Test
    @OnlyOnSonarQube(from = "9.2")
    void shouldRaiseIssuesOnADockerProject() throws IOException {
      var projectKey = "sample-docker";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Docker");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/docker-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "docker", "SonarLint IT Docker");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-docker", "src/Dockerfile"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(2);
    }

    @Test
    @OnlyOnSonarQube(from = "9.2")
    void shouldRaiseIssuesOnAKubernetesProject() throws IOException {
      var projectKey = "sample-kubernetes";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Kubernetes");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/kubernetes-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "kubernetes", "SonarLint IT Kubernetes");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-kubernetes", "src/sample.yaml"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    @OnlyOnSonarQube(from = "9.2")
    void shouldRaiseIssuesOnATerraformProject() throws IOException {
      var projectKey = "sample-terraform";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Terraform");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/terraform-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "terraform", "SonarLint IT Terraform");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-terraform", "src/sample.tf"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void customSensorsShouldNotBeExecuted() throws Exception {
      var projectKey = "sample-java-custom-sensor";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java Custom");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/custom-sensor.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Custom Sensor");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).isEmpty();
    }

    // TODO should be moved to a medium test
    @Test
    void globalExtension() throws Exception {
      var projectKey = "sample-global-extension";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Global Extension");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/global-extension.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "cobol", "SonarLint IT Global Extension");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      assertThat(logs).contains("Start Global Extension It works");

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-global-extension",
        "src/foo.glob",
        "sonar.cobol.file.suffixes", "glob"),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).extracting("ruleKey", "message").containsOnly(
        tuple("global:inc", "Issue number 0"));

      issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-global-extension",
        "src/foo.glob",
        "sonar.cobol.file.suffixes", "glob"),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).extracting("ruleKey", "message").containsOnly(
        tuple("global:inc", "Issue number 1"));

      engine.stop(true);
      assertThat(logs).contains("Stop Global Extension");
    }

    @Test
    void shouldRaiseIssuesFromATemplateRule() throws Exception {
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
        backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
          List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
        await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
        await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

        var issueListener = new SaveIssueListener();
        engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
          issueListener, null, null);

        assertThat(issueListener.getIssues()).hasSize(3);

        var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, javaRuleKey("myrule"), null)).get();
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
    void shouldHonorServerSideSettings() throws Exception {
      var projectKey = "sample-java-configured";
      var projectName = "Sample Java";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
        issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(2);

      clientLogs.clear();
      didSynchronizeConfigurationScopes.clear();
      // Override default file suffixes in global props so that input file is not considered as a Java file
      setSettingsMultiValue(null, "sonar.java.file.suffixes", ".foo");
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID, new BindingConfigurationDto(CONNECTION_ID, projectKey, false))); //todo change binding so there is a difference
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      issueListener.clear();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
        issueListener, null, null);
      assertThat(issueListener.getIssues()).isEmpty();

      clientLogs.clear();
      didSynchronizeConfigurationScopes.clear();
      // Override default file suffixes in project props so that input file is considered as a Java file again
      setSettingsMultiValue(projectKey, "sonar.java.file.suffixes", ".java");
      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      issueListener.clear();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
        issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(2);

    }

    @Test
    void shouldRaiseIssuesOnARubyProject() throws Exception {
      var projectKey = "sample-ruby";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Ruby");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/ruby-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "ruby", "SonarLint IT Ruby");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-ruby", "src/hello.rb"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAKotlinProject() throws Exception {
      var projectKey = "sample-kotlin";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Kotlin");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/kotlin-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "kotlin", "SonarLint IT Kotlin");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-kotlin", "src/hello.kt"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAScalaProject() throws Exception {
      var projectKey = "sample-scala";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Scala");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/scala-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "scala", "SonarLint IT Scala");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-scala", "src/Hello.scala"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAnXmlProject() throws Exception {
      var projectKey = "sample-xml";
      provisionProject(ORCHESTRATOR, projectKey, "Sample XML");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xml-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "xml", "SonarLint IT XML");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-xml", "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

  }

  @Nested
  class ServerSentEvents {

    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void start() {
      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.JAVA)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .build();
      engine = new ConnectedSonarLintEngineImpl(globalConfig);

    }

    @AfterEach
    void stop() {
      engine.stop(true);
    }

    @Test
    @OnlyOnSonarQube(from = "9.4")
    void shouldUpdateQualityProfileInLocalStorageWhenProfileChangedOnServer() {
      var projectKey = "projectKey-sse";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var qualityProfile = getQualityProfile(adminWsClient, "SonarLint IT Java");
      deactivateRule(adminWsClient, qualityProfile, "java:S106");
      waitAtMost(1, TimeUnit.MINUTES).pollDelay(Duration.ofSeconds(10)).untilAsserted(() -> {
        var issueListener = new SaveIssueListener();
        engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"), issueListener, null, null);
        assertThat(issueListener.getIssues())
          .extracting(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue::getRuleKey)
          .containsOnly("java:S2325");
      });
    }

    @Test
    @OnlyOnSonarQube(from = "9.6")
    void shouldUpdateIssueInLocalStorageWhenIssueResolvedOnServer() {
      var projectKey = "projectKey-sse2";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      analyzeMavenProject("sample-java", projectKey);

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var issueKey = getIssueKeys(adminWsClient, "java:S106").get(0);
      resolveIssueAsWontFix(adminWsClient, issueKey);

      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        var serverIssues = engine.getServerIssues(new ProjectBinding(projectKey, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/Foo.java");

        assertThat(serverIssues)
          .extracting(ServerIssue::getRuleKey, ServerIssue::isResolved)
          .contains(tuple("java:S106", true));
      });
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class BranchTests {
    // Use the pattern of long living branches in SQ 7.9, else we only have issues on changed files
    private static final String SHORT_BRANCH = "feature/short_living";
    private static final String LONG_BRANCH = "branch-1.x";
    private static final String PROJECT_KEY = "sample-xoo";
    private ConnectedSonarLintEngine engine;

    private Issue wfIssue;
    private Issue fpIssue;
    private Issue overridenSeverityIssue;
    private Issue overridenTypeIssue;

    @TempDir
    private Path sonarUserHome;

    @BeforeAll
    void prepare() {
      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setLogOutput(new ConsoleLogOutput(false))
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .setExtraProperties(new HashMap<>())
        .build());

      provisionProject(ORCHESTRATOR, PROJECT_KEY, "Sample Xoo");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xoo-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY, "xoo", "SonarLint IT Xoo");

      engine.updateProject(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), PROJECT_KEY, null);

      // main branch
      analyzeProject("sample-xoo-v1");

      // short living branch
      analyzeProject("sample-xoo-v1", "sonar.branch.name", SHORT_BRANCH);

      // long living branch
      analyzeProject("sample-xoo-v1", "sonar.branch.name", LONG_BRANCH);
      // Second analysis with fewer issues to have some closed issues on the branch
      analyzeProject("sample-xoo-v2", "sonar.branch.name", LONG_BRANCH);

      // Mark a few issues as closed WF and closed FP on the branch
      var issueSearchResponse = adminWsClient.issues()
        .search(new SearchRequest().setStatuses(List.of("OPEN")).setTypes(List.of("CODE_SMELL")).setComponentKeys(List.of(PROJECT_KEY)).setBranch(LONG_BRANCH));
      wfIssue = issueSearchResponse.getIssues(0);
      fpIssue = issueSearchResponse.getIssues(1);
      // Change severity and type
      overridenSeverityIssue = issueSearchResponse.getIssues(2);
      overridenTypeIssue = issueSearchResponse.getIssues(3);

      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(wfIssue.getKey()).setTransition("wontfix"));
      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(fpIssue.getKey()).setTransition("falsepositive"));

      adminWsClient.issues().setSeverity(new SetSeverityRequest().setIssue(overridenSeverityIssue.getKey()).setSeverity("BLOCKER"));
      adminWsClient.issues().setType(new SetTypeRequest().setIssue(overridenTypeIssue.getKey()).setType("BUG"));

      // Ensure an hostpot has been reported on server side
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 2)) {
        assertThat(adminWsClient.hotspots().search(new org.sonarqube.ws.client.hotspots.SearchRequest().setProjectKey(PROJECT_KEY).setBranch(LONG_BRANCH)).getHotspotsList())
          .isNotEmpty();
      } else {
        assertThat(
          adminWsClient.issues().search(new SearchRequest().setTypes(List.of("SECURITY_HOTSPOT")).setComponentKeys(List.of(PROJECT_KEY))).getIssuesList())
            .isNotEmpty();
      }
    }

    @AfterAll
    public void stop() {
      engine.stop(true);
    }

    @Test
    void shouldSyncBranchesFromServer() throws ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY, true)))));

      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
      var sonarProjectBranch = backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(CONFIG_SCOPE_ID)).get();
      assertThat(sonarProjectBranch.getMatchedSonarProjectBranch()).isEqualTo(MAIN_BRANCH_NAME);

      matchedBranchNameForProject = SHORT_BRANCH;
      backend.getSonarProjectBranchService().didVcsRepositoryChange(new DidVcsRepositoryChangeParams(CONFIG_SCOPE_ID));

      await().untilAsserted(() -> assertThat(backend.getSonarProjectBranchService()
        .getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(CONFIG_SCOPE_ID))
        .get().getMatchedSonarProjectBranch())
        .isEqualTo(SHORT_BRANCH));

      // Starting from SQ 8.1, concept of short vs long living branch has been removed
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 1)) {
        await().untilAsserted(() -> assertThat(allBranchNamesForProject).contains(MAIN_BRANCH_NAME, SHORT_BRANCH, LONG_BRANCH));
      } else {
        await().untilAsserted(() -> assertThat(allBranchNamesForProject).contains(MAIN_BRANCH_NAME, LONG_BRANCH));
      }
    }

    @Test
    void shouldSyncIssuesFromBranch() {
      engine.downloadAllServerIssues(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), PROJECT_KEY, LONG_BRANCH, null);

      var file1Issues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/500lines.xoo");
      var file2Issues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/10000lines.xoo");

      // Number of issues is not limited to 10k
      assertThat(file1Issues.size() + file2Issues.size()).isEqualTo(10_500);

      Map<String, ServerIssue> allIssues = new HashMap<>();
      engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/500lines.xoo").forEach(i -> allIssues.put(i.getKey(), i));
      engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/10000lines.xoo").forEach(i -> allIssues.put(i.getKey(), i));

      assertThat(allIssues).hasSize(10_500);
      assertThat(allIssues.get(wfIssue.getKey()).isResolved()).isTrue();
      assertThat(allIssues.get(fpIssue.getKey()).isResolved()).isTrue();
      assertThat(allIssues.get(overridenSeverityIssue.getKey()).getUserSeverity()).isEqualTo(IssueSeverity.BLOCKER);
      assertThat(allIssues.get(overridenTypeIssue.getKey()).getType()).isEqualTo(RuleType.BUG);

      // No hotspots
      assertThat(allIssues.values()).allSatisfy(i -> assertThat(i.getType()).isIn(RuleType.CODE_SMELL, RuleType.BUG, RuleType.VULNERABILITY));
    }

    private void analyzeProject(String projectDirName, String... properties) {
      var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
      ORCHESTRATOR.executeBuild(SonarScanner.create(projectDir.toFile())
        .setProjectKey(PROJECT_KEY)
        .setSourceDirs("src")
        .setProperties(properties)
        .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
        .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
    }
  }

  @Nested
  class TaintVulnerabilities {

    private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";

    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void prepare() {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_TAINT, "java", "SonarLint Taint Java");

      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.JAVA)
        .setSonarLintUserHome(sonarUserHome)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .setExtraProperties(new HashMap<>())
        .build());
    }

    @AfterEach
    void stop() {
      engine.stop(true);
      var request = new PostRequest("api/projects/bulk_delete");
      request.setParam("projects", PROJECT_KEY_JAVA_TAINT);
      try (var response = adminWsClient.wsConnector().call(request)) {
      }

    }

    @Test
    void shouldSyncTaintVulnerabilities() throws ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_TAINT, true)))));
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
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        assertThat(taintVulnerability.getRuleDescriptionContextKey()).isEqualTo("java_se");
      } else {
        assertThat(taintVulnerability.getRuleDescriptionContextKey()).isNull();
      }
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 2)) {
        assertThat(taintVulnerability.getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
        assertThat(taintVulnerability.getImpacts()).containsExactly(entry(SoftwareQuality.SECURITY, ImpactSeverity.HIGH));
      } else {
        assertThat(taintVulnerability.getCleanCodeAttribute()).isNull();
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
    @OnlyOnSonarQube(from = "9.6")
    void shouldUpdateTaintVulnerabilityInLocalStorageWhenChangedOnServer() throws ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_TAINT, true)))));
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
          TaintVulnerabilityDto::getFilePath, TaintVulnerabilityDto::getSeverity, TaintVulnerabilityDto::getType, TaintVulnerabilityDto::isOnNewCode)
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
          TaintVulnerabilityDto::getFilePath, TaintVulnerabilityDto::getSeverity, TaintVulnerabilityDto::getType, TaintVulnerabilityDto::isOnNewCode)
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

    public static final String HOTSPOT_FEATURE_DISABLED = "hotspotFeatureDisabled";

    private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";

    @BeforeAll
    void prepare() {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");

      // Build project to have bytecode and analyze
      analyzeMavenProject("sample-java-hotspot", PROJECT_KEY_JAVA_HOTSPOT);
    }

    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void start(TestInfo info) {
      FileUtils.deleteQuietly(sonarUserHome.toFile());
      var globalProps = new HashMap<String, String>();
      globalProps.put("sonar.global.label", "It works");

      var globalConfigBuilder = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(org.sonarsource.sonarlint.core.commons.Language.JAVA)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .setExtraProperties(globalProps);
      if (!info.getTags().contains(HOTSPOT_FEATURE_DISABLED)) {
        globalConfigBuilder.enableHotspots();
      }
      var globalConfig = globalConfigBuilder.build();
      engine = new ConnectedSonarLintEngineImpl(globalConfig);
    }

    @AfterEach
    void stop() {
      adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
      try {
        engine.stop(true);
      } catch (Exception e) {
        // Ignore
      }
    }

    @Test
    @Tag(HOTSPOT_FEATURE_DISABLED)
    void dontReportHotspotsIfNotEnabled() throws Exception {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_HOTSPOT, false)))));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_HOTSPOT, PROJECT_KEY_JAVA_HOTSPOT,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).isEmpty();
    }

    @Test
    // SonarQube should support opening security hotspots
    @OnlyOnSonarQube(from = "8.6")
    void canFetchHotspot() throws InvalidProtocolBufferException {
      analyzeMavenProject("sample-java-hotspot", PROJECT_KEY_JAVA_HOTSPOT);
      var securityHotspotsService = new ServerApi(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID)).hotspot();

      var remoteHotspot = securityHotspotsService.fetch(getFirstHotspotKey(PROJECT_KEY_JAVA_HOTSPOT));

      assertThat(remoteHotspot).isNotEmpty();
      var actualHotspot = remoteHotspot.get();
      assertThat(actualHotspot.message).isEqualTo("Make sure that this logger's configuration is safe.");
      assertThat(actualHotspot.filePath).isEqualTo("src/main/java/foo/Foo.java");
      assertThat(actualHotspot.textRange).usingRecursiveComparison().isEqualTo(new TextRangeWithHash(9, 4, 9, 45, ""));
      assertThat(actualHotspot.author).isEmpty();
      assertThat(actualHotspot.status).isEqualTo(ServerHotspotDetails.Status.TO_REVIEW);
      assertThat(actualHotspot.resolution).isNull();
      assertThat(actualHotspot.rule.key).isEqualTo("java:S4792");
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
    void reportHotspots() throws Exception {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_HOTSPOT, false)))));
      await().untilAsserted(() -> assertThat(clientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer configuration"))).isTrue());

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_HOTSPOT, PROJECT_KEY_JAVA_HOTSPOT,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java-hotspot/target/classes").getAbsolutePath()),
        issueListener, null, null);

      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
        assertThat(issueListener.getIssues()).hasSize(1)
          .extracting(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue::getRuleKey, org.sonarsource.sonarlint.core.client.api.common.analysis.Issue::getType)
          .containsExactly(tuple(javaRuleKey(ORCHESTRATOR, "S4792"), RuleType.SECURITY_HOTSPOT));
      } else {
        // no hotspot detection when connected to SQ < 9.7
        assertThat(issueListener.getIssues()).isEmpty();
      }
    }

    @Test
    @OnlyOnSonarQube(from = "9.7")
    void loadHotspotRuleDescription() throws Exception {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, PROJECT_KEY_JAVA_HOTSPOT, new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_HOTSPOT, true)))));

      var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "java:S4792", null)).get();
      assertThat(ruleDetails.details().getName()).isEqualTo("Configuring loggers is security-sensitive");
      assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(2).getContent().getLeft().getHtmlContent())
        .contains("Check that your production deployment doesn’t have its loggers in \"debug\" mode");
    }

    @Test
    @OnlyOnSonarQube(from = "9.7")
    void shouldMatchServerSecurityHotspots() throws ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_HOTSPOT, false)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var textRangeWithHash = new TextRangeWithHashDto(9, 4, 9, 45, "qwer");
      var clientTrackedHotspotsByServerRelativePath = Map.of(
        "src/main/java/foo/Foo.java", List.of(new ClientTrackedFindingDto(null, null, textRangeWithHash, null, "java:S4792", "Make sure that this logger's configuration is safe.")),
        "src/main/java/bar/Bar.java", List.of(new ClientTrackedFindingDto(null, null, textRangeWithHash, null, "java:S1234", "Some other rule"))
      );
      var matchWithServerSecurityHotspotsResponse =
        backend.getSecurityHotspotMatchingService().matchWithServerSecurityHotspots(new MatchWithServerSecurityHotspotsParams(CONFIG_SCOPE_ID, clientTrackedHotspotsByServerRelativePath, true)).get();
      assertThat(matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByServerRelativePath()).hasSize(2);
      var fooSecurityHotspots = matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByServerRelativePath().get("src/main/java/foo/Foo.java");
      assertThat(fooSecurityHotspots).hasSize(1);
      assertThat(fooSecurityHotspots.get(0).isLeft()).isTrue();
      assertThat(fooSecurityHotspots.get(0).getLeft().getStatus()).isEqualTo(HotspotStatus.TO_REVIEW);
      var barSecurityHotspots = matchWithServerSecurityHotspotsResponse.getSecurityHotspotsByServerRelativePath().get("src/main/java/bar/Bar.java");
      assertThat(barSecurityHotspots).hasSize(1);
      assertThat(barSecurityHotspots.get(0).isRight()).isTrue();
    }
  }

  @Nested
  class RuleDescription {

    @Test
    void shouldFailIfNotAuthenticated() {
      var projectKey = "noAuth";
      var projectName = "Sample Javascript";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID_WRONG_CREDENTIALS, projectKey, true)))));

      adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
      try {
        var ex = assertThrows(ExecutionException.class,
          () -> backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, javaRuleKey("S106"), null)).get());
        assertThat(ex.getCause()).hasMessage("Could not find rule '" + javaRuleKey("S106") + "' in plugins loaded from '" + CONNECTION_ID_WRONG_CREDENTIALS + "'");
      } finally {
        adminWsClient.settings().reset(new ResetRequest().setKeys(List.of("sonar.forceAuthentication")));
      }
    }

    @Test
    void shouldContainExtendedDescription() throws Exception {
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

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));

      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var ruleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID,
        javaRuleKey("S106"), null)).get();
      var ruleDescription = ruleDetailsResponse.details().getDescription();
      if (ORCHESTRATOR.getServer().version().isGreaterThan(9, 5)) {
        var ruleTabs = ruleDescription.getRight().getTabs();
        assertThat(ruleTabs.get(ruleTabs.size() - 1).getContent().getLeft().getHtmlContent()).contains(expected);
      } else {
        // no description sections at that time
        assertThat(ruleDescription.isRight()).isFalse();

      }
    }

    @Test
    void shouldSupportsMarkdownDescription() throws Exception {
      var projectKey = "project-with-markdown-description";
      var projectName = "Project With Markdown Description";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-markdown.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Markdown");
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));

      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var ruleDetailsResponse =
        backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "mycompany-java:markdown",
          null)).get();

      assertThat(ruleDetailsResponse.details().getDescription().getLeft().getHtmlContent())
        .isEqualTo("<h1>Title</h1><ul><li>one</li>\n"
          + "<li>two</li></ul>");
    }

    @Test
    void shouldReturnAllContextsWithOthersSelectedIfNoContextProvided() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-taint-new-backend";

      var projectName = "Java With Taint Vulnerabilities";
      provisionProject(ORCHESTRATOR, projectKey, projectName);
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "javasecurity:S2083", null)).get();

      var description = activeRuleDetailsResponse.details().getDescription();

      if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        // no description sections at that time
        assertThat(description.isRight()).isFalse();
      } else {
        var extendedDescription = description.getRight();
        assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
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
              + "  <li> <a href=\"https:/...");

        var howToFixTab = extendedDescription.getTabs().get(1);
        assertThat(howToFixTab.getContent().getRight().getDefaultContextKey()).isEqualTo("others");
      }
    }

    @Test
    void shouldReturnAllContextsWithTheMatchingOneSelectedIfContextProvided() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-taint-rule-context-new-backend";

      provisionProject(ORCHESTRATOR, projectKey, "Java With Taint Vulnerabilities And Multiple Contexts");
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "javasecurity:S5131", "spring")).get();

      var description = activeRuleDetailsResponse.details().getDescription();

      if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        // no description sections at that time
        assertThat(description.isRight()).isFalse();
      } else {
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
    }

    @Test
    @OnlyOnSonarQube(from = "9.7")
    void shouldEmulateDescriptionSectionsForHotspotRules() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-hotspot-new-backend";

      provisionProject(ORCHESTRATOR, projectKey, "Java With Security Hotspots");
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getRulesService()
        .getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, javaRuleKey(ORCHESTRATOR, "S4792"), null))
        .get();

      var extendedDescription = activeRuleDetailsResponse.details().getDescription().getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
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
    void getProject() {
      var projectKey = "sample-project";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Project");

      SonarLintLogger.setTarget(new ConsoleLogOutput(false));
      var api = new ServerApi(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID)).component();
      assertThat(api.getProject("non-existing")).isNotPresent();
      assertThat(api.getProject(projectKey)).isPresent();
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
    // Starting from SonarJava 6.0 (embedded in SQ 8.2), rule repository has been changed
    return javaRuleKey(ORCHESTRATOR, key);
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
      public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
        didSynchronizeConfigurationScopes.addAll(params.getConfigurationScopeIds());
      }

      @Override
      public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) throws ConfigScopeNotFoundException {
        allBranchNamesForProject.addAll(allBranchesNames);
        return matchedBranchNameForProject == null ? super.matchSonarProjectBranch(configurationScopeId, mainBranchName, allBranchesNames, cancelChecker)
          : matchedBranchNameForProject;
      }

      @Override
      public void showSmartNotification(ShowSmartNotificationParams params) {
        super.showSmartNotification(params);
      }

      @Override
      public void log(LogParams params) {
        clientLogs.add(params);
        System.out.println("ClientLog: " + params.getMessage());
      }
    };
  }
}
