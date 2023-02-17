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
import com.sonar.orchestrator.OnlyOnSonarQube;
import com.sonar.orchestrator.OrchestratorExtension;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.OrchestratorUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.sonarsource.sonarlint.core.clientapi.backend.HostInfoDto;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetActiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static its.utils.ItUtils.SONAR_VERSION;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarQubeDeveloperEditionTests extends AbstractConnectedTests {

  public static final String CONNECTION_ID = "orchestrator";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
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
    void updateNoAuth() {
      var projectKey = "noAuth";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Javascript");

      adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
      try {
        engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClientNoAuth(), projectKey, null);
        fail("Exception expected");
      } catch (Exception e) {
        assertThat(e).hasMessage("Not authorized. Please check server credentials.");
      } finally {
        adminWsClient.settings().reset(new ResetRequest().setKeys(List.of("sonar.forceAuthentication")));
      }
    }

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

        assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey("myrule"), projectKey).get().getHtmlDescription())
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
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), events::add, null);
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
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), events::add, null);
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

      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

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
      engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY), null);

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
      engine.downloadAllServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, LONG_BRANCH, null);

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

      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_TAINT, null);

      // For SQ 9.6+
      engine.syncServerTaintIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_TAINT, MAIN_BRANCH_NAME, null);
      // For SQ < 9.6
      engine.downloadAllServerTaintIssuesForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding, "src/main/java/foo/DbHelper.java", MAIN_BRANCH_NAME, null);

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
      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_TAINT, null);
      Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVA_TAINT), events::add, null);
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
      var securityHotspotsService = new ServerApi(endpointParams(ORCHESTRATOR), sqHttpClient()).hotspot();

      var remoteHotspot = securityHotspotsService
        .fetch(new GetSecurityHotspotRequestParams(getFirstHotspotKey(PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT));

      assertThat(remoteHotspot).isNotEmpty();
      var actualHotspot = remoteHotspot.get();
      assertThat(actualHotspot.message).isEqualTo("Make sure that this logger's configuration is safe.");
      assertThat(actualHotspot.filePath).isEqualTo("src/main/java/foo/Foo.java");
      assertThat(actualHotspot.textRange).usingRecursiveComparison().isEqualTo(new TextRange(9, 4, 9, 45));
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

      var ruleDetails = engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey(ORCHESTRATOR, "S4792"), PROJECT_KEY_JAVA_HOTSPOT).get();

      assertThat(ruleDetails.getName()).isEqualTo("Configuring loggers is security-sensitive");
      // HTML description is null for security hotspots when accessed through the deprecated engine API
      // When accessed through the backend service, the rule descriptions are split into sections
      // see its.ConnectedModeBackendTest.returnConvertedDescriptionSectionsForHotspotRules
      assertThat(ruleDetails.getHtmlDescription()).isNull();
    }

    @Test
    void downloadsServerHotspotsForProject() {
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);

      engine.downloadAllServerHotspots(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_HOTSPOT, "master", null);

      var serverHotspots = engine.getServerHotspots(new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide"), "master", "ide/src/main/java/foo/Foo.java");
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
        assertThat(serverHotspots)
          .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "resolved")
          .containsExactly(tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, false));
      } else {
        assertThat(serverHotspots).isEmpty();
      }
    }

    @Test
    void downloadsServerHotspotsForFile() {
      updateProject(engine, PROJECT_KEY_JAVA_HOTSPOT);
      var projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide");

      engine.downloadAllServerHotspotsForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding, "ide/src/main/java/foo/Foo.java", "master", null);

      var serverHotspots = engine.getServerHotspots(projectBinding, "master", "ide/src/main/java/foo/Foo.java");
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
        assertThat(serverHotspots)
          .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "resolved")
          .containsExactly(tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, false));
      } else {
        assertThat(serverHotspots).isEmpty();
      }
    }
  }

  @Nested
  class RuleDescription {
    static final String USE_NEW_CLIENT_API = "use_new_client_api";

    private ConnectedSonarLintEngine engine;
    private SonarLintBackend backend;

    @TempDir
    private Path sonarUserHome;

    @BeforeEach
    void start(TestInfo testInfo) {
      var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId(CONNECTION_ID)
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.JAVA)
        .setLogOutput((msg, level) -> {
          System.out.println(msg);
        })
        .build();
      engine = new ConnectedSonarLintEngineImpl(globalConfig);

      if (testInfo.getTags().contains(USE_NEW_CLIENT_API)) {
        backend = new SonarLintBackendImpl(newDummySonarLintClient());
        backend.initialize(
          new InitializeParams(new HostInfoDto("clientName"),"integrationTests", sonarUserHome.resolve("storage"), Collections.emptySet(), Collections.emptyMap(), Set.of(Language.JAVA),
            Collections.emptySet(), false, List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl())), Collections.emptyList(),
            sonarUserHome.toString(), false));
      }
    }

    @AfterEach
    void stop(TestInfo testInfo) {
      if (testInfo.getTags().contains(USE_NEW_CLIENT_API)) {
        backend.shutdown();
      }
      engine.stop(true);
    }

    @Test
    void shouldContainExtendedDescription() throws Exception {
      var projectKey = "project-with-extended-description";

      provisionProject(ORCHESTRATOR, projectKey, "Project With Extended Description");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java");
      updateProject(engine, projectKey);

      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey("S106"), projectKey).get().getExtendedDescription()).isEmpty();

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
      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey("S106"), projectKey).get().getExtendedDescription())
        .isEqualTo(expected);
    }

    @Test
    void shouldSupportsMarkdownDescription() throws Exception {
      var projectKey = "project-with-markdown-description";

      provisionProject(ORCHESTRATOR, projectKey, "Project With Markdown Description");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-markdown.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "SonarLint IT Java Markdown");
      updateProject(engine, projectKey);

      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), "mycompany-java:markdown", projectKey).get().getHtmlDescription())
        .isEqualTo("<h1>Title</h1><ul><li>one</li>\n"
          + "<li>two</li></ul>");
    }

    @Test
    @Tag(USE_NEW_CLIENT_API)
    void shouldContainAllContextsIfNoContextProvided() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-taint-new-backend";

      provisionProject(ORCHESTRATOR, projectKey, "Java With Taint Vulnerabilities");
      // sync is still done by the engine for now
      updateProject(engine, projectKey);
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("project", "javasecurity:S2083", null)).get();

      var description = activeRuleDetailsResponse.details().getDescription();

      if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        // no description sections at that time
        assertThat(description.isRight()).isFalse();
      } else {
        var extendedDescription = description.getRight();
        assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
        assertThat(extendedDescription.getTabs())
          .flatExtracting(this::extractTabContent)
          .contains(
            "Why is this an issue?",
            "<p>Path injections occur when an application uses untrusted data to construct a file path and access this file without validating its path first.</p>\n" +
              "<p>A user with malicious intent would inject specially crafted values, such as <code>../</code>, to change the initial intended path. The resulting\n" +
              "path would resolve somewhere in the filesystem where the user should not normally have access to.</p>\n" +
              "<h3>What is the potential impact?</h3>\n" +
              "<p>A web application is vulnerable to path injection and an attacker is able to exploit it.</p>\n" +
              "<p>The files that can be affected are limited by the permission of the process that runs the application. Worst case scenario: the process runs with\n" +
              "root privileges on Linux, and therefore any file can be affected.</p>\n" +
              "<p>Below are some real-world scenarios that illustrate some impacts of an attacker exploiting the vulnerability.</p>\n" +
              "<h4>Override or delete arbitrary files</h4>\n" +
              "<p>The injected path component tampers with the location of a file the application is supposed to delete or write into. The vulnerability is exploited\n" +
              "to remove or corrupt files that are critical for the application or for the system to work properly.</p>\n" +
              "<p>It could result in data being lost or the application being unavailable.</p>\n" +
              "<h4>Read arbitrary files</h4>\n" +
              "<p>The injected path component tampers with the location of a file the application is supposed to read and output. The vulnerability is exploited to\n" +
              "leak the content of arbitrary files from the file system, including sensitive files like SSH private keys.</p>",
            "How can I fix it?",
            // actual description not checked because it changes frequently between versions
            "java_se", "Java SE",
            "<h4>How can I fix it in another component or framework?</h4>\n"
              + "<p>Although the main framework or component you use in your project is not listed, you may find helpful content in the instructions we provide.</p>\n"
              + "<p>Caution: The libraries mentioned in these instructions may not be appropriate for your code.</p>\n"
              + "<p>\n"
              + "<ul>\n"
              + "    <li>Do use libraries that are compatible with the frameworks you are using.</li>\n"
              + "    <li>Don't blindly copy and paste the fix-ups into your code.</li>\n"
              + "</ul>\n"
              + "<h4>Help us improve</h4>\n"
              + "<p>Let us know if the instructions we provide do not work for you.\n"
              + "    Tell us which framework you use and why our solution does not work by submitting an idea on the SonarLint product-board.</p>\n"
              + "<a href=\"https://portal.productboard.com/sonarsource/4-sonarlint/submit-idea\">Submit an idea</a>\n"
              + "<p>We will do our best to provide you with more relevant instructions in the future.</p>",
            "others", "Others",
            "More Info",
            "<h3>Standards</h3>\n" +
              "<ul>\n" +
              "  <li> <a href=\"https://owasp.org/Top10/A01_2021-Broken_Access_Control/\">OWASP Top 10 2021 Category A1</a> - Broken Access Control </li>\n" +
              "  <li> <a href=\"https://owasp.org/Top10/A03_2021-Injection/\">OWASP Top 10 2021 Category A3</a> - Injection </li>\n" +
              "  <li> <a href=\"https://www.owasp.org/index.php/Top_10-2017_A1-Injection\">OWASP Top 10 2017 Category A1</a> - Injection </li>\n" +
              "  <li> <a href=\"https://www.owasp.org/index.php/Top_10-2017_A5-Broken_Access_Control\">OWASP Top 10 2017 Category A5</a> - Broken Access Control </li>\n" +
              "  <li> <a href=\"https://cwe.mitre.org/data/definitions/20\">MITRE, CWE-20</a> - Improper Input Validation </li>\n" +
              "  <li> <a href=\"https://cwe.mitre.org/data/definitions/22\">MITRE, CWE-22</a> - Improper Limitation of a Pathname to a Restricted Directory ('Path\n" +
              "  Traversal') </li>\n" +
              "</ul><br/><br/><h3>Clean Code Principles</h3>\n" +
              "<h4>Defense-In-Depth</h4>\n" +
              "<p>\n" +
              "    Applications and infrastructure benefit greatly from relying on multiple security mechanisms\n" +
              "    layered on top of each other. If one security mechanism fails, there is a high probability\n" +
              "    that the subsequent layers of security will successfully defend against the attack.\n" +
              "</p>\n" +
              "<p>A non-exhaustive list of these code protection ramparts includes the following:</p>\n" +
              "<ul>\n" +
              "    <li>Minimizing the attack surface of the code</li>\n" +
              "    <li>Application of the principle of least privilege</li>\n" +
              "    <li>Validation and sanitization of data</li>\n" +
              "    <li>Encrypting incoming, outgoing, or stored data with secure cryptography</li>\n" +
              "    <li>Ensuring that internal errors cannot disrupt the overall runtime</li>\n" +
              "    <li>Separation of tasks and access to information</li>\n" +
              "</ul>\n" +
              "\n" +
              "<p>\n" +
              "    Note that these layers must be simple enough to use in an everyday workflow. Security\n" +
              "    measures should not break usability.\n" +
              "</p><br/><br/><h4>Never Trust User Input</h4>\n" +
              "<p>\n" +
              "    Applications must treat all user input and, more generally, all third-party data as\n" +
              "    attacker-controlled data.\n" +
              "</p>\n" +
              "<p>\n" +
              "    The application must determine where the third-party data comes from and treat that data\n" +
              "    source as an attack vector. Two rules apply:\n" +
              "</p>\n" +
              "\n" +
              "<p>\n" +
              "    First, before using it in the application&apos;s business logic, the application must\n" +
              "    validate the attacker-controlled data against predefined formats, such as:\n" +
              "</p>\n" +
              "<ul>\n" +
              "    <li>Character sets</li>\n" +
              "    <li>Sizes</li>\n" +
              "    <li>Types</li>\n" +
              "    <li>Or any strict schema</li>\n" +
              "</ul>\n" +
              "\n" +
              "<p>\n" +
              "    Second, the application must sanitize string data before inserting it into interpreted\n" +
              "    contexts (client-side code, file paths, SQL queries). Unsanitized code can corrupt the\n" +
              "    application&apos;s logic.\n" +
              "</p>");
      }
    }

    @Test
    @Tag(USE_NEW_CLIENT_API)
    void shouldContainsOnlyAppropriateContextIfContextProvided() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-taint-rule-context-new-backend";

      provisionProject(ORCHESTRATOR, projectKey, "Java With Taint Vulnerabilities And Multiple Contexts");
      // sync is still done by the engine for now
      updateProject(engine, projectKey);
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("project", "javasecurity:S5131", "spring")).get();

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
            "<p>Reflected cross-site scripting (XSS) occurs in a web application when the application retrieves data like parameters or headers from an incoming\n"
              + "HTTP request and inserts it into its HTTP response without first sanitizing it. The most common cause is the insertion of GET parameters.</p>\n"
              + "<p>When well-intentioned users open a link to a page that is vulnerable to reflected XSS, they are exposed to attacks that target their own\n"
              + "browser.</p>\n"
              + "<p>A user with malicious intent carefully crafts the link beforehand.</p>\n"
              + "<p>After creating this link, the attacker must use phishing techniques to ensure that his target users click on the link.</p>\n"
              + "<h3>What is the potential impact?</h3>\n"
              + "<p>A well-intentioned user opens a malicious link that injects data into the web application. This data can be text, but it can also be arbitrary code\n"
              + "that can be interpreted by the target user’s browser, such as HTML, CSS, or JavaScript.</p>\n"
              + "<p>Below are some real-world scenarios that illustrate some impacts of an attacker exploiting the vulnerability.</p>\n"
              + "<h4>Vandalism on the front-end website</h4>\n"
              + "<p>The malicious link defaces the target web application from the perspective of the user who is the victim. This may result in loss of integrity and\n"
              + "theft of the benevolent user’s data.</p>\n"
              + "<h4>Identity spoofing</h4>\n"
              + "<p>The forged link injects malicious code into the web application. The code enables identity spoofing thanks to cookie theft.</p>\n"
              + "<h4>Record user activity</h4>\n"
              + "<p>The forged link injects malicious code into the web application. To leak confidential information, attackers can inject code that records keyboard\n"
              + "activity (keylogger) and even requests access to other devices, such as the camera or microphone.</p>\n"
              + "<h4>Chaining XSS with other vulnerabilities</h4>\n"
              + "<p>In many cases, bug hunters and attackers chain cross-site scripting vulnerabilities with other vulnerabilities to maximize their impact.<br> For\n"
              + "example, an XSS can be used as the first step to exploit more dangerous vulnerabilities or features that require higher privileges, such as a code\n"
              + "injection vulnerability in the admin control panel of a web application.</p>",
            "How can I fix it?",
            "<p>The following code is vulnerable to cross-site scripting because it returns an HTML response that contains user input.</p>\n"
              + "<p>If you do not intend to send HTML code to clients, the vulnerability can be fixed by specifying the type of data returned in the response. For\n"
              + "example, you can use the <code>produces</code> property of the <code>GetMapping</code> annotation.</p>\n"
              + "<h4>Noncompliant code example</h4>\n"
              + "<pre data-diff-id=\"1\" data-diff-type=\"noncompliant\">\n"
              + "@RestController\n"
              + "public class ApiController\n"
              + "{\n"
              + "    @GetMapping(value = \"/endpoint\")\n"
              + "    public String endpoint(@RequestParam(\"input\") input)\n"
              + "    {\n"
              + "        return input;\n"
              + "    }\n"
              + "}\n"
              + "</pre>\n"
              + "<h4>Compliant solution</h4>\n"
              + "<pre data-diff-id=\"1\" data-diff-type=\"compliant\">\n"
              + "@RestController\n"
              + "public class ApiController\n"
              + "{\n"
              + "    @GetMapping(value = \"/endpoint\", produces = \"text/plain\")\n"
              + "    public String endpoint(@RequestParam(\"input\") input)\n"
              + "    {\n"
              + "        return input;\n"
              + "    }\n"
              + "}\n"
              + "</pre>\n"
              + "<h3>How does this work?</h3>\n"
              + "<p>If the HTTP response is HTML code, it is highly recommended to use a template engine like <a href=\"https://www.thymeleaf.org/\">Thymeleaf</a> to\n"
              + "generate it. This template engine separates the view from the business logic and automatically encodes the output of variables, drastically reducing\n"
              + "the risk of cross-site scripting vulnerabilities.</p>\n"
              + "<p>If you do not intend to send HTML code to clients, the vulnerability can be fixed by specifying the type of data returned in the response with the\n"
              + "<code>content-type</code> HTTP header. This header tells the browser that the response does not contain HTML code and should not be parsed and\n"
              + "interpreted as HTML. Thus, the response is not vulnerable to reflected cross-site scripting.</p>\n"
              + "<p>For example, setting the content-type to <code>text/plain</code> allows to safely reflect user input since browsers will not try to parse and\n"
              + "execute the response.</p>\n"
              + "<h3>Pitfalls</h3>\n"
              + "<h4>Content-types</h4>\n"
              + "<p>Be aware that there are more content-types than <code>text/html</code> that allow to execute JavaScript code in a browser and thus are prone to\n"
              + "cross-site scripting vulnerabilities.<br> The following content-types are known to be affected:</p>\n"
              + "<ul>\n"
              + "  <li> application/mathml+xml </li>\n"
              + "  <li> application/rdf+xml </li>\n"
              + "  <li> application/vnd.wap.xhtml+xml </li>\n"
              + "  <li> application/xhtml+xml </li>\n"
              + "  <li> application/xml </li>\n"
              + "  <li> image/svg+xml </li>\n"
              + "  <li> multipart/x-mixed-replace </li>\n"
              + "  <li> text/html </li>\n"
              + "  <li> text/rdf </li>\n"
              + "  <li> text/xml </li>\n"
              + "  <li> text/xsl </li>\n"
              + "</ul>\n"
              + "<h4>The limits of validation</h4>\n"
              + "<p>Validation of user inputs is a good practice to protect against various injection attacks. But for XSS, validation on its own is not the\n"
              + "recommended approach.</p>\n"
              + "<p>As an example, filtering out user inputs based on a deny-list will never fully prevent XSS vulnerability from being exploited. This practice is\n"
              + "sometimes used by web application firewalls. It is only a matter of time for malicious users to find the exploitation payload that will defeat the\n"
              + "filters.</p>\n"
              + "<p>Another example is applications that allow users or third-party services to send HTML content to be used by the application. A common approach is\n"
              + "trying to parse HTML and strip sensitive HTML tags. Again, this deny-list approach is vulnerable by design: maintaining a list of sensitive HTML tags,\n"
              + "in the long run, is very difficult.</p>\n"
              + "<p>A preferred option is to use Markdown in conjunction with a parser that removes embedded HTML and restricts the use of \"javascript:\" URI.</p>\n"
              + "<h3>Going the extra mile</h3>\n"
              + "<h4>Content Security Policy (CSP) Header</h4>\n"
              + "<p>With a defense-in-depth security approach, the <strong>CSP</strong> response header can be added to instruct client browsers to\n"
              + "<strong>block</strong> loading data that does not meet the application’s security requirements. If configured correctly, this can prevent any attempt\n"
              + "to exploit XSS in the application.<br> <a href=\"https://web.dev/csp-xss/\">Learn more here.</a></p>",
            "More Info",
            "<h3>Documentation</h3>\n"
              + "<ul>\n"
              + "  <li> <a href=\"https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html\">OWASP Cheat Sheet</a> - XSS\n"
              + "  Prevention Cheat Sheet </li>\n"
              + "  <li> <a href=\"https://javadoc.io/doc/org.owasp.encoder/encoder/latest/index.html\">OWASP Encoder</a> </li>\n"
              + "  <li> <a href=\"https://spring.io/guides/gs/securing-web/\">Spring.io, Securing a Web Application</a> </li>\n"
              + "  <li> <a href=\"https://www.thymeleaf.org/doc/tutorials/2.1/usingthymeleaf.html\">Thymeleaf.org, Tutorial: Using Thymeleaf</a> </li>\n"
              + "</ul>\n"
              + "<h3>Articles &amp; Blog Posts</h3>\n"
              + "<ul>\n"
              + "  <li> <a href=\"https://blog.sonarsource.com/wordpress-stored-xss-vulnerability\">SonarSource, WordPress 5.8.2 Stored XSS Vulnerability</a> </li>\n"
              + "  <li> <a href=\"https://blog.sonarsource.com/ghost-admin-takeover\">SonarSource, Ghost CMS 4.3.2 - Cross-Origin Admin Takeover</a> </li>\n"
              + "  <li> <a href=\"https://samy.pl/myspace/\">Samy Kamkar, The MySpace Worm</a> </li>\n"
              + "</ul>\n"
              + "<h3>Conference Presentations</h3>\n"
              + "<ul>\n"
              + "  <li> <a href=\"https://www.youtube.com/watch?v=ksq7e6UUDag\">DEF CON Safe Mode Red Team Village, Ray Doyle, Weaponized XSS Moving Beyond Alert</a>\n"
              + "  </li>\n"
              + "</ul>\n"
              + "<h3>Standards</h3>\n"
              + "<ul>\n"
              + "  <li> <a href=\"https://owasp.org/Top10/A03_2021-Injection/\">OWASP Top 10 2021 Category A3</a> - Injection </li>\n"
              + "  <li> <a href=\"https://owasp.org/www-project-top-ten/2017/A7_2017-Cross-Site_Scripting_(XSS)\">OWASP Top 10 2017 Category A7</a> - Cross-Site\n"
              + "  Scripting (XSS) </li>\n"
              + "  <li> <a href=\"https://cwe.mitre.org/data/definitions/79.html\">MITRE, CWE-79</a> - Improper Neutralization of Input During Web Page Generation\n"
              + "  ('Cross-site Scripting') </li>\n"
              + "</ul><br/><br/><h3>Clean Code Principles</h3>\n"
              + "<h4>Defense-In-Depth</h4>\n"
              + "<p>\n"
              + "    Applications and infrastructure benefit greatly from relying on multiple security mechanisms\n"
              + "    layered on top of each other. If one security mechanism fails, there is a high probability\n"
              + "    that the subsequent layers of security will successfully defend against the attack.\n"
              + "</p>\n"
              + "<p>A non-exhaustive list of these code protection ramparts includes the following:</p>\n"
              + "<ul>\n"
              + "    <li>Minimizing the attack surface of the code</li>\n"
              + "    <li>Application of the principle of least privilege</li>\n"
              + "    <li>Validation and sanitization of data</li>\n"
              + "    <li>Encrypting incoming, outgoing, or stored data with secure cryptography</li>\n"
              + "    <li>Ensuring that internal errors cannot disrupt the overall runtime</li>\n"
              + "    <li>Separation of tasks and access to information</li>\n"
              + "</ul>\n"
              + "\n"
              + "<p>\n"
              + "    Note that these layers must be simple enough to use in an everyday workflow. Security\n"
              + "    measures should not break usability.\n"
              + "</p><br/><br/><h4>Never Trust User Input</h4>\n"
              + "<p>\n"
              + "    Applications must treat all user input and, more generally, all third-party data as\n"
              + "    attacker-controlled data.\n"
              + "</p>\n"
              + "<p>\n"
              + "    The application must determine where the third-party data comes from and treat that data\n"
              + "    source as an attack vector. Two rules apply:\n"
              + "</p>\n"
              + "\n"
              + "<p>\n"
              + "    First, before using it in the application&apos;s business logic, the application must\n"
              + "    validate the attacker-controlled data against predefined formats, such as:\n"
              + "</p>\n"
              + "<ul>\n"
              + "    <li>Character sets</li>\n"
              + "    <li>Sizes</li>\n"
              + "    <li>Types</li>\n"
              + "    <li>Or any strict schema</li>\n"
              + "</ul>\n"
              + "\n"
              + "<p>\n"
              + "    Second, the application must sanitize string data before inserting it into interpreted\n"
              + "    contexts (client-side code, file paths, SQL queries). Unsanitized code can corrupt the\n"
              + "    application&apos;s logic.\n"
              + "</p>");
      }
    }

    @Test
    @Tag(USE_NEW_CLIENT_API)
    @OnlyOnSonarQube(from = "9.7")
    void shouldEmulateDescriptionSectionsForHotspotRules() throws ExecutionException, InterruptedException {
      var projectKey = "sample-java-hotspot-new-backend";

      provisionProject(ORCHESTRATOR, projectKey, "Java With Security Hotspots");
      updateProject(engine, projectKey);

      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, projectKey, false)))));

      var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("project", javaRuleKey(ORCHESTRATOR, "S4792"), null))
        .get();

      var extendedDescription = activeRuleDetailsResponse.details().getDescription().getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
      assertThat(extendedDescription.getTabs())
        .flatExtracting(this::extractTabContent)
        .containsOnly(
          "What's the risk?",
          "<p>Configuring loggers is security-sensitive. It has led in the past to the following vulnerabilities:</p>\n" +
            "<ul>\n" +
            "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2018-0285\">CVE-2018-0285</a> </li>\n" +
            "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2000-1127\">CVE-2000-1127</a> </li>\n" +
            "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2017-15113\">CVE-2017-15113</a> </li>\n" +
            "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2015-5742\">CVE-2015-5742</a> </li>\n" +
            "</ul>\n" +
            "<p>Logs are useful before, during and after a security incident.</p>\n" +
            "<ul>\n" +
            "  <li> Attackers will most of the time start their nefarious work by probing the system for vulnerabilities. Monitoring this activity and stopping it\n" +
            "  is the first step to prevent an attack from ever happening. </li>\n" +
            "  <li> In case of a successful attack, logs should contain enough information to understand what damage an attacker may have inflicted. </li>\n" +
            "</ul>\n" +
            "<p>Logs are also a target for attackers because they might contain sensitive information. Configuring loggers has an impact on the type of information\n" +
            "logged and how they are logged.</p>\n" +
            "<p>This rule flags for review code that initiates loggers configuration. The goal is to guide security code reviews.</p>\n" +
            "<h2>Exceptions</h2>\n" +
            "<p>Log4J 1.x is not covered as it has reached <a href=\"https://blogs.apache.org/foundation/entry/apache_logging_services_project_announces\">end of\n" +
            "life</a>.</p>\n",
          "Assess the risk",
          "<h2>Ask Yourself Whether</h2>\n" +
            "<ul>\n" +
            "  <li> unauthorized users might have access to the logs, either because they are stored in an insecure location or because the application gives\n" +
            "  access to them. </li>\n" +
            "  <li> the logs contain sensitive information on a production server. This can happen when the logger is in debug mode. </li>\n" +
            "  <li> the log can grow without limit. This can happen when additional information is written into logs every time a user performs an action and the\n" +
            "  user can perform the action as many times as he/she wants. </li>\n" +
            "  <li> the logs do not contain enough information to understand the damage an attacker might have inflicted. The loggers mode (info, warn, error)\n" +
            "  might filter out important information. They might not print contextual information like the precise time of events or the server hostname. </li>\n" +
            "  <li> the logs are only stored locally instead of being backuped or replicated. </li>\n" +
            "</ul>\n" +
            "<p>There is a risk if you answered yes to any of those questions.</p>\n" +
            "<h2>Sensitive Code Example</h2>\n" +
            "<p>This rule supports the following libraries: Log4J, <code>java.util.logging</code> and Logback</p>\n" +
            "<pre>\n" +
            "// === Log4J 2 ===\n" +
            "import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;\n" +
            "import org.apache.logging.log4j.Level;\n" +
            "import org.apache.logging.log4j.core.*;\n" +
            "import org.apache.logging.log4j.core.config.*;\n" +
            "\n" +
            "// Sensitive: creating a new custom configuration\n" +
            "abstract class CustomConfigFactory extends ConfigurationFactory {\n" +
            "    // ...\n" +
            "}\n" +
            "\n" +
            "class A {\n" +
            "    void foo(Configuration config, LoggerContext context, java.util.Map&lt;String, Level&gt; levelMap,\n" +
            "            Appender appender, java.io.InputStream stream, java.net.URI uri,\n" +
            "            java.io.File file, java.net.URL url, String source, ClassLoader loader, Level level, Filter filter)\n" +
            "            throws java.io.IOException {\n" +
            "        // Creating a new custom configuration\n" +
            "        ConfigurationBuilderFactory.newConfigurationBuilder();  // Sensitive\n" +
            "\n" +
            "        // Setting loggers level can result in writing sensitive information in production\n" +
            "        Configurator.setAllLevels(\"com.example\", Level.DEBUG);  // Sensitive\n" +
            "        Configurator.setLevel(\"com.example\", Level.DEBUG);  // Sensitive\n" +
            "        Configurator.setLevel(levelMap);  // Sensitive\n" +
            "        Configurator.setRootLevel(Level.DEBUG);  // Sensitive\n" +
            "\n" +
            "        config.addAppender(appender); // Sensitive: this modifies the configuration\n" +
            "\n" +
            "        LoggerConfig loggerConfig = config.getRootLogger();\n" +
            "        loggerConfig.addAppender(appender, level, filter); // Sensitive\n" +
            "        loggerConfig.setLevel(level); // Sensitive\n" +
            "\n" +
            "        context.setConfigLocation(uri); // Sensitive\n" +
            "\n" +
            "        // Load the configuration from a stream or file\n" +
            "        new ConfigurationSource(stream);  // Sensitive\n" +
            "        new ConfigurationSource(stream, file);  // Sensitive\n" +
            "        new ConfigurationSource(stream, url);  // Sensitive\n" +
            "        ConfigurationSource.fromResource(source, loader);  // Sensitive\n" +
            "        ConfigurationSource.fromUri(uri);  // Sensitive\n" +
            "    }\n" +
            "}\n" +
            "</pre>\n" +
            "<pre>\n" +
            "// === java.util.logging ===\n" +
            "import java.util.logging.*;\n" +
            "\n" +
            "class M {\n" +
            "    void foo(LogManager logManager, Logger logger, java.io.InputStream is, Handler handler)\n" +
            "            throws SecurityException, java.io.IOException {\n" +
            "        logManager.readConfiguration(is); // Sensitive\n" +
            "\n" +
            "        logger.setLevel(Level.FINEST); // Sensitive\n" +
            "        logger.addHandler(handler); // Sensitive\n" +
            "    }\n" +
            "}\n" +
            "</pre>\n" +
            "<pre>\n" +
            "// === Logback ===\n" +
            "import ch.qos.logback.classic.util.ContextInitializer;\n" +
            "import ch.qos.logback.core.Appender;\n" +
            "import ch.qos.logback.classic.joran.JoranConfigurator;\n" +
            "import ch.qos.logback.classic.spi.ILoggingEvent;\n" +
            "import ch.qos.logback.classic.*;\n" +
            "\n" +
            "class M {\n" +
            "    void foo(Logger logger, Appender&lt;ILoggingEvent&gt; fileAppender) {\n" +
            "        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, \"config.xml\"); // Sensitive\n" +
            "        JoranConfigurator configurator = new JoranConfigurator(); // Sensitive\n" +
            "\n" +
            "        logger.addAppender(fileAppender); // Sensitive\n" +
            "        logger.setLevel(Level.DEBUG); // Sensitive\n" +
            "    }\n" +
            "}\n" +
            "</pre>\n",
          "How can I fix it?",
          "<h2>Recommended Secure Coding Practices</h2>\n" +
            "<ul>\n" +
            "  <li> Check that your production deployment doesn’t have its loggers in \"debug\" mode as it might write sensitive information in logs. </li>\n" +
            "  <li> Production logs should be stored in a secure location which is only accessible to system administrators. </li>\n" +
            "  <li> Configure the loggers to display all warnings, info and error messages. Write relevant information such as the precise time of events and the\n" +
            "  hostname. </li>\n" +
            "  <li> Choose log format which is easy to parse and process automatically. It is important to process logs rapidly in case of an attack so that the\n" +
            "  impact is known and limited. </li>\n" +
            "  <li> Check that the permissions of the log files are correct. If you index the logs in some other service, make sure that the transfer and the\n" +
            "  service are secure too. </li>\n" +
            "  <li> Add limits to the size of the logs and make sure that no user can fill the disk with logs. This can happen even when the user does not control\n" +
            "  the logged information. An attacker could just repeat a logged action many times. </li>\n" +
            "</ul>\n" +
            "<p>Remember that configuring loggers properly doesn’t make them bullet-proof. Here is a list of recommendations explaining on how to use your\n" +
            "logs:</p>\n" +
            "<ul>\n" +
            "  <li> Don’t log any sensitive information. This obviously includes passwords and credit card numbers but also any personal information such as user\n" +
            "  names, locations, etc…\u200B Usually any information which is protected by law is good candidate for removal. </li>\n" +
            "  <li> Sanitize all user inputs before writing them in the logs. This includes checking its size, content, encoding, syntax, etc…\u200B As for any user\n" +
            "  input, validate using whitelists whenever possible. Enabling users to write what they want in your logs can have many impacts. It could for example\n" +
            "  use all your storage space or compromise your log indexing service. </li>\n" +
            "  <li> Log enough information to monitor suspicious activities and evaluate the impact an attacker might have on your systems. Register events such as\n" +
            "  failed logins, successful logins, server side input validation failures, access denials and any important transaction. </li>\n" +
            "  <li> Monitor the logs for any suspicious activity. </li>\n" +
            "</ul>\n" +
            "<h2>See</h2>\n" +
            "<ul>\n" +
            "  <li> <a href=\"https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/\">OWASP Top 10 2021 Category A9</a> - Security Logging and\n" +
            "  Monitoring Failures </li>\n" +
            "  <li> <a href=\"https://www.owasp.org/www-project-top-ten/2017/A3_2017-Sensitive_Data_Exposure\">OWASP Top 10 2017 Category A3</a> - Sensitive Data\n" +
            "  Exposure </li>\n" +
            "  <li> <a href=\"https://owasp.org/www-project-top-ten/2017/A10_2017-Insufficient_Logging%2526Monitoring\">OWASP Top 10 2017 Category A10</a> -\n" +
            "  Insufficient Logging &amp; Monitoring </li>\n" +
            "  <li> <a href=\"https://cwe.mitre.org/data/definitions/117\">MITRE, CWE-117</a> - Improper Output Neutralization for Logs </li>\n" +
            "  <li> <a href=\"https://cwe.mitre.org/data/definitions/532\">MITRE, CWE-532</a> - Information Exposure Through Log Files </li>\n" +
            "  <li> <a href=\"https://www.sans.org/top25-software-errors/#cat3\">SANS Top 25</a> - Porous Defenses </li>\n" +
            "</ul>");
    }

    private List<Object> extractTabContent(ActiveRuleDescriptionTabDto tab) {
      if (tab.getContent().isLeft()) {
        return List.of(tab.getTitle(), tab.getContent().getLeft().getHtmlContent());
      }
      return tab.getContent().getRight().getContextualSections().stream().flatMap(s -> Stream.of(tab.getTitle(), s.getHtmlContent(), s.getContextKey(), s.getDisplayName()))
        .collect(Collectors.toList());
    }

    private SonarLintClient newDummySonarLintClient() {
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
        public CompletableFuture<GetHostInfoResponse> getHostInfo() {
          return CompletableFuture.completedFuture(new GetHostInfoResponse(""));
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
        public HttpClient getHttpClient(String connectionId) {
          return sqHttpClient();
        }

        @Override
        public HttpClient getHttpClientNoAuth(String forUrl) {
          return sqHttpClient();
        }
      };
    }
  }

  @Nested
  class SonarProjectService {

    @Test
    void getProject() {
      var projectKey = "sample-project";
      provisionProject(ORCHESTRATOR, projectKey, "Sample Project");

      var api = new ServerApi(endpointParams(ORCHESTRATOR), sqHttpClient()).component();
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
      assertThat(engine.downloadAllProjects(endpointParams(ORCHESTRATOR), sqHttpClient(), null)).containsKeys("foo-bar1", "foo-bar2", "foo-bar3");
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
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), null);
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, MAIN_BRANCH_NAME, null);
  }

  private static void analyzeMavenProject(String projectDirName, String projectKey) {
    analyzeMavenProject(ORCHESTRATOR, projectDirName, Map.of("sonar.projectKey", projectKey));
  }
}
