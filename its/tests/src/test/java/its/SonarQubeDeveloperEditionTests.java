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
import its.utils.OrchestratorUtils;
import its.utils.PluginLocator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
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
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static its.utils.ItUtils.SONAR_VERSION;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @BeforeAll
  static void createSonarLintUser() {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }

  private static SonarLintBackend backend;

  @TempDir
  private static Path sonarUserHome;

  @BeforeAll
  static void start() {
    backend = new SonarLintBackendImpl(newDummySonarLintClient());
    try {
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO, new FeatureFlagsDto(false, true, false, false, false, false), sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
          Collections.emptySet(), Collections.emptyMap(), Set.of(Language.JAVA), Collections.emptySet(),
          List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), Collections.emptyList(), sonarUserHome.toString(),
          Map.of()))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Nested
  class AnalysisTests {

    private List<String> logs;

    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void start(@TempDir Path sonarUserHome) {
      Map<String, String> globalProps = new HashMap<>();
      globalProps.put("sonar.global.label", "It works");
      logs = new CopyOnWriteArrayList<>();

      var nodeJsHelper = new NodeJsHelper();
      nodeJsHelper.detect(null);

      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .enableHotspots()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.JAVA)
        .addEnabledLanguage(Language.PHP)
        .addEnabledLanguage(Language.JS)
        .addEnabledLanguage(Language.PYTHON)
        .addEnabledLanguage(Language.HTML)
        .addEnabledLanguage(Language.RUBY)
        .addEnabledLanguage(Language.KOTLIN)
        .addEnabledLanguage(Language.SCALA)
        .addEnabledLanguage(Language.XML)
        // Needed to have the global extension plugin loaded
        .addEnabledLanguage(Language.COBOL)
        .addEnabledLanguage(Language.GO)
        .addEnabledLanguage(Language.CLOUDFORMATION)
        .addEnabledLanguage(Language.DOCKER)
        .addEnabledLanguage(Language.KUBERNETES)
        .addEnabledLanguage(Language.TERRAFORM)
        .useEmbeddedPlugin(Language.GO.getPluginKey(), PluginLocator.getGoPluginPath())
        .useEmbeddedPlugin(Language.CLOUDFORMATION.getPluginKey(), PluginLocator.getIacPluginPath())
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
      engine.stop(true);
    }

    // TODO should be moved to a separate class, not related to analysis
    @Test
    void shouldRaiseIssuesOnAJavaScriptProject() throws Exception {
      var projectKey = "sample-javascript";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Javascript");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/javascript-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "js", "SonarLint IT Javascript");

      updateProject(engine, projectKey);

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
      updateProject(engine, projectKey);

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
      updateProject(engine, projectKey);

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-php", "src/Math.php"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void shouldRaiseIssuesOnAPythonProject() throws Exception {
      var projectKey = "sample-python";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Python");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/python-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "SonarLint IT Python");

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      ;
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
        updateProject(engine, projectKey);

        var issueListener = new SaveIssueListener();
        engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
          issueListener, null, null);

        assertThat(issueListener.getIssues()).hasSize(3);

        assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), javaRuleKey("myrule"), projectKey).get().getHtmlDescription())
          .contains("my_rule_description");

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
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      updateProject(engine, projectKey);

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
        issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(2);

      // Override default file suffixes in global props so that input file is not considered as a Java file
      setSettingsMultiValue(null, "sonar.java.file.suffixes", ".foo");
      updateProject(engine, projectKey);

      issueListener.clear();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"),
        issueListener, null, null);
      assertThat(issueListener.getIssues()).isEmpty();

      // Override default file suffixes in project props so that input file is considered as a Java file again
      setSettingsMultiValue(projectKey, "sonar.java.file.suffixes", ".java");
      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

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

      updateProject(engine, projectKey);

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-xml", "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

  }

  @Nested
  class ServerSentEvents {

    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void start(@TempDir Path sonarUserHome) {
      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.JAVA)
        .setLogOutput((msg, level) -> {
          System.out.println(msg);
        })
        .build();
      engine = new ConnectedSonarLintEngineImpl(globalConfig);

    }

    @AfterEach
    void stop() {
      engine.stop(true);
    }

    @Test
    @OnlyOnSonarQube(from = "9.4")
    void shouldUpdateQualityProfileInLocalStorageWhenProfileChangedOnServer() throws IOException {
      var projectKey = "projectKey-sse";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      updateProject(engine, projectKey);
      Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey), events::add, null);
      var qualityProfile = getQualityProfile(adminWsClient, "SonarLint IT Java");
      deactivateRule(adminWsClient, qualityProfile, "java:S106");
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(RuleSetChangedEvent.class, e -> {
            assertThat(e.getDeactivatedRules()).containsOnly("java:S106");
            assertThat(e.getActivatedRules()).isEmpty();
            assertThat(e.getProjectKeys()).containsOnly(projectKey);
          });
      });

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey, "sample-java", "src/main/java/foo/Foo.java"), issueListener, null, null);
      assertThat(issueListener.getIssues())
        .extracting(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue::getRuleKey)
        .containsOnly("java:S2325");
    }

    @Test
    @OnlyOnSonarQube(from = "9.6")
    void shouldUpdateIssueInLocalStorageWhenIssueResolvedOnServer() {

      var projectKey = "projectKey-sse2";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Java");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");

      analyzeMavenProject("sample-java", projectKey);
      updateProject(engine, projectKey);
      Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey), events::add, null);
      var issueKey = getIssueKeys(adminWsClient, "java:S106").get(0);
      resolveIssueAsWontFix(adminWsClient, issueKey);

      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(IssueChangedEvent.class, e -> {
            assertThat(e.getImpactedIssueKeys()).containsOnly(issueKey);
            assertThat(e.getResolved()).isTrue();
            assertThat(e.getUserSeverity()).isNull();
            assertThat(e.getUserType()).isNull();
            assertThat(e.getProjectKey()).isEqualTo(projectKey);
          });
      });

      var serverIssues = engine.getServerIssues(new ProjectBinding(projectKey, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/Foo.java");

      assertThat(serverIssues)
        .extracting(ServerIssue::getRuleKey, ServerIssue::isResolved)
        .contains(tuple("java:S106", true));
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
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .setExtraProperties(new HashMap<>())
        .build());

      provisionProject(ORCHESTRATOR, PROJECT_KEY, "Sample Xoo");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xoo-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY, "xoo", "SonarLint IT Xoo");

      engine.updateProject(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY, null);

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
    void shouldSyncBranchesFromServer() {
      engine.sync(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), Set.of(PROJECT_KEY), null);

      // Starting from SQ 8.1, concept of short vs long living branch has been removed
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 1)) {
        assertThat(engine.getServerBranches(PROJECT_KEY).getBranchNames()).containsOnly(MAIN_BRANCH_NAME, LONG_BRANCH, SHORT_BRANCH);
      } else {
        assertThat(engine.getServerBranches(PROJECT_KEY).getBranchNames()).containsOnly(MAIN_BRANCH_NAME, LONG_BRANCH);
      }
      assertThat(engine.getServerBranches(PROJECT_KEY).getMainBranchName()).isEqualTo(MAIN_BRANCH_NAME);
    }

    @Test
    void shouldSyncIssuesFromBranch() {
      engine.downloadAllServerIssues(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY, LONG_BRANCH, null);

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
    void prepare(@TempDir Path sonarUserHome) {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_TAINT, "java", "SonarLint Taint Java");

      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .addEnabledLanguage(Language.JAVA)
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
    void shouldSyncTaintVulnerabilities() {
      analyzeMavenProject("sample-java-taint", PROJECT_KEY_JAVA_TAINT);

      // Ensure a vulnerability has been reported on server side
      var issuesList = adminWsClient.issues().search(new SearchRequest().setTypes(List.of("VULNERABILITY")).setComponentKeys(List.of(PROJECT_KEY_JAVA_TAINT))).getIssuesList();
      assertThat(issuesList).hasSize(1);

      var projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_TAINT, "", "");

      engine.updateProject(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVA_TAINT, null);

      // For SQ 9.6+
      engine.syncServerTaintIssues(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVA_TAINT, MAIN_BRANCH_NAME, null);
      // For SQ < 9.6
      engine.downloadAllServerTaintIssuesForFile(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), projectBinding, "src/main/java/foo/DbHelper.java",
        MAIN_BRANCH_NAME, null);

      var sinkIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");

      assertThat(sinkIssues).hasSize(1);

      var taintIssue = sinkIssues.get(0);
      assertThat(taintIssue.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));

      assertThat(taintIssue.getSeverity()).isEqualTo(IssueSeverity.MAJOR);

      assertThat(taintIssue.getType()).isEqualTo(RuleType.VULNERABILITY);
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        assertThat(taintIssue.getRuleDescriptionContextKey()).isEqualTo("java_se");
      } else {
        assertThat(taintIssue.getRuleDescriptionContextKey()).isNull();
      }
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 2)) {
        assertThat(taintIssue.getCleanCodeAttribute()).hasValue(CleanCodeAttribute.COMPLETE);
        assertThat(taintIssue.getImpacts()).containsExactly(entry(SoftwareQuality.SECURITY, ImpactSeverity.HIGH));
      } else {
        assertThat(taintIssue.getCleanCodeAttribute()).isEmpty();
      }
      assertThat(taintIssue.getFlows()).isNotEmpty();
      var flow = taintIssue.getFlows().get(0);
      assertThat(flow.locations()).isNotEmpty();
      assertThat(flow.locations().get(0).getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(flow.locations().get(flow.locations().size() - 1).getTextRange().getHash()).isIn(hash("request.getParameter(\"user\")"), hash("request.getParameter(\"pass\")"));

      var allTaintIssues = engine.getAllServerTaintIssues(projectBinding, "master");
      assertThat(allTaintIssues)
        .hasSize(1)
        .extracting(ServerTaintIssue::getFilePath)
        .containsExactly("src/main/java/foo/DbHelper.java");
    }

    @Test
    @OnlyOnSonarQube(from = "9.6")
    void shouldUpdateTaintVulnerabilityInLocalStorageWhenChangedOnServer() {
      engine.updateProject(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVA_TAINT, null);
      Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), Set.of(PROJECT_KEY_JAVA_TAINT), events::add, null);
      var projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_TAINT, "", "");
      assertThat(engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java")).isEmpty();

      // check TaintVulnerabilityRaised is received
      analyzeMavenProject("sample-java-taint", PROJECT_KEY_JAVA_TAINT);

      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(TaintVulnerabilityRaisedEvent.class, e -> {
            assertThat(e.getRuleKey()).isEqualTo("javasecurity:S3649");
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });

      var issues = getIssueKeys(adminWsClient, "javasecurity:S3649");
      assertThat(issues).isNotEmpty();
      var issueKey = issues.get(0);

      var taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues)
        .extracting("key", "resolved", "ruleKey", "message", "filePath", "severity", "type")
        .containsOnly(
          tuple(issueKey, false, "javasecurity:S3649", "Change this code to not construct SQL queries directly from user-controlled data.", "src/main/java/foo/DbHelper.java",
            IssueSeverity.MAJOR, RuleType.VULNERABILITY));
      assertThat(taintIssues)
        .extracting("textRange")
        .extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "hash")
        .containsOnly(tuple(11, 35, 11, 64, "d123d615e9ea7cc7e78c784c768f2941"));
      assertThat(taintIssues)
        .flatExtracting("flows")
        .flatExtracting("locations")
        .extracting("message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
        .contains(
          // flow 1 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", "src/main/java/foo/DbHelper.java", 11, 35, 11, 64,
            "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", "src/main/java/foo/Endpoint.java", 9, 18, 9, 46, "a2b69949119440a24e900f15c0939c30"),
          // flow 2 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", "src/main/java/foo/DbHelper.java", 11, 35, 11, 64,
            "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", "src/main/java/foo/Endpoint.java", 8, 18, 8, 46, "2ef54227b849e317e7104dc550be8146"));

      // check IssueChangedEvent is received
      resolveIssueAsWontFix(adminWsClient, issueKey);
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(IssueChangedEvent.class, e -> {
            assertThat(e.getImpactedIssueKeys()).containsOnly(issueKey);
            assertThat(e.getResolved()).isTrue();
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });

      taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues).isEmpty();

      // check IssueChangedEvent is received
      reopenIssue(adminWsClient, issueKey);
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(IssueChangedEvent.class, e -> {
            assertThat(e.getImpactedIssueKeys()).containsOnly(issueKey);
            assertThat(e.getResolved()).isFalse();
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });
      taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues).isNotEmpty();

      // analyze another project under the same project key to close the taint issue
      analyzeMavenProject("sample-java", PROJECT_KEY_JAVA_TAINT);

      // check TaintVulnerabilityClosed is received
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(TaintVulnerabilityClosedEvent.class, e -> {
            assertThat(e.getTaintIssueKey()).isEqualTo(issueKey);
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });
      taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues).isEmpty();
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

    @TempDir
    private Path sonarUserHome;

    @BeforeEach
    void start(TestInfo info) {
      FileUtils.deleteQuietly(sonarUserHome.toFile());
      var globalProps = new HashMap<String, String>();
      globalProps.put("sonar.global.label", "It works");

      var globalConfigBuilder = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.JAVA)
        .setLogOutput((msg, level) -> {
          System.out.println(msg);
        })
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
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);

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
      var securityHotspotsService = new ServerApi(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID)).hotspot();

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
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);

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
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);

      var ruleDetails = engine
        .getActiveRuleDetails(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), javaRuleKey(ORCHESTRATOR, "S4792"), PROJECT_KEY_JAVA_HOTSPOT).get();

      assertThat(ruleDetails.getName()).isEqualTo("Configuring loggers is security-sensitive");
      // HTML description is null for security hotspots when accessed through the deprecated engine API
      // When accessed through the backend service, the rule descriptions are split into sections
      // see its.ConnectedModeBackendTest.returnConvertedDescriptionSectionsForHotspotRules
      assertThat(ruleDetails.getHtmlDescription()).isNull();
    }

    @Test
    void downloadsServerHotspotsForProject() {
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);

      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 1)) {
        engine.syncServerHotspots(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVA_HOTSPOT, "master", null);
        var serverHotspots = engine.getServerHotspots(new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide"), "master", "ide/src/main/java/foo/Foo.java");
        assertThat(serverHotspots)
          .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
          .containsExactly(
            tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, HotspotReviewStatus.TO_REVIEW));
      } else if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
        engine.downloadAllServerHotspots(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVA_HOTSPOT, "master", null);
        var serverHotspots = engine.getServerHotspots(new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide"), "master", "ide/src/main/java/foo/Foo.java");
        assertThat(serverHotspots)
          .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
          .containsExactly(
            tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, HotspotReviewStatus.TO_REVIEW));
      } else {
        engine.downloadAllServerHotspots(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVA_HOTSPOT, "master", null);
        var serverHotspots = engine.getServerHotspots(new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide"), "master", "ide/src/main/java/foo/Foo.java");
        assertThat(serverHotspots).isEmpty();
      }
    }

    @Test
    void downloadsServerHotspotsForFile() {
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);
      var projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide");

      engine.downloadAllServerHotspotsForFile(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), projectBinding, "ide/src/main/java/foo/Foo.java", "master", null);

      var serverHotspots = engine.getServerHotspots(projectBinding, "master", "ide/src/main/java/foo/Foo.java");
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)
        && !ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 1)) {
        assertThat(serverHotspots)
          .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
          .containsExactly(
            tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, HotspotReviewStatus.TO_REVIEW));
      } else {
        assertThat(serverHotspots).isEmpty();
      }
    }
  }

  @Nested
  class RuleDescription {
    private ConnectedSonarLintEngine engine;

    @BeforeEach
    void start() {
      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.JAVA)
        .setLogOutput((msg, level) -> {
          System.out.println(msg);
        })
        .build();
      engine = new ConnectedSonarLintEngineImpl(globalConfig);
    }

    @AfterEach
    void stop() throws ExecutionException, InterruptedException {
      engine.stop(true);
    }

    @Test
    void shouldFailIfNotAuthenticated() {
      var projectKey = "noAuth";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Javascript");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");
      updateProject(engine, projectKey);

      adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
      try {
        var ex = assertThrows(ExecutionException.class,
          () -> engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID_WRONG_CREDENTIALS), javaRuleKey("S106"), projectKey).get());
        assertThat(ex.getCause()).hasMessage("Not authorized. Please check server credentials.");
      } finally {
        adminWsClient.settings().reset(new ResetRequest().setKeys(List.of("sonar.forceAuthentication")));
      }
    }

    @Test
    void shouldContainExtendedDescription() throws Exception {
      var projectKey = "project-with-extended-description";

      provisionProject(ORCHESTRATOR, projectKey, "Project With Extended Description");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");
      updateProject(engine, projectKey);

      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), javaRuleKey("S106"), projectKey).get().getExtendedDescription())
        .isEmpty();

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
      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), javaRuleKey("S106"), projectKey).get().getExtendedDescription())
        .isEqualTo(expected);
    }

    @Test
    void shouldSupportsMarkdownDescription() throws Exception {
      var projectKey = "project-with-markdown-description";

      provisionProject(ORCHESTRATOR, projectKey, "Project With Markdown Description");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-markdown.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Markdown");
      updateProject(engine, projectKey);

      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), "mycompany-java:markdown", projectKey).get().getHtmlDescription())
        .isEqualTo("<h1>Title</h1><ul><li>one</li>\n"
          + "<li>two</li></ul>");
    }

    @Test
    void shouldReturnAllContextsWithOthersSelectedIfNoContextProvided() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-taint-new-backend";

      provisionProject(ORCHESTRATOR, projectKey, "Java With Taint Vulnerabilities");
      // sync is still done by the engine for now
      updateProject(engine, projectKey);
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("project", "javasecurity:S2083", null)).get();

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
      // sync is still done by the engine for now
      updateProject(engine, projectKey);
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("project", "javasecurity:S5131", "spring")).get();

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
      updateProject(engine, projectKey);

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("project", javaRuleKey(ORCHESTRATOR, "S4792"), null))
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

      var api = new ServerApi(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID)).component();
      assertThat(api.getProject("non-existing")).isNotPresent();
      assertThat(api.getProject(projectKey)).isPresent();
    }

    @Test
    void downloadAllProjects(@TempDir Path sonarUserHome) {
      provisionProject(ORCHESTRATOR, "foo-bar1", "Foo1");
      provisionProject(ORCHESTRATOR, "foo-bar2", "Foo2");
      provisionProject(ORCHESTRATOR, "foo-bar3", "Foo3");
      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .build();
      var engine = new ConnectedSonarLintEngineImpl(globalConfig);
      assertThat(engine.downloadAllProjects(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), null)).containsKeys("foo-bar1", "foo-bar2", "foo-bar3");
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

  private static void updateProject(ConnectedSonarLintEngine engine, String projectKey) {
    engine.updateProject(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey), null);
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), backend.getHttpClient(CONNECTION_ID), projectKey, MAIN_BRANCH_NAME, null);
  }

  private static void analyzeMavenProject(String projectDirName, String projectKey) {
    analyzeMavenProject(ORCHESTRATOR, projectDirName, Map.of("sonar.projectKey", projectKey));
  }

  private static SonarLintClient newDummySonarLintClient() {
    return new SonarLintClient() {
      @Override
      public void suggestBinding(SuggestBindingParams params) {

      }

      @Override
      public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
        return CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(Collections.emptyList()));
      }

      @Override
      public void openUrlInBrowser(OpenUrlInBrowserParams params) {

      }

      @Override
      public void showMessage(ShowMessageParams params) {

      }

      @Override
      public void showSmartNotification(ShowSmartNotificationParams params) {

      }

      @Override
      public CompletableFuture<GetClientInfoResponse> getClientInfo() {
        return CompletableFuture.completedFuture(new GetClientInfoResponse(""));
      }

      @Override
      public void showHotspot(ShowHotspotParams params) {

      }

      @Override
      public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
        return null;
      }

      @Override
      public CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params) {
        return null;
      }

      @Override
      public CompletableFuture<Void> startProgress(StartProgressParams params) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public void reportProgress(ReportProgressParams params) {

      }

      @Override
      public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {

      }

      public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
        if (params.getConnectionId().equals(CONNECTION_ID)) {
          return CompletableFuture.completedFuture(new GetCredentialsResponse(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD)));
        } else if (params.getConnectionId().equals(CONNECTION_ID_WRONG_CREDENTIALS)) {
          return CompletableFuture.completedFuture(new GetCredentialsResponse(new UsernamePasswordDto("foo", "bar")));
        } else {
          return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown connection: " + params.getConnectionId()));
        }
      }

      @Override
      public CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params) {
        return CompletableFuture.completedFuture(new SelectProxiesResponse(List.of(ProxyDto.NO_PROXY)));
      }

    };
  }
}
