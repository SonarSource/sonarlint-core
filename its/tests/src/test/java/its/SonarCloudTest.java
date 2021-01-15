/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.WsResponse;
import org.sonarqube.ws.client.qualityprofiles.ActivateRuleRequest;
import org.sonarqube.ws.client.qualityprofiles.AddProjectRequest;
import org.sonarqube.ws.client.qualityprofiles.DeactivateRuleRequest;
import org.sonarqube.ws.client.qualityprofiles.SearchRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Category(SonarCloud.class)
public class SonarCloudTest extends AbstractConnectedTest {
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

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private List<String> logs;

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
    String line = "mvn clean compile";
    CommandLine cmdLine = CommandLine.parse(line);
    DefaultExecutor executor = new DefaultExecutor();
    executor.setWorkingDirectory(new File("projects/sample-java"));
    int exitValue = executor.execute(cmdLine);
    assertThat(exitValue).isZero();
  }

  @AfterClass
  public static void cleanup() {
    adminWsClient.projects().bulkDelete(new org.sonarqube.ws.client.projects.BulkDeleteRequest()
      .setQ("-" + randomPositiveInt)
      .setOrganization(SONARCLOUD_ORGANIZATION));
  }

  private static void associateProjectToQualityProfile(String projectKey, String language, String profileName) {
    adminWsClient.qualityprofiles().addProject(new AddProjectRequest()
      .setProject(projectKey(projectKey))
      .setLanguage(language)
      .setQualityProfile(profileName)
      .setOrganization(SONARCLOUD_ORGANIZATION));
  }

  private static void restoreProfile(String profile) {
    File backupFile = new File("src/test/resources/" + profile);
    // XXX can't use RestoreRequest because of a bug
    PostRequest request = new PostRequest("api/qualityprofiles/restore");
    request.setParam("organization", SONARCLOUD_ORGANIZATION);
    request.setPart("backup", new PostRequest.Part(MediaTypes.XML, backupFile));
    adminWsClient.wsConnector().call(request);
  }

  private static void provisionProject(String key, String name) {
    adminWsClient.projects().create(new org.sonarqube.ws.client.projects.CreateRequest()
      .setProject(projectKey(key))
      .setName(name)
      .setOrganization(SONARCLOUD_ORGANIZATION));
  }

  private static String projectKey(String key) {
    return "sonarlint-its-" + key + "-" + randomPositiveInt;
  }

  @Before
  public void start() throws IOException {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    logs = new ArrayList<>();

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("sonarcloud")
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
      .setLogOutput((msg, level) -> {
        logs.add(msg);
      })
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setExtraProperties(globalProps)
      .build());
    assertThat(engine.getGlobalStorageStatus()).isNull();
    assertThat(engine.getState()).isEqualTo(State.NEVER_UPDATED);

    // This profile is altered in a test
    restoreProfile("java-sonarlint.xml");
  }

  @After
  public void stop() {
    adminWsClient.settings().reset(new ResetRequest()
      .setKeys(Collections.singletonList("sonar.java.file.suffixes"))
      .setComponent(projectKey(PROJECT_KEY_JAVA)));
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void downloadProjects() {
    updateGlobal();
    assertThat(engine.allProjectsByKey()).isNotEmpty();
    provisionProject("foo-bar", "Foo");
    assertThat(engine.downloadAllProjects(getServerConfig(), null)).containsKeys(projectKey("foo-bar"), projectKey(PROJECT_KEY_JAVA), projectKey(PROJECT_KEY_PHP));
    assertThat(engine.allProjectsByKey()).containsKeys(projectKey("foo-bar"), projectKey(PROJECT_KEY_JAVA), projectKey(PROJECT_KEY_PHP));
  }

  @Test
  public void parsingErrorJava() throws IOException {
    String fileContent = "pac kage its; public class MyTest { }";
    Path testFile = temp.newFile("MyTestParseError.java").toPath();
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA));

    SaveIssueListener issueListener = new SaveIssueListener();
    AnalysisResults results = engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  public void parsingErrorJavascript() throws IOException {
    String fileContent = "asd asd";
    Path testFile = temp.newFile("MyTest.js").toPath();
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVASCRIPT));

    SaveIssueListener issueListener = new SaveIssueListener();
    AnalysisResults results = engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVASCRIPT), testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  public void globalUpdate() {
    updateGlobal();

    assertThat(engine.getState()).isEqualTo(State.UPDATED);
    assertThat(engine.getGlobalStorageStatus()).isNotNull();
    assertThat(engine.getGlobalStorageStatus().isStale()).isFalse();
    assertThat(engine.getRuleDetails("java:S106").getHtmlDescription()).contains("When logging a message there are");

    assertThat(engine.getProjectStorageStatus(projectKey(PROJECT_KEY_JAVA))).isNull();
  }

  @Test
  public void updateProject() {
    updateGlobal();

    updateProject(projectKey(PROJECT_KEY_JAVA));

    assertThat(engine.getProjectStorageStatus(projectKey(PROJECT_KEY_JAVA))).isNotNull();
  }

  @Test
  public void verifyExtendedDescription() {
    String ruleKey = "java:S106";

    String extendedDescription = "my dummy extended description";

    WsRequest request = new PostRequest("/api/rules/update")
      .setParam("key", ruleKey)
      .setParam("organization", SONARCLOUD_ORGANIZATION)
      .setParam("markdown_note", extendedDescription);
    try (WsResponse response = adminWsClient.wsConnector().call(request)) {
      assertThat(response.code()).isEqualTo(200);
    }

    updateGlobal();

    assertThat(engine.getRuleDetails(ruleKey).getExtendedDescription()).isEqualTo(extendedDescription);
  }

  @Test
  public void analysisJavascript() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVASCRIPT));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVASCRIPT), PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisPHP() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_PHP));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_PHP), PROJECT_KEY_PHP, "src/Math.php"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisPython() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_PYTHON));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_PYTHON), PROJECT_KEY_PYTHON, "src/hello.py"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisWeb() throws IOException {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_WEB));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_WEB), PROJECT_KEY_WEB, "src/file.html"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisUseQualityProfile() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).hasSize(2);
  }

  @Test
  public void dontReportHotspots() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA_HOTSPOT));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void analysisIssueOnDirectory() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA_PACKAGE));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_PACKAGE), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "inputFile.path").containsOnly(
      tuple("java:S106", Paths.get("projects/sample-java/src/main/java/foo/Foo.java").toAbsolutePath().toString()),
      tuple("java:S1228", null));
  }

  @Test
  public void analysisJavaPomXml() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA, "pom.xml"), issueListener, null, null);

    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisUseEmptyQualityProfile() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA_EMPTY));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA_EMPTY), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void analysisUseConfiguration() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

    // Override default file suffixes in project props so that input file is not considered as a Java file
    setSettingsMultiValue(projectKey(PROJECT_KEY_JAVA), "sonar.java.file.suffixes", ".foo");
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA));

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_JAVA), PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void generateToken() {
    WsHelper ws = new WsHelperImpl();
    ServerConfiguration serverConfig = getServerConfig();

    String token = ws.generateAuthenticationToken(serverConfig, "test-its", true);
    assertThat(token).isNotNull();
  }

  @Test
  public void checkForUpdate() {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_JAVA));

    ServerConfiguration serverConfig = getServerConfig();

    StorageUpdateCheckResult result = engine.checkIfGlobalStorageNeedUpdate(serverConfig, null);
    assertThat(result.needUpdate()).isFalse();

    // Toggle a rule to ensure quality profile date is changed
    SearchWsResponse response = adminWsClient.qualityprofiles().search(new SearchRequest().setOrganization(SONARCLOUD_ORGANIZATION).setLanguage("java"));
    String profileKey = response.getProfilesList().stream().filter(p -> p.getName().equals("SonarLint IT Java")).findFirst().get().getKey();
    adminWsClient.qualityprofiles().deactivateRule(new DeactivateRuleRequest()
      .setKey(profileKey)
      .setRule("java:S1228"));
    adminWsClient.qualityprofiles().activateRule(new ActivateRuleRequest()
      .setKey(profileKey)
      .setRule("java:S1228"));

    result = engine.checkIfGlobalStorageNeedUpdate(serverConfig, null);
    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).contains("Quality profile 'SonarLint IT Java' for language 'Java' updated");

    result = engine.checkIfProjectStorageNeedUpdate(serverConfig, projectKey(PROJECT_KEY_JAVA), null);
    assertThat(result.needUpdate()).isFalse();

    // Change a project setting that is not in the whitelist
    setSettings(projectKey(PROJECT_KEY_JAVA), "sonar.foo", "biz");
    result = engine.checkIfProjectStorageNeedUpdate(serverConfig, projectKey(PROJECT_KEY_JAVA), null);
    assertThat(result.needUpdate()).isFalse();

    // Change a project setting that *is* in the whitelist
    setSettingsMultiValue(projectKey(PROJECT_KEY_JAVA), "sonar.exclusions", "**/*.foo");

    result = engine.checkIfProjectStorageNeedUpdate(serverConfig, projectKey(PROJECT_KEY_JAVA), null);
    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
  }

  @Test
  public void downloadUserOrganizations() {
    WsHelper helper = new WsHelperImpl();
    assertThat(helper.listUserOrganizations(getServerConfig(), null)).hasSize(1);
  }

  @Test
  public void getOrganization() {
    WsHelper helper = new WsHelperImpl();
    Optional<RemoteOrganization> org = helper.getOrganization(getServerConfigForOrg(null), SONARCLOUD_ORGANIZATION, null);
    assertThat(org).isPresent();
    assertThat(org.get().getKey()).isEqualTo(SONARCLOUD_ORGANIZATION);
    assertThat(org.get().getName()).isEqualTo("SonarLint IT Tests");
  }

  @Test
  public void getProject() {
    WsHelper helper = new WsHelperImpl();
    assertThat(helper.getProject(getServerConfig(), projectKey("foo"), null)).isNotPresent();
    assertThat(helper.getProject(getServerConfig(), projectKey(PROJECT_KEY_RUBY), null)).isPresent();
  }

  @Test
  public void analysisRuby() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_RUBY));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_RUBY), PROJECT_KEY_RUBY, "src/hello.rb"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisKotlin() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_KOTLIN));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_KOTLIN), PROJECT_KEY_KOTLIN, "src/hello.kt"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisScala() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_SCALA));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_SCALA), PROJECT_KEY_SCALA, "src/Hello.scala"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisXml() throws Exception {
    updateGlobal();
    updateProject(projectKey(PROJECT_KEY_XML));

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectKey(PROJECT_KEY_XML), PROJECT_KEY_XML, "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void testConnection() {
    assertThat(new WsHelperImpl().validateConnection(getServerConfigForOrg(SONARCLOUD_ORGANIZATION)).success()).isTrue();
    assertThat(new WsHelperImpl().validateConnection(getServerConfigForOrg(null)).success()).isTrue();
    assertThat(new WsHelperImpl().validateConnection(getServerConfigForOrg("not-exists")).success()).isFalse();
  }

  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(Collections.singletonList(value))
      .setComponent(moduleKey));
  }

  private void setSettings(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValue(value)
      .setComponent(moduleKey));
  }

  private void updateProject(String projectKey) {
    engine.updateProject(getServerConfig(), projectKey, null);
  }

  private void updateGlobal() {
    engine.update(getServerConfig(), null);
  }

  private ServerConfiguration getServerConfig() {
    return getServerConfigForOrg(SONARCLOUD_ORGANIZATION);
  }

  public static WsClient newAdminWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(SONARCLOUD_STAGING_URL)
      .credentials(SONARCLOUD_USER, SONARCLOUD_PASSWORD)
      .build());
  }

  private ServerConfiguration getServerConfigForOrg(@Nullable String orgKey) {
    return ServerConfiguration.builder()
      .url(SONARCLOUD_STAGING_URL)
      .organizationKey(orgKey)
      .userAgent("SonarLint ITs")
      .credentials(SONARCLOUD_USER,
        SONARCLOUD_PASSWORD)
      .build();
  }
}
