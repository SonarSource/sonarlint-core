/*
 * SonarLint Core - ITs - Tests
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
package its;

import its.utils.PluginLocator;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ShouldUseEnterpriseCSharpAnalyzerParams;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
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
  private static final Map<SonarCloudRegion, SonarQubeCloudRegionDto> SONARCLOUD_STAGING_URIS = new EnumMap<>(SonarCloudRegion.class);
  static {
    SONARCLOUD_STAGING_URIS.put(SonarCloudRegion.EU, new SonarQubeCloudRegionDto(URI.create("https://sc-staging.io"), URI.create("https://api.sc-staging.io"),
      URI.create("wss://events-api.sc-staging.io/")));
    SONARCLOUD_STAGING_URIS.put(SonarCloudRegion.US, new SonarQubeCloudRegionDto(URI.create("https://us-sc-staging.io"), URI.create("https://api.us-sc-staging.io"),
      URI.create("wss://events-api.us-sc-staging.io/")));
  }
  private static final SonarCloudRegion region = StringUtils.isNotBlank(System.getenv("SONARCLOUD_REGION")) ?
    SonarCloudRegion.valueOf(System.getenv("SONARCLOUD_REGION")) : SonarCloudRegion.EU;
  private static final URI SONARCLOUD_STAGING_URL = SONARCLOUD_STAGING_URIS.get(region).getUri();
  private static final String SONARCLOUD_ORGANIZATION = "sonarlint-it";
  private static final String SONARCLOUD_TOKEN = System.getenv("SONARCLOUD_IT_TOKEN");

  private static final String PROJECT_KEY_JAVA = "sample-java";

  public static final String CONNECTION_ID = "sonarcloud";

  private static WsClient adminWsClient;
  @TempDir
  private static Path sonarUserHome;

  private static int randomPositiveInt;

  private static SonarLintRpcServer backend;
  private static SonarLintRpcClientDelegate client;
  private static final Set<String> openedConfigurationScopeIds = new HashSet<>();
  private static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();

  @BeforeAll
  static void prepare() throws Exception {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    client = newDummySonarLintClient();
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

    backend = clientLauncher.getServerProxy();
    var languages = Set.of(JAVA, PHP, JS, PYTHON, HTML, RUBY, KOTLIN, SCALA, XML);
    var featureFlags = new FeatureFlagsDto(false, true, true, false, true, true, false, true, false, true, false);
    backend.initialize(
      new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(),
        new SonarCloudAlternativeEnvironmentDto(SONARCLOUD_STAGING_URIS), featureFlags,
        sonarUserHome.resolve("storage"),
        sonarUserHome.resolve("work"), emptySet(), PluginLocator.getEmbeddedPluginsByKeyForTests(), languages, emptySet(), emptySet(), emptyList(),
        List.of(new SonarCloudConnectionConfigurationDto(CONNECTION_ID, SONARCLOUD_ORGANIZATION, SonarCloudRegion.valueOf(region.name()), true)), sonarUserHome.toString(),
        emptyMap(), false, null, false, null));
    randomPositiveInt = new Random().nextInt() & Integer.MAX_VALUE;

    adminWsClient = newAdminWsClient();

    restoreProfile("java-sonarlint.xml");
    provisionProject(PROJECT_KEY_JAVA, "Sample Java");
    associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");

    // Build project to have bytecode
    runMaven(Paths.get("projects/sample-java"), "clean", "compile");

    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
  }

  @AfterAll
  static void cleanup() throws Exception {
    var request = new PostRequest("api/projects/bulk_delete");
    request.setParam("q", "-" + randomPositiveInt);
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertIsOk(response);
    }
    ((MockSonarLintRpcClientDelegate) client).clear();
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
  void should_use_enterprise_csharp_analyzer_with_sonarcloud() {
    // the project and config scope names do not matter
    var configScopeId = "match_main_branch_by_default";
    openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA);
    waitForAnalysisToBeReady(configScopeId);

    var shouldUseEnterpriseAnalyzer = backend.getAnalysisService().shouldUseEnterpriseCSharpAnalyzer(new ShouldUseEnterpriseCSharpAnalyzerParams(configScopeId)).join();

    await().untilAsserted(() -> assertThat(shouldUseEnterpriseAnalyzer.shouldUseEnterpriseAnalyzer()).isTrue());
  }

  @Test
  void getAllProjects() {
    provisionProject("foo-bar", "Foo");
    var getAllProjectsParams = new GetAllProjectsParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION,
      Either.forLeft(new TokenDto(SONARCLOUD_TOKEN)), region));

    waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(backend.getConnectionService().getAllProjects(getAllProjectsParams).get().getSonarProjects())
      .extracting(SonarProjectDto::getKey)
      .contains(projectKey("foo-bar")));
  }

  @Test
  void testRuleDescription() throws Exception {
    openBoundConfigurationScope("testRuleDescription", PROJECT_KEY_JAVA);
    waitForAnalysisToBeReady("testRuleDescription");

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

    openBoundConfigurationScope(configScopeId, projectKeyJs);
    waitForAnalysisToBeReady(configScopeId);
    var issues = analyzeAndGetIssues(projectKeyJs, "src/Person.js", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void analysisPHP() {
    var configScopeId = "analysisPHP";
    restoreProfile("php-sonarlint.xml");
    var projectKeyPhp = "sample-php";
    provisionProject(projectKeyPhp, "Sample PHP");
    associateProjectToQualityProfile(projectKeyPhp, "php", "SonarLint IT PHP");

    openBoundConfigurationScope(configScopeId, projectKeyPhp);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKeyPhp, "src/Math.php", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void analysisPython() {
    var configScopeId = "analysisPython";
    restoreProfile("python-sonarlint.xml");
    var projectKeyPython = "sample-python";
    provisionProject(projectKeyPython, "Sample Python");
    associateProjectToQualityProfile(projectKeyPython, "py", "SonarLint IT Python");

    openBoundConfigurationScope(configScopeId, projectKeyPython);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKeyPython, "src/hello.py", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void analysisWeb() {
    var configScopeId = "analysisWeb";
    restoreProfile("web-sonarlint.xml");
    var projectKey = "sample-web";
    provisionProject(projectKey, "Sample Web");
    associateProjectToQualityProfile(projectKey, "web", "SonarLint IT Web");

    openBoundConfigurationScope(configScopeId, projectKey);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKey, "src/file.html", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  @Disabled("Reaction to settings changes is not fully implemented in the new backend, see SLCORE-650")
  void analysisUseConfiguration() {
    var configScopeId = "analysisUseConfiguration";
    openUnboundConfigurationScope(configScopeId);
    var issues = analyzeAndGetIssues(PROJECT_KEY_JAVA, "src/main/java/foo/Foo.java", configScopeId,
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath());
    assertThat(issues).hasSize(2);

    try {
      // Override default file suffixes in project props so that input file is not considered as a Java file
      setSettingsMultiValue(projectKey(PROJECT_KEY_JAVA), SONAR_JAVA_FILE_SUFFIXES, ".foo");

      backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey(PROJECT_KEY_JAVA), true)));
      waitForAnalysisToBeReady(configScopeId);

      issues = analyzeAndGetIssues(PROJECT_KEY_JAVA, "src/main/java/foo/Foo.java", configScopeId,
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath());
      assertThat(issues).isEmpty();
    } finally {
      adminWsClient.settings().reset(new ResetRequest()
        .setKeys(Collections.singletonList(SONAR_JAVA_FILE_SUFFIXES))
        .setComponent(projectKey(PROJECT_KEY_JAVA)));
    }
  }

  @Test
  void downloadUserOrganizations() throws ExecutionException, InterruptedException {
    var response = backend.getConnectionService()
      .listUserOrganizations(new ListUserOrganizationsParams(Either.forLeft(new TokenDto(SONARCLOUD_TOKEN)), region)).get();
    assertThat(response.getUserOrganizations()).hasSize(1);
  }

  @Test
  void getOrganization() throws ExecutionException, InterruptedException {
    var response = backend.getConnectionService()
      .getOrganization(new GetOrganizationParams(Either.forLeft(new TokenDto(SONARCLOUD_TOKEN)), SONARCLOUD_ORGANIZATION, region)).get();
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

    openBoundConfigurationScope(configScopeId, projectKeyRuby);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKeyRuby, "src/hello.rb", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void analysisKotlin() {
    var configScopeId = "analysisKotlin";
    restoreProfile("kotlin-sonarlint.xml");
    var projectKeyKotlin = "sample-kotlin";
    provisionProject(projectKeyKotlin, "Sample Kotlin");
    associateProjectToQualityProfile(projectKeyKotlin, "kotlin", "SonarLint IT Kotlin");

    openBoundConfigurationScope(configScopeId, projectKeyKotlin);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKeyKotlin, "src/hello.kt", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void analysisScala() {
    var configScopeId = "analysisScala";
    restoreProfile("scala-sonarlint.xml");
    var projectKeyScala = "sample-scala";
    provisionProject(projectKeyScala, "Sample Scala");
    associateProjectToQualityProfile(projectKeyScala, "scala", "SonarLint IT Scala");

    openBoundConfigurationScope(configScopeId, projectKeyScala);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKeyScala, "src/Hello.scala", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void analysisXml() {
    var configScopeId = "analysisXml";
    restoreProfile("xml-sonarlint.xml");
    var projectKeyXml = "sample-xml";
    provisionProject(projectKeyXml, "Sample XML");
    associateProjectToQualityProfile(projectKeyXml, "xml", "SonarLint IT XML");

    openBoundConfigurationScope(configScopeId, projectKeyXml);
    waitForAnalysisToBeReady(configScopeId);

    var issues = analyzeAndGetIssues(projectKeyXml, "src/foo.xml", configScopeId);
    assertThat(issues).hasSize(1);
  }

  @Test
  void testConnection() throws ExecutionException, InterruptedException {
    var successResponse = backend.getConnectionService()
      .validateConnection(
        new ValidateConnectionParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION, Either.forLeft(new TokenDto(SONARCLOUD_TOKEN)), region)))
      .get();
    assertThat(successResponse.isSuccess()).isTrue();
    assertThat(successResponse.getMessage()).isEqualTo("Authentication successful");

    var failIfWrongOrg = backend.getConnectionService().validateConnection(
      new ValidateConnectionParams(new TransientSonarCloudConnectionDto("not-exists", Either.forLeft(new TokenDto(SONARCLOUD_TOKEN)), region))).get();
    assertThat(failIfWrongOrg.isSuccess()).isFalse();
    assertThat(failIfWrongOrg.getMessage()).isEqualTo("No organizations found for key: not-exists");

    var failIfWrongCredentials = backend.getConnectionService()
      .validateConnection(new ValidateConnectionParams(new TransientSonarCloudConnectionDto(SONARCLOUD_ORGANIZATION, Either.forLeft(new TokenDto("foo")), region))).get();
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
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT);
      waitForAnalysisToBeReady(configScopeId);

      var issues = analyzeAndGetHotspots(PROJECT_KEY_JAVA_HOTSPOT, "src/main/java/foo/Foo.java", configScopeId);
      assertThat(issues)
        .extracting(RaisedHotspotDto::getRuleKey, h -> h.getSeverityMode().getLeft().getType())
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
    void shouldMatchServerSecurityHotspots() {
      var configScopeId = "shouldMatchServerSecurityHotspots";
      openBoundConfigurationScope(configScopeId, PROJECT_KEY_JAVA_HOTSPOT);
      waitForAnalysisToBeReady(configScopeId);

      var raisedHotspots = analyzeAndGetHotspots(PROJECT_KEY_JAVA_HOTSPOT, "src/main/java/foo/Foo.java", configScopeId);

      assertThat(raisedHotspots).hasSize(1);
      assertThat(raisedHotspots.get(0).getStatus()).isEqualTo(HotspotStatus.TO_REVIEW);
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
      assertThat(taintVulnerability.getRuleDescriptionContextKey()).isNull();
      assertThat(taintVulnerability.getSeverityMode().isRight()).isTrue();
      assertThat(taintVulnerability.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.COMPLETE);
      assertThat(taintVulnerability.getSeverityMode().getRight().getImpacts().get(0)).extracting("softwareQuality", "impactSeverity").containsExactly(SoftwareQuality.SECURITY,
        ImpactSeverity.BLOCKER);
      assertThat(taintVulnerability.getFlows()).isNotEmpty();
      assertThat(taintVulnerability.isOnNewCode()).isTrue();
      // the feature is not enabled for our org
      assertThat(taintVulnerability.isAiCodeFixable()).isFalse();
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
      .url(SONARCLOUD_STAGING_URL.toString())
      .token(SONARCLOUD_TOKEN)
      .build());
  }

  private static void analyzeMavenProject(String projectKey, String projectDirName) throws IOException {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    runMaven(projectDir, "clean", "package", "sonar:sonar",
      "-Dsonar.projectKey=" + projectKey,
      "-Dsonar.host.url=" + SONARCLOUD_STAGING_URL,
      "-Dsonar.organization=" + SONARCLOUD_ORGANIZATION,
      "-Dsonar.token=" + SONARCLOUD_TOKEN,
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

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forLeft(new TokenDto(SONARCLOUD_TOKEN));
        }
        return super.getCredentials(connectionId);
      }

      @Override
      public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
        analysisReadinessByConfigScopeId.putAll(configurationScopeIds.stream().collect(Collectors.toMap(Function.identity(), k -> areReadyForAnalysis)));
      }

      @Override
      public void log(LogParams params) {
        System.out.println(params);
        rpcClientLogs.add(params);
      }
    };
  }

  private static List<RaisedIssueDto> analyzeAndGetIssues(String projectKey, String fileName, String configScopeId, String... properties) {
    final var baseDir = Paths.get("projects/" + projectKey).toAbsolutePath();
    final var filePath = baseDir.resolve(fileName);
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(filePath.toUri(), Path.of(fileName), configScopeId, false, null, filePath, null, null, true)),
      List.of(),
      List.of()));

    var analyzeResponse = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), List.of(filePath.toUri()), toMap(properties), true, System.currentTimeMillis())).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedIssues = ((MockSonarLintRpcClientDelegate) client).getRaisedIssues(configScopeId);
    ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    return raisedIssues != null ? raisedIssues.values().stream().flatMap(List::stream).toList() : List.of();
  }

  private static List<RaisedHotspotDto> analyzeAndGetHotspots(String projectKey, String fileName, String configScopeId, String... properties) {
    final var baseDir = Paths.get("projects/" + projectKey).toAbsolutePath();
    final var filePath = baseDir.resolve(fileName);
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(),
      List.of(new ClientFileDto(filePath.toUri(), Path.of(fileName), configScopeId, false, null, filePath, null, null, true)), List.of()));

    var analyzeResponse = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), List.of(filePath.toUri()), toMap(properties), true, System.currentTimeMillis())).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedHotspots = ((MockSonarLintRpcClientDelegate) client).getRaisedHotspots(configScopeId);
    ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    return raisedHotspots != null ? raisedHotspots.values().stream().flatMap(List::stream).toList() : List.of();
  }

}
