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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
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
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.GetOrganizationParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
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
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto;
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;

@Tag("SonarCloud")
class SonarCloudTests extends AbstractConnectedTests {
  private static final String SONAR_JAVA_FILE_SUFFIXES = "sonar.java.file.suffixes";
  private static final String SONARCLOUD_STAGING_URL = "https://sc-staging.io";
  private static final String SONARCLOUD_ORGANIZATION = "sonarlint-it";
  private static final String SONARCLOUD_USER = "sonarlint-it";
  private static final String SONARCLOUD_PASSWORD = System.getenv("SONARCLOUD_IT_PASSWORD");

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

  private final ProgressMonitor progress = new ProgressMonitor(null);

  private static WsClient adminWsClient;
  @TempDir
  private static Path sonarUserHome;

  private static ConnectedSonarLintEngine engine;

  private static int randomPositiveInt;

  private static SonarLintBackend backend;

  @BeforeAll
  static void prepare() throws Exception {
    System.setProperty("sonarlint.internal.sonarcloud.url", SONARCLOUD_STAGING_URL);
    backend = new SonarLintBackendImpl(newDummySonarLintClient());
    backend.initialize(
      new InitializeParams(IT_CLIENT_INFO, new FeatureFlagsDto(false, true, false, false, false, false), sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
        Collections.emptySet(), Collections.emptyMap(), Set.of(Language.JAVA), Collections.emptySet(),
        Collections.emptyList(), List.of(new SonarCloudConnectionConfigurationDto(CONNECTION_ID, SONARCLOUD_ORGANIZATION, true)), sonarUserHome.toString(),
        Map.of()));

    randomPositiveInt = new Random().nextInt() & Integer.MAX_VALUE;

    adminWsClient = newAdminWsClient();

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

    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarCloudBuilder()
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
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setExtraProperties(globalProps)
      .build());

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

    ALL_PROJECTS.forEach(p -> engine.updateProject(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), p, null));
    engine.sync(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), ALL_PROJECTS, null);
  }

  @AfterAll
  static void cleanup() throws Exception {
    var request = new PostRequest("api/projects/bulk_delete");
    request.setParam("q", "-" + randomPositiveInt);
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertIsOk(response);
    }

    try {
      engine.stop(true);
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
    // This property is altered in analysisUseConfiguration test
    adminWsClient.settings().reset(new ResetRequest()
      .setKeys(Collections.singletonList(SONAR_JAVA_FILE_SUFFIXES))
      .setComponent(projectKey(PROJECT_KEY_JAVA)));
    engine.sync(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey(PROJECT_KEY_JAVA)), null);

    // This profile is altered in a test
    restoreProfile("java-sonarlint.xml");
  }

  @Test
  void sync_all_project_branches() throws IOException {
    assertThat(engine.getServerBranches(projectKey(PROJECT_KEY_JAVA)).getBranchNames()).containsOnly(MAIN_BRANCH_NAME);
    assertThat(engine.getServerBranches(projectKey(PROJECT_KEY_JAVA)).getMainBranchName()).contains(MAIN_BRANCH_NAME);
  }

  @Test
  void downloadProjects() {
    provisionProject("foo-bar", "Foo");
    waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
      assertThat(engine.downloadAllProjects(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), null)).containsKeys(projectKey("foo-bar"),
        projectKey(PROJECT_KEY_JAVA),
        projectKey(PROJECT_KEY_PHP));
    });
  }

  @Test
  void testRuleDescription() throws Exception {
    assertThat(
      engine.getActiveRuleDetails(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), "java:S106", projectKey(PROJECT_KEY_JAVA)).get().getHtmlDescription())
      .contains("When logging a message there are");
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

    assertThat(
      engine.getActiveRuleDetails(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), ruleKey, projectKey(PROJECT_KEY_JAVA)).get().getExtendedDescription())
      .isEqualTo(extendedDescription);
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

    engine.sync(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey(PROJECT_KEY_JAVA)), null);

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
    var api = new ServerApi(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID)).component();
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

      engine.updateProject(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), projectKey(PROJECT_KEY_JAVA_HOTSPOT), null);
      engine.sync(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey(PROJECT_KEY_JAVA_HOTSPOT)), null);
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
      var ruleDetails = engine.getActiveRuleDetails(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), "java:S4792", projectKey(PROJECT_KEY_JAVA_HOTSPOT)).get();

      assertThat(ruleDetails.getName()).isEqualTo("Configuring loggers is security-sensitive");
      // HTML description is null for security hotspots when accessed through the deprecated engine API
      // When accessed through the backend service, the rule descriptions are split into sections
      // see its.ConnectedModeBackendTest.returnConvertedDescriptionSectionsForHotspotRules
      assertThat(ruleDetails.getHtmlDescription()).isNull();
    }

    @Test
    void downloadsServerHotspotsForProject() {
      engine.downloadAllServerHotspots(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), projectKey(PROJECT_KEY_JAVA_HOTSPOT), "master", null);

      var serverHotspots = engine.getServerHotspots(new ProjectBinding(projectKey(PROJECT_KEY_JAVA_HOTSPOT), "", "ide"), "master", "ide/src/main/java/foo/Foo.java");
      assertThat(serverHotspots)
        .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
        .containsExactly(tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, HotspotReviewStatus.TO_REVIEW));
    }

    @Test
    void downloadsServerHotspotsForFile() {
      var projectBinding = new ProjectBinding(projectKey(PROJECT_KEY_JAVA_HOTSPOT), "", "ide");

      engine.downloadAllServerHotspotsForFile(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), projectBinding, "ide/src/main/java/foo/Foo.java", "master", null);

      var serverHotspots = engine.getServerHotspots(projectBinding, "master", "ide/src/main/java/foo/Foo.java");
      assertThat(serverHotspots)
        .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
        .containsExactly(tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, HotspotReviewStatus.TO_REVIEW));
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

      engine.updateProject(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), projectKey(PROJECT_KEY_JAVA_TAINT), null);
      engine.sync(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), Set.of(projectKey(PROJECT_KEY_JAVA_TAINT)), null);
    }

    @Test
    void download_taint_vulnerabilities_for_file() {
      ProjectBinding projectBinding = new ProjectBinding(projectKey(PROJECT_KEY_JAVA_TAINT), "", "");

      engine.downloadAllServerTaintIssuesForFile(sonarcloudEndpointITOrg(), backend.getHttpClient(CONNECTION_ID), projectBinding, "src/main/java/foo/DbHelper.java",
        MAIN_BRANCH_NAME,
        null);

      var sinkIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");

      assertThat(sinkIssues).hasSize(1);

      var taintIssue = sinkIssues.get(0);
      assertThat(taintIssue.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(taintIssue.getSeverity()).isEqualTo(IssueSeverity.MAJOR);
      assertThat(taintIssue.getType()).isEqualTo(RuleType.VULNERABILITY);
      assertThat(taintIssue.getFlows()).isNotEmpty();
      var flow = taintIssue.getFlows().get(0);
      assertThat(flow.locations()).isNotEmpty();
      assertThat(flow.locations().get(0).getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(flow.locations().get(flow.locations().size() - 1).getTextRange().getHash()).isIn(hash("request.getParameter(\"user\")"), hash("request.getParameter(\"pass\")"));
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
      "-Dsonar.login=" + SONARCLOUD_USER,
      "-Dsonar.password=" + SONARCLOUD_PASSWORD,
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
    var cmdLine = CommandLine.parse("mvn");
    cmdLine.addArguments(new String[] {"--batch-mode", "--show-version", "--errors"});
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
          return CompletableFuture.completedFuture(new GetCredentialsResponse(new UsernamePasswordDto(SONARCLOUD_USER, SONARCLOUD_PASSWORD)));
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
