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

import its.utils.ConsoleLogOutput;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.WsResponse;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.usertokens.GenerateRequest;
import org.sonarqube.ws.client.usertokens.RevokeRequest;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.HTML;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.KOTLIN;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PYTHON;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.RUBY;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.SCALA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.XML;

@Tag("SonarCloud")
class SonarCloudTests extends AbstractConnectedTests {
  private static final String SONAR_JAVA_FILE_SUFFIXES = "sonar.java.file.suffixes";
  private static final String SONARCLOUD_STAGING_URL = "https://sc-staging.io";
  private static final String SONARCLOUD_ORGANIZATION = "sonarlint-it";
  private static final String SONARCLOUD_USER = "sonarlint-it";
  private static final String SONARCLOUD_PASSWORD = System.getenv("SONARCLOUD_IT_PASSWORD");

  private static final String TIMESTAMP = Long.toString(Instant.now().toEpochMilli());
  private static final String TOKEN_NAME = "SLCORE-IT-" + TIMESTAMP;
  private static final String PROJECT_KEY_JAVA = "sample-java";


  public static final String CONNECTION_ID = "sonarcloud";

  private static WsClient adminWsClient;
  @TempDir
  private static Path sonarUserHome;

  private static SonarLintAnalysisEngine engine;

  private static int randomPositiveInt;

  private static SonarLintRpcServer backend;
  private static String sonarcloudUserToken;
  private static final Set<String> openedConfigurationScopeIds = new HashSet<>();
  private static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();

  @BeforeAll
  static void prepare() throws Exception {
    System.setProperty("sonarlint.internal.sonarcloud.url", SONARCLOUD_STAGING_URL);

    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    var languages = Set.of(JAVA, PHP, JS, PYTHON, HTML, RUBY, KOTLIN, SCALA, XML);
    var featureFlags = new FeatureFlagsDto(false, true, true, false, true, true, false, true);
    backend.initialize(
      new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, featureFlags, sonarUserHome.resolve("storage"),
        sonarUserHome.resolve("work"), emptySet(), emptyMap(), languages, emptySet(), emptyList(),
        List.of(new SonarCloudConnectionConfigurationDto(CONNECTION_ID, SONARCLOUD_ORGANIZATION, true)), sonarUserHome.toString(),
        emptyMap(), false, null));
    randomPositiveInt = new Random().nextInt() & Integer.MAX_VALUE;

    adminWsClient = newAdminWsClient();
    sonarcloudUserToken = adminWsClient.userTokens()
      .generate(new GenerateRequest().setName(TOKEN_NAME))
      .getToken();

    restoreProfile("java-sonarlint.xml");
    provisionProject(PROJECT_KEY_JAVA, "Sample Java");
    associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");


    // Build project to have bytecode
    runMaven(Paths.get("projects/sample-java"), "clean", "compile");

    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");

    var logOutput = new ConsoleLogOutput(false);

    engine = new SonarLintAnalysisEngine(EngineConfiguration.builder()
      .setLogOutput(logOutput)
      .setSonarLintUserHome(sonarUserHome)
      .setExtraProperties(globalProps)
      .build(), backend, CONNECTION_ID);
  }

  @AfterAll
  static void cleanup() throws Exception {
    adminWsClient.userTokens()
      .revoke(new RevokeRequest().setName(TOKEN_NAME));

    var request = new PostRequest("api/projects/bulk_delete");
    request.setParam("q", "-" + randomPositiveInt);
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertIsOk(response);
    }

    try {
      engine.stop();
    } catch (Exception e) {
      // Ignore
    }
    backend.shutdown().get();
  }

  private static void associateProjectToQualityProfile(String projectKey, String language, String profileName) {
    var request = new PostRequest("api/qualityprofiles/add_project");
    request.setParam("language", language);
    request.setParam("project", projectKey(projectKey));
    request.setParam("qualityProfile", profileName);
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertIsOk(response);
    }
  }

  private static void restoreProfile(String profile) {
    var backupFile = new File("src/test/resources/" + profile);
    // XXX can't use RestoreRequest because of a bug
    var request = new PostRequest("api/qualityprofiles/restore");
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    request.setPart("backup", new PostRequest.Part(MediaTypes.XML, backupFile));
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertIsOk(response);
    }
  }

  private static void provisionProject(String key, String name) {
    var request = new PostRequest("api/projects/create");
    request.setParam("name", name);
    request.setParam("project", projectKey(key));
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertIsOk(response);
    }
  }

  private static String projectKey(String key) {
    return "sonarlint-its-" + key + "-" + randomPositiveInt;
  }

  @AfterEach
  void cleanup_after_each() {
    openedConfigurationScopeIds.forEach(configScopeId -> backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(configScopeId)));
    openedConfigurationScopeIds.clear();
    analysisReadinessByConfigScopeId.clear();
    rpcClientLogs.clear();
  }

  @Test
  void match_main_branch_by_default() throws ExecutionException, InterruptedException {
    var configScopeId = "match_main_branch_by_default";
    openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA);
    waitForAnalysisToBeReady(configScopeId);

    var sonarProjectBranch = backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(configScopeId)).get();

    await().untilAsserted(() -> assertThat(sonarProjectBranch.getMatchedSonarProjectBranch()).isEqualTo(MAIN_BRANCH_NAME));
  }

  @Test
  void getAllProjects() {
    provisionProject("foo-bar", "Foo");
    var getAllProjectsParams = new GetAllProjectsParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION,
      Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD))));

    waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(backend.getConnectionService().getAllProjects(getAllProjectsParams).get().getSonarProjects())
      .extracting(SonarProjectDto::getKey)
      .contains(projectKey("foo-bar")));
  }

  @Test
  void testRuleDescription() throws Exception {
    openBoundConfigurationScope("testRuleDescription", PROJECT_KEY_JAVA);

    var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("testRuleDescription", "java:S106", null)).get();

    assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(0).getContent().getLeft().getHtmlContent())
      .contains("logs serve as a record of events within an application");
  }

  @Test
  void verifyExtendedDescription() throws Exception {
    var configScopeId = "verifyExtendedDescription";
    var ruleKey = "java:S106";

    var extendedDescription = "my dummy extended description";

    WsRequest request = new PostRequest("/api/rules/update")
      .setParam("key", ruleKey)
      .setParam("organization", SONARCLOUD_ORGANIZATION)
      .setParam("markdown_note", extendedDescription);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertThat(response.code()).isEqualTo(200);
    }

    openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA);
    waitForAnalysisToBeReady(configScopeId);
    var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(configScopeId, "java" +
      ":S106", null)).get();
    assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(1).getContent().getLeft().getHtmlContent()).contains(extendedDescription);
  }

  @Test
  void analysisJavascript() {
    var configScopeId = "analysisJavascript";
    restoreProfile("javascript-sonarlint.xml");
    var projectKeyJs = "sample-javascript";
    provisionProject(projectKeyJs, "Sample Javascript");
    associateProjectToQualityProfile(projectKeyJs, "js", "SonarLint IT Javascript");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyJs);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyJs, "src/Person.js"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisPHP() {
    var configScopeId = "analysisPHP";
    restoreProfile("php-sonarlint.xml");
    var projectKeyPhp = "sample-php";
    provisionProject(projectKeyPhp, "Sample PHP");
    associateProjectToQualityProfile(projectKeyPhp, "php", "SonarLint IT PHP");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyPhp);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyPhp, "src/Math.php"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisPython() {
    var configScopeId = "analysisPython";
    restoreProfile("python-sonarlint.xml");
    var projectKeyPython = "sample-python";
    provisionProject(projectKeyPython, "Sample Python");
    associateProjectToQualityProfile(projectKeyPython, "py", "SonarLint IT Python");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyPython);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyPython, "src/hello.py"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisWeb() {
    var configScopeId = "analysisWeb";
    restoreProfile("web-sonarlint.xml");
    var projectKey = "sample-web";
    provisionProject(projectKey, "Sample Web");
    associateProjectToQualityProfile(projectKey, "web", "SonarLint IT Web");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKey);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKey, "src/file.html"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  @Disabled("Reaction to settings changes is not fully implemented in the new backend, see SLCORE-650")
  void analysisUseConfiguration() {
    var configScopeId = "analysisUseConfiguration";
    var issueListener = new SaveIssueListener();
    openUnboundConfigurationScope(configScopeId);
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(2);

    try {
      // Override default file suffixes in project props so that input file is not considered as a Java file
      setSettingsMultiValue(projectKey(PROJECT_KEY_JAVA), SONAR_JAVA_FILE_SUFFIXES, ".foo");

      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA), true)));
      waitForAnalysisToBeReady(configScopeId);

      issueListener.clear();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA,
          "src/main/java/foo/Foo.java",
          "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
        issueListener, null, null, configScopeId);
      assertThat(issueListener.getIssues()).isEmpty();
    } finally {
      adminWsClient.settings().reset(new ResetRequest()
        .setKeys(Collections.singletonList(SONAR_JAVA_FILE_SUFFIXES))
        .setComponent(projectKey(PROJECT_KEY_JAVA)));
    }
  }

  @Test
  void downloadUserOrganizations() throws ExecutionException, InterruptedException {
    var response = backend.getConnectionService()
      .listUserOrganizations(new ListUserOrganizationsParams(Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD)))).get();
    assertThat(response.getUserOrganizations()).hasSize(1);
  }

  @Test
  void getOrganization() throws ExecutionException, InterruptedException {
    var response = backend.getConnectionService()
      .getOrganization(new GetOrganizationParams(Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD)), SONARCLOUD_ORGANIZATION)).get();
    var org = response.getOrganization();
    assertThat(org).isNotNull();
    assertThat(org.getKey()).isEqualTo(SONARCLOUD_ORGANIZATION);
    assertThat(org.getName()).isEqualTo("SonarLint IT Tests");
  }

  @Test
  void analysisRuby() {
    var configScopeId = "analysisRuby";
    restoreProfile("ruby-sonarlint.xml");
    var projectKeyRuby = "sample-ruby";
    provisionProject(projectKeyRuby, "Sample Ruby");
    associateProjectToQualityProfile(projectKeyRuby, "ruby", "SonarLint IT Ruby");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyRuby);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyRuby, "src/hello.rb"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisKotlin() {
    var configScopeId = "analysisKotlin";
    restoreProfile("kotlin-sonarlint.xml");
    var projectKeyKotlin = "sample-kotlin";
    provisionProject(projectKeyKotlin, "Sample Kotlin");
    associateProjectToQualityProfile(projectKeyKotlin, "kotlin", "SonarLint IT Kotlin");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyKotlin);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyKotlin, "src/hello.kt"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisScala() {
    var configScopeId = "analysisScala";
    restoreProfile("scala-sonarlint.xml");
    var projectKeyScala = "sample-scala";
    provisionProject(projectKeyScala, "Sample Scala");
    associateProjectToQualityProfile(projectKeyScala, "scala", "SonarLint IT Scala");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyScala);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyScala, "src/Hello.scala"), issueListener, null, null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisXml() {
    var configScopeId = "analysisXml";
    restoreProfile("xml-sonarlint.xml");
    var projectKeyXml = "sample-xml";
    provisionProject(projectKeyXml, "Sample XML");
    associateProjectToQualityProfile(projectKeyXml, "xml", "SonarLint IT XML");

    var issueListener = new SaveIssueListener();
    openBoundConfigurationScope(configScopeId, projectKeyXml);
    waitForAnalysisToBeReady(configScopeId);
    engine.analyze(createAnalysisConfiguration(projectKeyXml, "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null, configScopeId);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void testConnection() throws ExecutionException, InterruptedException {
    var successResponse = backend.getConnectionService()
      .validateConnection(
        new ValidateConnectionParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION, Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD)))))
      .get();
    assertThat(successResponse.isSuccess()).isTrue();
    assertThat(successResponse.getMessage()).isEqualTo("Authentication successful");

    var failIfWrongOrg = backend.getConnectionService().validateConnection(
      new ValidateConnectionParams(new TransientSonarCloudConnectionDto("not-exists", Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD))))).get();
    assertThat(failIfWrongOrg.isSuccess()).isFalse();
    assertThat(failIfWrongOrg.getMessage()).isEqualTo("No organizations found for key: not-exists");

    var failIfWrongCredentials = backend.getConnectionService()
      .validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION, Either.forLeft(new TokenDto("foo"))))).get();
    assertThat(failIfWrongCredentials.isSuccess()).isFalse();
    assertThat(failIfWrongCredentials.getMessage()).isEqualTo("Authentication failed");
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Hotspots {
    private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";

    @BeforeAll
    void prepare() throws Exception {
      restoreProfile("java-sonarlint-with-hotspot.xml");
      provisionProject(PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
      associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");
      analyzeMavenProject(projectKey(PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT);
    }

    @Test
    void reportHotspots() {
      var configScopeId = "reportHotspots";
      var issueListener = new SaveIssueListener();
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT);
      waitForAnalysisToBeReady(configScopeId);
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_HOTSPOT,
          "src/main/java/foo/Foo.java",
          "sonar.java.binaries", new File("projects/sample-java-hotspot/target/classes").getAbsolutePath()),
        issueListener, null, null, configScopeId);

      assertThat(issueListener.getIssues()).hasSize(1)
        .extracting(RawIssue::getRuleKey, RawIssue::getType)
        .containsExactly(tuple("java:S4792", RuleType.SECURITY_HOTSPOT));
    }

    @Test
    void loadHotspotRuleDescription() throws Exception {
      openBoundConfigurationScope("loadHotspotRuleDescription", PROJECT_KEY_JAVA_HOTSPOT);

      var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("loadHotspotRuleDescription", "java:S4792", null)).get();
      assertThat(ruleDetails.details().getName()).isEqualTo("Configuring loggers is security-sensitive");
      assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(2).getContent().getLeft().getHtmlContent())
        .contains("Check that your production deployment doesn’t have its loggers in \"debug\" mode");
    }

    @Test
    void shouldMatchServerSecurityHotspots() throws ExecutionException, InterruptedException {
      var configScopeId = "shouldMatchServerSecurityHotspots";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT);
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

  private static void openBoundConfigurationScope(String configScopeId, String projectKey) {
    openedConfigurationScopeIds.add(configScopeId);
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configScopeId, null, true, "My " + configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey(projectKey), true)))));
  }

  private static void openUnboundConfigurationScope(String configScopeId) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configScopeId, null, true, "My " + configScopeId, new BindingConfigurationDto(null, null, true)))));
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class TaintVulnerabilities {
    private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";

    @BeforeAll
    void prepare() throws Exception {
      restoreProfile("java-sonarlint-with-taint.xml");
      provisionProject(PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
      associateProjectToQualityProfile(PROJECT_KEY_JAVA_TAINT, "java", "SonarLint Taint Java");
      analyzeMavenProject(projectKey(PROJECT_KEY_JAVA_TAINT), PROJECT_KEY_JAVA_TAINT);
    }

    @Test
    void download_taint_vulnerabilities_for_project() throws ExecutionException, InterruptedException {
      var configScopeId = "download_taint_vulnerabilities_for_project";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_TAINT);
      waitForAnalysisToBeReady(configScopeId);

      // Ensure a vulnerability has been reported on server side
      var issuesList = adminWsClient.issues().search(new SearchRequest().setTypes(List.of("VULNERABILITY")).setComponentKeys(List.of(projectKey(PROJECT_KEY_JAVA_TAINT))))
        .getIssuesList();
      assertThat(issuesList).hasSize(1);
      var issueKey = issuesList.get(0).getKey();

      var taintVulnerabilities = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(configScopeId, true)).get().getTaintVulnerabilities();

      assertThat(taintVulnerabilities).hasSize(1);
      var taintVulnerability = taintVulnerabilities.get(0);
      assertThat(taintVulnerability.getSonarServerKey()).isEqualTo(issueKey);
      assertThat(taintVulnerability.getRuleKey()).isEqualTo("javasecurity:S3649");
      assertThat(taintVulnerability.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(taintVulnerability.getSeverity()).isEqualTo(IssueSeverity.MAJOR);
      assertThat(taintVulnerability.getType()).isEqualTo(org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.VULNERABILITY);
      assertThat(taintVulnerability.getRuleDescriptionContextKey()).isNull();
      assertThat(taintVulnerability.getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
      assertThat(taintVulnerability.getImpacts()).containsExactly(entry(SoftwareQuality.SECURITY, ImpactSeverity.HIGH));
      assertThat(taintVulnerability.getFlows()).isNotEmpty();
      assertThat(taintVulnerability.isOnNewCode()).isTrue();
      var flow = taintVulnerability.getFlows().get(0);
      assertThat(flow.getLocations()).isNotEmpty();
      assertThat(flow.getLocations().get(0).getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(flow.getLocations().get(flow.getLocations().size() - 1).getTextRange().getHash()).isIn(hash("request.getParameter(\"user\")"),
        hash("request.getParameter(\"pass\")"));
    }
  }

  private static void waitForAnalysisToBeReady(String configScopeId) {
    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
  }

  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(Collections.singletonList(value))
      .setComponent(moduleKey));
  }

  public static WsClient newAdminWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(SONARCLOUD_STAGING_URL)
      .credentials(SONARCLOUD_USER, SONARCLOUD_PASSWORD)
      .build());
  }

  private static void analyzeMavenProject(String projectKey, String projectDirName) throws IOException {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    runMaven(projectDir, "clean", "package", "sonar:sonar",
      "-Dsonar.projectKey=" + projectKey,
      "-Dsonar.host.url=" + SONARCLOUD_STAGING_URL,
      "-Dsonar.organization=" + SONARCLOUD_ORGANIZATION,
      "-Dsonar.token=" + sonarcloudUserToken,
      "-Dsonar.scm.disabled=true",
      "-Dsonar.branch.autoconfig.disabled=true");

    waitAtMost(1, TimeUnit.MINUTES).until(() -> {
      var request = new GetRequest("api/analysis_reports/is_queue_empty");
      try (var response = adminWsClient.wsConnector().call(request)) {
        return "true".equals(response.content());
      }
    });
  }

  private static void runMaven(Path workDir, String... args) throws IOException {
    CommandLine cmdLine;
    if (SystemUtils.IS_OS_WINDOWS) {
      cmdLine = CommandLine.parse("cmd.exe");
      cmdLine.addArguments("/c");
      cmdLine.addArguments("mvn");
    } else {
      cmdLine = CommandLine.parse("mvn");
    }

    cmdLine.addArguments(new String[]{"--batch-mode", "--show-version", "--errors"});
    cmdLine.addArguments(args);
    var executor = new DefaultExecutor();
    executor.setWorkingDirectory(workDir.toFile());
    var exitValue = executor.execute(cmdLine);
    assertThat(exitValue).isZero();
  }

  private static void assertIsOk(WsResponse response) {
    var code = response.code();
    assertThat(code)
      .withFailMessage(() -> "Expected an HTTP call to have an OK code, got: " + code)
      // This is an approximation for "non error codes" - 200, 201, 204... + possible redirects
      .isBetween(200, 399);
  }

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD));
        }
        return super.getCredentials(connectionId);
      }

      @Override
      public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
        analysisReadinessByConfigScopeId.putAll(configurationScopeIds.stream().collect(Collectors.toMap(Function.identity(), k -> areReadyForAnalysis)));
      }

      @Override
      public void didChangeNodeJs(@org.jetbrains.annotations.Nullable Path nodeJsPath, @org.jetbrains.annotations.Nullable String version) {
        engine.restartAsync();
      }

      @Override
      public void didUpdatePlugins(String connectionId) {
        engine.restartAsync();
      }

      @Override
      public void log(LogParams params) {
        System.out.println(params.toString());
        rpcClientLogs.add(params);
      }
    };
  }
}
