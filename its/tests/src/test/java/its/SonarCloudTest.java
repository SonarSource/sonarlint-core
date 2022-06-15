/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectionValidator;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Category(SonarCloud.class)
public class SonarCloudTest extends AbstractConnectedTest {
  private static final String SONAR_JAVA_FILE_SUFFIXES = "sonar.java.file.suffixes";
  private static final String SONARCLOUD_STAGING_URL = "https://sc-staging.io";
  private static final String SONARCLOUD_ORGANIZATION = "sonarlint-it";
  private static final String SONARCLOUD_USER = "sonarlint-it";
  private static final String SONARCLOUD_PASSWORD = System.getenv("SONARCLOUD_IT_PASSWORD");

  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String PROJECT_KEY_JAVA_PACKAGE = "sample-java-package";
  private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";
  private static final String PROJECT_KEY_JAVA_EMPTY = "sample-java-empty";
  private static final String PROJECT_KEY_PHP = "sample-php";
  private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
  private static final String PROJECT_KEY_PYTHON = "sample-python";
  private static final String PROJECT_KEY_WEB = "sample-web";
  private static final String PROJECT_KEY_KOTLIN = "sample-kotlin";
  private static final String PROJECT_KEY_RUBY = "sample-ruby";
  private static final String PROJECT_KEY_SCALA = "sample-scala";
  private static final String PROJECT_KEY_XML = "sample-xml";

  protected static final OkHttpClient SC_CLIENT = CLIENT_NO_AUTH.newBuilder()
    .addNetworkInterceptor(new PreemptiveAuthenticatorInterceptor(Credentials.basic(SONARCLOUD_USER, SONARCLOUD_PASSWORD)))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private final ProgressMonitor progress = new ProgressMonitor(null);

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  private static ConnectedSonarLintEngine engine;

  private static int randomPositiveInt;

  @BeforeClass
  public static void prepare() throws Exception {
    randomPositiveInt = new Random().nextInt() & Integer.MAX_VALUE;

    adminWsClient = newAdminWsClient();
    sonarUserHome = temp.newFolder().toPath();

    restoreProfile("java-sonarlint.xml");
    restoreProfile("java-sonarlint-package.xml");
    restoreProfile("java-sonarlint-with-hotspot.xml");
    restoreProfile("java-empty-sonarlint.xml");
    restoreProfile("javascript-sonarlint.xml");
    restoreProfile("php-sonarlint.xml");
    restoreProfile("python-sonarlint.xml");
    restoreProfile("web-sonarlint.xml");
    restoreProfile("kotlin-sonarlint.xml");
    restoreProfile("ruby-sonarlint.xml");
    restoreProfile("scala-sonarlint.xml");
    restoreProfile("xml-sonarlint.xml");

    provisionProject(PROJECT_KEY_JAVA, "Sample Java");
    provisionProject(PROJECT_KEY_JAVA_PACKAGE, "Sample Java Package");
    provisionProject(PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
    provisionProject(PROJECT_KEY_JAVA_EMPTY, "Sample Java Empty");
    provisionProject(PROJECT_KEY_PHP, "Sample PHP");
    provisionProject(PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
    provisionProject(PROJECT_KEY_PYTHON, "Sample Python");
    provisionProject(PROJECT_KEY_WEB, "Sample Web");
    provisionProject(PROJECT_KEY_RUBY, "Sample Ruby");
    provisionProject(PROJECT_KEY_KOTLIN, "Sample Kotlin");
    provisionProject(PROJECT_KEY_SCALA, "Sample Scala");
    provisionProject(PROJECT_KEY_XML, "Sample XML");

    associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");
    associateProjectToQualityProfile(PROJECT_KEY_JAVA_PACKAGE, "java", "SonarLint IT Java Package");
    associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");
    associateProjectToQualityProfile(PROJECT_KEY_JAVA_EMPTY, "java", "SonarLint IT Java Empty");
    associateProjectToQualityProfile(PROJECT_KEY_PHP, "php", "SonarLint IT PHP");
    associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
    associateProjectToQualityProfile(PROJECT_KEY_PYTHON, "py", "SonarLint IT Python");
    associateProjectToQualityProfile(PROJECT_KEY_WEB, "web", "SonarLint IT Web");
    associateProjectToQualityProfile(PROJECT_KEY_RUBY, "ruby", "SonarLint IT Ruby");
    associateProjectToQualityProfile(PROJECT_KEY_KOTLIN, "kotlin", "SonarLint IT Kotlin");
    associateProjectToQualityProfile(PROJECT_KEY_SCALA, "scala", "SonarLint IT Scala");
    associateProjectToQualityProfile(PROJECT_KEY_XML, "xml", "SonarLint IT XML");

    // Build project to have bytecode
    var line = "mvn clean compile";
    var cmdLine = CommandLine.parse(line);
    var executor = new DefaultExecutor();
    executor.setWorkingDirectory(new File("projects/sample-java"));
    var exitValue = executor.execute(cmdLine);
    assertThat(exitValue).isZero();

    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarCloudBuilder()
      .setConnectionId("sonarcloud")
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
    assertThat(engine.getGlobalStorageStatus()).isNull();

    updateGlobal();

    assertThat(engine.getGlobalStorageStatus()).isNotNull();
    assertThat(engine.getGlobalStorageStatus().isStale()).isFalse();

    Set<String> ALL_PROJECTS = Set.of(
      projectKey(PROJECT_KEY_JAVA),
      projectKey(PROJECT_KEY_JAVA_PACKAGE),
      projectKey(PROJECT_KEY_JAVA_HOTSPOT),
      projectKey(PROJECT_KEY_JAVA_EMPTY),
      projectKey(PROJECT_KEY_PHP),
      projectKey(PROJECT_KEY_JAVASCRIPT),
      projectKey(PROJECT_KEY_PYTHON),
      projectKey(PROJECT_KEY_WEB),
      projectKey(PROJECT_KEY_KOTLIN),
      projectKey(PROJECT_KEY_RUBY),
      projectKey(PROJECT_KEY_SCALA),
      projectKey(PROJECT_KEY_XML));

    ALL_PROJECTS.forEach(p -> engine.updateProject(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), p, null));
    engine.sync(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), ALL_PROJECTS, null);
  }

  @AfterClass
  public static void cleanup() {
    var request = new PostRequest("api/projects/bulk_delete");
    request.setParam("q", "-" + randomPositiveInt);
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
    }

    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  private static void associateProjectToQualityProfile(String projectKey, String language, String profileName) {
    var request = new PostRequest("api/qualityprofiles/add_project");
    request.setParam("language", language);
    request.setParam("project", projectKey(projectKey));
    request.setParam("qualityProfile", profileName);
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
    }
  }

  private static void restoreProfile(String profile) {
    var backupFile = new File("src/test/resources/" + profile);
    // XXX can't use RestoreRequest because of a bug
    var request = new PostRequest("api/qualityprofiles/restore");
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    request.setPart("backup", new PostRequest.Part(MediaTypes.XML, backupFile));
    try (var response = adminWsClient.wsConnector().call(request)) {
    }
  }

  private static void provisionProject(String key, String name) {
    var request = new PostRequest("api/projects/create");
    request.setParam("name", name);
    request.setParam("project", projectKey(key));
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    try (var response = adminWsClient.wsConnector().call(request)) {
    }
  }

  private static String projectKey(String key) {
    return "sonarlint-its-" + key + "-" + randomPositiveInt;
  }

  @After
  public void cleanup_after_each() {
    // This property is altered in analysisUseConfiguration test
    adminWsClient.settings().reset(new ResetRequest()
      .setKeys(Collections.singletonList(SONAR_JAVA_FILE_SUFFIXES))
      .setComponent(projectKey(PROJECT_KEY_JAVA)));
    engine.sync(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), Set.of(projectKey(PROJECT_KEY_JAVA)), null);

    // This profile is altered in a test
    restoreProfile("java-sonarlint.xml");
  }

  @Test
  public void sync_all_project_branches() throws IOException {
    assertThat(engine.getServerBranches(projectKey(PROJECT_KEY_JAVA)).getBranchNames()).containsOnly("master");
    assertThat(engine.getServerBranches(projectKey(PROJECT_KEY_JAVA)).getMainBranchName()).contains("master");
  }

  @Test
  public void downloadProjects() {
    assertThat(engine.allProjectsByKey()).isNotEmpty();
    provisionProject("foo-bar", "Foo");
    assertThat(engine.downloadAllProjects(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), null)).containsKeys(projectKey("foo-bar"),
      projectKey(PROJECT_KEY_JAVA),
      projectKey(PROJECT_KEY_PHP));
    assertThat(engine.allProjectsByKey()).containsKeys(projectKey("foo-bar"), projectKey(PROJECT_KEY_JAVA), projectKey(PROJECT_KEY_PHP));
  }

  @Test
  public void parsingErrorJava() throws IOException {
    var fileContent = "pac kage its; public class MyTest { }";
    var testFile = temp.newFile("MyTestParseError.java").toPath();
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    var issueListener = new SaveIssueListener();
    var results = engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  public void parsingErrorJavascript() throws IOException {
    var fileContent = "asd asd";
    var testFile = temp.newFile("MyTest.js").toPath();
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    var issueListener = new SaveIssueListener();
    var results = engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVASCRIPT), testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  public void testRuleDescription() throws Exception {
    assertThat(
      engine.getActiveRuleDetails(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), "java:S106", projectKey(PROJECT_KEY_JAVA)).get().getHtmlDescription())
        .contains("When logging a message there are");
  }

  @Test
  public void verifyExtendedDescription() throws Exception {
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
      engine.getActiveRuleDetails(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), ruleKey, projectKey(PROJECT_KEY_JAVA)).get().getExtendedDescription())
        .isEqualTo(extendedDescription);
  }

  @Test
  public void analysisJavascript() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVASCRIPT), PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisPHP() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_PHP), PROJECT_KEY_PHP, "src/Math.php"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisPython() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_PYTHON), PROJECT_KEY_PYTHON, "src/hello.py"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisWeb() throws IOException {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_WEB), PROJECT_KEY_WEB, "src/file.html"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisUseQualityProfile() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).hasSize(2);
  }

  @Test
  public void dontReportHotspots() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void analysisIssueOnDirectory() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_PACKAGE), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "inputFile.path").containsOnly(
      tuple("java:S106", Paths.get("projects/sample-java/src/main/java/foo/Foo.java").toAbsolutePath().toString()),
      tuple("java:S1228", null));
  }

  @Test
  public void analysisUseEmptyQualityProfile() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_EMPTY), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void analysisUseConfiguration() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

    // Override default file suffixes in project props so that input file is not considered as a Java file
    setSettingsMultiValue(projectKey(PROJECT_KEY_JAVA), SONAR_JAVA_FILE_SUFFIXES, ".foo");

    engine.sync(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), Set.of(projectKey(PROJECT_KEY_JAVA)), null);

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void downloadUserOrganizations() {
    var helper = new ServerApi(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT)).organization();
    assertThat(helper.listUserOrganizations(progress)).hasSize(1);
  }

  @Test
  public void getOrganization() {
    var helper = new ServerApi(sonarcloudEndpoint(null), new SonarLintHttpClientOkHttpImpl(SC_CLIENT)).organization();
    var org = helper.getOrganization(SONARCLOUD_ORGANIZATION, progress);
    assertThat(org).isPresent();
    assertThat(org.get().getKey()).isEqualTo(SONARCLOUD_ORGANIZATION);
    assertThat(org.get().getName()).isEqualTo("SonarLint IT Tests");
  }

  @Test
  public void getProject() {
    var api = new ServerApi(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT)).component();
    assertThat(api.getProject(projectKey("foo"))).isNotPresent();
    assertThat(api.getProject(projectKey(PROJECT_KEY_RUBY))).isPresent();
  }

  @Test
  public void analysisRuby() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_RUBY), PROJECT_KEY_RUBY, "src/hello.rb"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisKotlin() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_KOTLIN), PROJECT_KEY_KOTLIN, "src/hello.kt"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisScala() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_SCALA), PROJECT_KEY_SCALA, "src/Hello.scala"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisXml() throws Exception {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_XML), PROJECT_KEY_XML, "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void testConnection() throws ExecutionException, InterruptedException {
    assertThat(
      new ConnectionValidator(new ServerApiHelper(sonarcloudEndpoint(SONARCLOUD_ORGANIZATION), new SonarLintHttpClientOkHttpImpl(SC_CLIENT))).validateConnection().get().success())
        .isTrue();
    assertThat(
      new ConnectionValidator(new ServerApiHelper(sonarcloudEndpoint(null), new SonarLintHttpClientOkHttpImpl(SC_CLIENT))).validateConnection().get().success())
        .isTrue();
    assertThat(
      new ConnectionValidator(new ServerApiHelper(sonarcloudEndpoint("not-exists"), new SonarLintHttpClientOkHttpImpl(SC_CLIENT))).validateConnection().get().success())
        .isFalse();
  }

  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(Collections.singletonList(value))
      .setComponent(moduleKey));
  }

  private static void updateGlobal() {
    engine.update(sonarcloudEndpointITOrg(), new SonarLintHttpClientOkHttpImpl(SC_CLIENT), null);
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
}
