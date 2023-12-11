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

import its.utils.ConsoleLogOutput;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.GetMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
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
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";
  private static final String SONAR_JAVA_FILE_SUFFIXES = "sonar.java.file.suffixes";
  private static final String SONARCLOUD_STAGING_URL = "https://sc-staging.io";
  private static final String SONARCLOUD_ORGANIZATION = "sonarlint-it";
  private static final String SONARCLOUD_USER = "sonarlint-it";
  private static final String SONARCLOUD_PASSWORD = System.getenv("SONARCLOUD_IT_PASSWORD");

  private static final String TIMESTAMP = Long.toString(Instant.now().toEpochMilli());
  private static final String TOKEN_NAME = "SLCORE-IT-" + TIMESTAMP;
  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String PROJECT_KEY_PHP = "sample-php";
  private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
  private static final String PROJECT_KEY_PYTHON = "sample-python";
  private static final String PROJECT_KEY_WEB = "sample-web";
  private static final String PROJECT_KEY_KOTLIN = "sample-kotlin";
  private static final String PROJECT_KEY_RUBY = "sample-ruby";
  private static final String PROJECT_KEY_SCALA = "sample-scala";
  private static final String PROJECT_KEY_XML = "sample-xml";

  public static final String CONNECTION_ID = "sonarcloud";

  private static WsClient adminWsClient;
  @TempDir
  private static Path sonarUserHome;

  private static ConnectedSonarLintEngine engine;

  private static int randomPositiveInt;

  private static SonarLintRpcServer backend;
  private static String sonarcloudUserToken;
  private static BackendJsonRpcLauncher serverLauncher;
  private static final List<String> didSynchronizeConfigurationScopes = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void prepare() throws Exception {
    System.setProperty("sonarlint.internal.sonarcloud.url", SONARCLOUD_STAGING_URL);

    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    var languages = Set.of(JAVA, PHP, JS, PYTHON, HTML, RUBY, KOTLIN, SCALA, XML);
    var featureFlags = new FeatureFlagsDto(false, true, true, false, false, true, false, true);
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
    restoreProfile("java-sonarlint-with-hotspot.xml");
    restoreProfile("javascript-sonarlint.xml");
    restoreProfile("php-sonarlint.xml");
    restoreProfile("python-sonarlint.xml");
    restoreProfile("web-sonarlint.xml");
    restoreProfile("kotlin-sonarlint.xml");
    restoreProfile("ruby-sonarlint.xml");
    restoreProfile("scala-sonarlint.xml");
    restoreProfile("xml-sonarlint.xml");
    restoreProfile("java-sonarlint-with-taint.xml");

    provisionProject(PROJECT_KEY_JAVA, "Sample Java");
    provisionProject(PROJECT_KEY_PHP, "Sample PHP");
    provisionProject(PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
    provisionProject(PROJECT_KEY_PYTHON, "Sample Python");
    provisionProject(PROJECT_KEY_WEB, "Sample Web");
    provisionProject(PROJECT_KEY_RUBY, "Sample Ruby");
    provisionProject(PROJECT_KEY_KOTLIN, "Sample Kotlin");
    provisionProject(PROJECT_KEY_SCALA, "Sample Scala");
    provisionProject(PROJECT_KEY_XML, "Sample XML");

    associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");
    associateProjectToQualityProfile(PROJECT_KEY_PHP, "php", "SonarLint IT PHP");
    associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
    associateProjectToQualityProfile(PROJECT_KEY_PYTHON, "py", "SonarLint IT Python");
    associateProjectToQualityProfile(PROJECT_KEY_WEB, "web", "SonarLint IT Web");
    associateProjectToQualityProfile(PROJECT_KEY_RUBY, "ruby", "SonarLint IT Ruby");
    associateProjectToQualityProfile(PROJECT_KEY_KOTLIN, "kotlin", "SonarLint IT Kotlin");
    associateProjectToQualityProfile(PROJECT_KEY_SCALA, "scala", "SonarLint IT Scala");
    associateProjectToQualityProfile(PROJECT_KEY_XML, "xml", "SonarLint IT XML");

    // Build project to have bytecode
    runMaven(Paths.get("projects/sample-java"), "clean", "compile");

    var ALL_PROJECTS = Set.of(
      projectKey(PROJECT_KEY_JAVA),
      projectKey(PROJECT_KEY_PHP),
      projectKey(PROJECT_KEY_JAVASCRIPT),
      projectKey(PROJECT_KEY_PYTHON),
      projectKey(PROJECT_KEY_WEB),
      projectKey(PROJECT_KEY_KOTLIN),
      projectKey(PROJECT_KEY_RUBY),
      projectKey(PROJECT_KEY_SCALA),
      projectKey(PROJECT_KEY_XML));

    ALL_PROJECTS.forEach(projectKey -> {
      rpcClientLogs.clear();
      didSynchronizeConfigurationScopes.clear();
      bindProject(projectKey, CONFIG_SCOPE_ID + projectKey);
    });

    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");

    var logOutput = new ConsoleLogOutput(false);

    var globalAnalysisConfig = backend.getAnalysisService().getGlobalStandaloneConfiguration().join();

    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarCloudBuilder()
      .setLogOutput(logOutput)
      .setConnectionId(CONNECTION_ID)
      .enableHotspots()
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
      .setNodeJs(requireNonNull(globalAnalysisConfig.getNodeJsPath()), Version.create(requireNonNull(globalAnalysisConfig.getNodeJsVersion())))
      .setExtraProperties(globalProps)
      .build());
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
    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(CONFIG_SCOPE_ID));
    didSynchronizeConfigurationScopes.clear();
    rpcClientLogs.clear();
    // This property is altered in analysisUseConfiguration test
    adminWsClient.settings().reset(new ResetRequest()
      .setKeys(Collections.singletonList(SONAR_JAVA_FILE_SUFFIXES))
      .setComponent(projectKey(PROJECT_KEY_JAVA)));

    // This profile is altered in a test
    restoreProfile("java-sonarlint.xml");
  }

  @Test
  void sync_all_project_branches() throws ExecutionException, InterruptedException {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA), true)))));

    var sonarProjectBranch = backend.getSonarProjectBranchService().getMatchedSonarProjectBranch(new GetMatchedSonarProjectBranchParams(CONFIG_SCOPE_ID)).get();
    await().untilAsserted(() -> assertThat(sonarProjectBranch.getMatchedSonarProjectBranch()).isEqualTo(MAIN_BRANCH_NAME));
  }

  @Test
  void downloadProjects() {
    provisionProject("foo-bar", "Foo");
    var getAllProjectsParams = new GetAllProjectsParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION,
      Either.forRight(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD))));

    waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() ->
      assertThat(backend.getConnectionService().getAllProjects(getAllProjectsParams).get().getSonarProjects())
        .extracting(SonarProjectDto::getKey)
        .contains(projectKey("foo-bar"), projectKey(PROJECT_KEY_JAVA), projectKey(PROJECT_KEY_PHP))
    );
  }

  @Test
  void testRuleDescription() throws Exception {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectKey(PROJECT_KEY_JAVA), new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA), true)))));

    var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "java:S106", null)).get();
    assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(0).getContent().getLeft().getHtmlContent()).contains("logs serve as a record of events within an application");
  }

  @Test
  void verifyExtendedDescription() throws Exception {
    var ruleKey = "java:S106";

    var extendedDescription = "my dummy extended description";

    WsRequest request = new PostRequest("/api/rules/update")
      .setParam("key", ruleKey)
      .setParam("organization", SONARCLOUD_ORGANIZATION)
      .setParam("markdown_note", extendedDescription);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertThat(response.code()).isEqualTo(200);
    }

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectKey(PROJECT_KEY_JAVA), new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA), true)))));

    var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "java" +
      ":S106", null)).get();
    assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(1).getContent().getLeft().getHtmlContent()).contains(extendedDescription);
  }

  @Test
  void analysisJavascript() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVASCRIPT), PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisPHP() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_PHP), PROJECT_KEY_PHP, "src/Math.php"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisPython() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_PYTHON), PROJECT_KEY_PYTHON, "src/hello.py"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisWeb() throws IOException {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_WEB), PROJECT_KEY_WEB, "src/file.html"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisUseConfiguration() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

    // Override default file suffixes in project props so that input file is not considered as a Java file
    setSettingsMultiValue(projectKey(PROJECT_KEY_JAVA), SONAR_JAVA_FILE_SUFFIXES, ".foo");

    bindProject(projectKey(PROJECT_KEY_JAVA), CONFIG_SCOPE_ID);

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).isEmpty();
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
  void getProject() {
    var api = new ServerApi(sonarcloudEndpointITOrg(), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID)).component();
    assertThat(api.getProject(projectKey("foo"))).isNotPresent();
    assertThat(api.getProject(projectKey(PROJECT_KEY_RUBY))).isPresent();
  }

  @Test
  void analysisRuby() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_RUBY), PROJECT_KEY_RUBY, "src/hello.rb"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisKotlin() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_KOTLIN), PROJECT_KEY_KOTLIN, "src/hello.kt"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisScala() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_SCALA), PROJECT_KEY_SCALA, "src/Hello.scala"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisXml() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_XML), PROJECT_KEY_XML, "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
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
      provisionProject(PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
      associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");
      analyzeMavenProject(projectKey(PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT);
      bindProject(projectKey(PROJECT_KEY_JAVA_HOTSPOT), CONFIG_SCOPE_ID);
    }

    @Test
    void reportHotspots() throws Exception {
      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT,
          "src/main/java/foo/Foo.java",
          "sonar.java.binaries", new File("projects/sample-java-hotspot/target/classes").getAbsolutePath()),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).hasSize(1)
        .extracting(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue::getRuleKey, org.sonarsource.sonarlint.core.client.api.common.analysis.Issue::getType)
        .containsExactly(tuple("java:S4792", RuleType.SECURITY_HOTSPOT));
    }

    @Test
    void loadHotspotRuleDescription() throws Exception {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectKey(PROJECT_KEY_JAVA_HOTSPOT), new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA_HOTSPOT), true)))));

      var ruleDetails = backend.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams(CONFIG_SCOPE_ID, "java:S4792", null)).get();
      assertThat(ruleDetails.details().getName()).isEqualTo("Configuring loggers is security-sensitive");
      assertThat(ruleDetails.details().getDescription().getRight().getTabs().get(2).getContent().getLeft().getHtmlContent())
        .contains("Check that your production deployment doesn’t have its loggers in \"debug\" mode");
    }

    @Test
    void shouldMatchServerSecurityHotspots() throws ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectKey(PROJECT_KEY_JAVA_HOTSPOT), new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA_HOTSPOT), false)))));

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
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class TaintVulnerabilities {
    private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";

    @BeforeAll
    void prepare() throws Exception {
      provisionProject(PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
      associateProjectToQualityProfile(PROJECT_KEY_JAVA_TAINT, "java", "SonarLint Taint Java");
      analyzeMavenProject(projectKey(PROJECT_KEY_JAVA_TAINT), PROJECT_KEY_JAVA_TAINT);
    }

    @Test
    void download_taint_vulnerabilities_for_project() throws IOException, ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "Project", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_JAVA_TAINT, true)))));
      analyzeMavenProject("sample-java-taint", PROJECT_KEY_JAVA_TAINT);

      // Ensure a vulnerability has been reported on server side
      var issuesList = adminWsClient.issues().search(new SearchRequest().setTypes(List.of("VULNERABILITY")).setComponentKeys(List.of(PROJECT_KEY_JAVA_TAINT))).getIssuesList();
      assertThat(issuesList).hasSize(1);
      var issueKey = issuesList.get(0).getKey();

      var taintVulnerabilities = backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(CONFIG_SCOPE_ID, true)).get().getTaintVulnerabilities();

      assertThat(taintVulnerabilities).hasSize(1);
      var taintVulnerability = taintVulnerabilities.get(0);
      assertThat(taintVulnerability.getSonarServerKey()).isEqualTo(issueKey);
      assertThat(taintVulnerability.getRuleKey()).isEqualTo("javasecurity:S3649");
      assertThat(taintVulnerability.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      // forced severity is not taken into account anymore
      assertThat(taintVulnerability.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
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

  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(Collections.singletonList(value))
      .setComponent(moduleKey));
  }

  private static EndpointParams sonarcloudEndpointITOrg() {
    return sonarcloudEndpoint(SONARCLOUD_ORGANIZATION);
  }

  public static WsClient newAdminWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(SONARCLOUD_STAGING_URL)
      .credentials(SONARCLOUD_USER, SONARCLOUD_PASSWORD)
      .build());
  }

  private static EndpointParams sonarcloudEndpoint(@Nullable String orgKey) {
    return endpointParams(SONARCLOUD_STAGING_URL, true, orgKey);
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

  private static void bindProject(String projectKey, String configurationScopeId) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configurationScopeId, null, true, projectKey, new BindingConfigurationDto(CONNECTION_ID, projectKey,
        false)))));
    await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(configurationScopeId));
    // TODO FIX ME and remove this check for a log after https://sonarsource.atlassian.net/browse/SLCORE-396 is fixed
    await().untilAsserted(() -> assertThat(rpcClientLogs.stream().anyMatch(s -> s.getMessage().equals("Stored project analyzer " +
      "configuration"))).isTrue());
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
      public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
        didSynchronizeConfigurationScopes.addAll(params.getConfigurationScopeIds());
      }

      @Override
      public void log(LogParams params) {
        rpcClientLogs.add(params);
      }
    };
  }
}
