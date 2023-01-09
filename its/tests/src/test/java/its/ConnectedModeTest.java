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

import com.google.protobuf.InvalidProtocolBufferException;
import com.sonar.orchestrator.OrchestratorExtension;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.OrchestratorUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Hotspots;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

class ConnectedModeTest extends AbstractConnectedTest {

  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String PROJECT_KEY_JAVA_CUSTOM_SENSOR = "sample-java-custom-sensor";
  private static final String PROJECT_KEY_GLOBAL_EXTENSION = "sample-global-extension";
  private static final String PROJECT_KEY_JAVA_PACKAGE = "sample-java-package";
  private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";
  private static final String PROJECT_KEY_JAVA_EMPTY = "sample-java-empty";
  private static final String PROJECT_KEY_JAVA_MARKDOWN = "sample-java-markdown";
  private static final String PROJECT_KEY_JAVA_CUSTOM = "sample-java-custom";
  private static final String PROJECT_KEY_PHP = "sample-php";
  private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
  private static final String PROJECT_KEY_PYTHON = "sample-python";
  private static final String PROJECT_KEY_WEB = "sample-web";
  private static final String PROJECT_KEY_KOTLIN = "sample-kotlin";
  private static final String PROJECT_KEY_RUBY = "sample-ruby";
  private static final String PROJECT_KEY_SCALA = "sample-scala";
  private static final String PROJECT_KEY_XML = "sample-xml";

  private static String javaRuleKey(String key) {
    // Starting from SonarJava 6.0 (embedded in SQ 8.2), rule repository has been changed
    return javaRuleKey(ORCHESTRATOR, key);
  }

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .addPlugin(FileLocation.of("../plugins/global-extension-plugin/target/global-extension-plugin.jar"))
    .addPlugin(FileLocation.of("../plugins/custom-sensor-plugin/target/custom-sensor-plugin.jar"))
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/global-extension.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-package.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-markdown.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-empty-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/javascript-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-custom.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/php-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/python-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/custom-sensor.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/web-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/kotlin-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/ruby-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/scala-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/xml-sonarlint.xml"))
    // Ensure SSE are processed correctly just after SQ startup
    .setServerProperty("sonar.pushevents.polling.initial.delay", "2")
    .setServerProperty("sonar.pushevents.polling.period", "1")
    .setServerProperty("sonar.pushevents.polling.last.timestamp", "1")
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private static WsClient adminWsClient;
  @TempDir
  private static Path sonarUserHome;
  private ConnectedGlobalConfiguration globalConfig;

  private ConnectedSonarLintEngine engine;
  private List<String> logs;

  @BeforeAll
  static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA, "Sample Java");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_PACKAGE, "Sample Java Package");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_EMPTY, "Sample Java Empty");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_MARKDOWN, "Sample Java Markdown");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_PHP, "Sample PHP");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_CUSTOM, "Sample Java Custom");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_PYTHON, "Sample Python");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_WEB, "Sample Web");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_CUSTOM_SENSOR, "Sample Java Custom");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_GLOBAL_EXTENSION, "Sample Global Extension");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_RUBY, "Sample Ruby");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_KOTLIN, "Sample Kotlin");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_SCALA, "Sample Scala");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_XML, "Sample XML");

    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_PACKAGE, "java", "SonarLint IT Java Package");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_EMPTY, "java", "SonarLint IT Java Empty");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_MARKDOWN, "java", "SonarLint IT Java Markdown");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_PHP, "php", "SonarLint IT PHP");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_CUSTOM, "java", "SonarLint IT Java Custom");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_PYTHON, "py", "SonarLint IT Python");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_WEB, "web", "SonarLint IT Web");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_CUSTOM_SENSOR, "java", "SonarLint IT Custom Sensor");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_RUBY, "ruby", "SonarLint IT Ruby");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_KOTLIN, "kotlin", "SonarLint IT Kotlin");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_SCALA, "scala", "SonarLint IT Scala");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_XML, "xml", "SonarLint IT XML");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_GLOBAL_EXTENSION, "cobol", "SonarLint IT Global Extension");

    // Build project to have bytecode and analyze
    analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA, Map.of("sonar.projectKey", PROJECT_KEY_JAVA));
  }

  @BeforeEach
  void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    logs = new ArrayList<>();

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
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
    adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")).setComponent(PROJECT_KEY_JAVA));
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  void downloadProjects() {
    provisionProject(ORCHESTRATOR, "foo-bar", "Foo");
    assertThat(engine.downloadAllProjects(endpointParams(ORCHESTRATOR), sqHttpClient(), null)).hasSize(17).containsKeys("foo-bar", PROJECT_KEY_JAVA, PROJECT_KEY_PHP);
  }

  @Test
  void updateNoAuth() {
    adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
    try {
      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClientNoAuth(), PROJECT_KEY_JAVA, null);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).hasMessage("Not authorized. Please check server credentials.");
    } finally {
      adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("false"));
    }
  }

  @Test
  void parsingErrorJava(@TempDir Path tempDir) throws IOException {
    var fileContent = "pac kage its; public class MyTest { }";
    var testFile = tempDir.resolve("MyTestParseError.java");
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    updateProject(PROJECT_KEY_JAVA);

    var issueListener = new SaveIssueListener();
    var results = engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  void parsingErrorJavascript(@TempDir Path tempDir) throws IOException {
    var fileContent = "asd asd";
    var testFile = tempDir.resolve("MyTest.js");
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    updateProject(PROJECT_KEY_JAVASCRIPT);

    var issueListener = new SaveIssueListener();
    var results = engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT, testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  void verifyExtendedDescription() throws Exception {
    updateProject(PROJECT_KEY_JAVA);

    assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey("S106"), PROJECT_KEY_JAVA).get().getExtendedDescription()).isEmpty();

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
    assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey("S106"), PROJECT_KEY_JAVA).get().getExtendedDescription())
      .isEqualTo(expected);
  }

  @Test
  void verifyMarkdownDescription() throws Exception {
    updateProject(PROJECT_KEY_JAVA_MARKDOWN);

    assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), "mycompany-java:markdown", PROJECT_KEY_JAVA_MARKDOWN).get().getHtmlDescription())
      .isEqualTo("<h1>Title</h1><ul><li>one</li>\n"
        + "<li>two</li></ul>");
  }

  @Test
  void analysisJavascript() throws Exception {
    updateProject(PROJECT_KEY_JAVASCRIPT);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT, PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisJavaWithCustomRules() throws Exception {
    updateProject(PROJECT_KEY_JAVA_CUSTOM);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_CUSTOM, PROJECT_KEY_JAVA_CUSTOM,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).extracting("ruleKey", "startLine").containsOnly(
      tuple("mycompany-java:AvoidAnnotation", 12));
  }

  @Test
  void analysisPHP() throws Exception {
    updateProject(PROJECT_KEY_PHP);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_PHP, PROJECT_KEY_PHP, "src/Math.php"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisPython() throws Exception {
    updateProject(PROJECT_KEY_PYTHON);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_PYTHON, PROJECT_KEY_PYTHON, "src/hello.py"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisWeb() throws IOException {
    updateProject(PROJECT_KEY_WEB);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_WEB, PROJECT_KEY_WEB, "src/file.html"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisUseQualityProfile() throws Exception {
    updateProject(PROJECT_KEY_JAVA);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).hasSize(2);
  }

  @Test
  void dontReportHotspotsIfNotEnabled() throws Exception {
    updateProject(PROJECT_KEY_JAVA_HOTSPOT);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_HOTSPOT, PROJECT_KEY_JAVA_HOTSPOT,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  void canFetchHotspot() throws InvalidProtocolBufferException {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 6),
      "SonarQube should support opening security hotspots");

    analyzeMavenProject(PROJECT_KEY_JAVA_HOTSPOT);
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
    var parser = Hotspots.SearchWsResponse.parser();
    return parser.parseFrom(response.getBody()).getHotspots(0).getKey();
  }

  @Test
  void analysisIssueOnDirectory() throws Exception {
    updateProject(PROJECT_KEY_JAVA_PACKAGE);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_PACKAGE, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "inputFile.path").containsOnly(
      tuple(javaRuleKey("S106"), Paths.get("projects/sample-java/src/main/java/foo/Foo.java").toAbsolutePath().toString()),
      tuple(javaRuleKey("S1228"), null));
  }

  @Test
  void customSensorsNotExecuted() throws Exception {
    updateProject(PROJECT_KEY_JAVA_CUSTOM_SENSOR);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_CUSTOM_SENSOR, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  void globalExtension() throws Exception {
    updateProject(PROJECT_KEY_GLOBAL_EXTENSION);

    assertThat(logs).contains("Start Global Extension It works");

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_GLOBAL_EXTENSION, PROJECT_KEY_GLOBAL_EXTENSION,
      "src/foo.glob",
      "sonar.cobol.file.suffixes", "glob"),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "message").containsOnly(
      tuple("global:inc", "Issue number 0"));

    issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_GLOBAL_EXTENSION, PROJECT_KEY_GLOBAL_EXTENSION,
      "src/foo.glob",
      "sonar.cobol.file.suffixes", "glob"),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "message").containsOnly(
      tuple("global:inc", "Issue number 1"));

    engine.stop(true);
    assertThat(logs).contains("Stop Global Extension");
  }

  @Test
  void analysisTemplateRule() throws Exception {
    QualityProfile qp = getQualityProfile(adminWsClient, "SonarLint IT Java");

    WsRequest request = new PostRequest("/api/rules/create")
      .setParam("custom_key", "myrule")
      .setParam("name", "myrule")
      .setParam("markdown_description", "my_rule_description")
      .setParam("params", "methodName=echo;className=foo.Foo;argumentTypes=int")
      .setParam("template_key", javaRuleKey("S2253"))
      .setParam("severity", "MAJOR");
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
      updateProject(PROJECT_KEY_JAVA);

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).hasSize(3);

      assertThat(engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey("myrule"), PROJECT_KEY_JAVA).get().getHtmlDescription())
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
  void analysisUseEmptyQualityProfile() throws Exception {
    updateProject(PROJECT_KEY_JAVA_EMPTY);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_EMPTY, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  void analysisUseConfiguration() throws Exception {
    updateProject(PROJECT_KEY_JAVA);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

    // Override default file suffixes in global props so that input file is not considered as a Java file
    setSettingsMultiValue(null, "sonar.java.file.suffixes", ".foo");
    updateProject(PROJECT_KEY_JAVA);

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).isEmpty();

    // Override default file suffixes in project props so that input file is considered as a Java file again
    setSettingsMultiValue(PROJECT_KEY_JAVA, "sonar.java.file.suffixes", ".java");
    updateProject(PROJECT_KEY_JAVA);

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

  }

  @Test
  void getProject() {
    var api = new ServerApi(endpointParams(ORCHESTRATOR), sqHttpClient()).component();
    assertThat(api.getProject("foo")).isNotPresent();
    assertThat(api.getProject(PROJECT_KEY_RUBY)).isPresent();
  }

  @Test
  void analysisRuby() throws Exception {
    updateProject(PROJECT_KEY_RUBY);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_RUBY, PROJECT_KEY_RUBY, "src/hello.rb"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisKotlin() throws Exception {
    updateProject(PROJECT_KEY_KOTLIN);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_KOTLIN, PROJECT_KEY_KOTLIN, "src/hello.kt"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisScala() throws Exception {
    updateProject(PROJECT_KEY_SCALA);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_SCALA, PROJECT_KEY_SCALA, "src/Hello.scala"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void analysisXml() throws Exception {
    updateProject(PROJECT_KEY_XML);

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_XML, PROJECT_KEY_XML, "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  void cleanOldPlugins() throws IOException {
    updateProject(PROJECT_KEY_JAVA);
    var pluginsFolderPath = globalConfig.getStorageRoot().resolve(encodeForFs("orchestrator")).resolve("plugins");
    var newFile = pluginsFolderPath.resolve("new_file");
    Files.createFile(newFile);
    engine.stop(false);

    engine = new ConnectedSonarLintEngineImpl(globalConfig);

    assertThat(newFile).doesNotExist();
  }

  @Test
  void updatesStorageOnServerEvents() throws IOException, InterruptedException {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 4));

    updateProject(PROJECT_KEY_JAVA);
    Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
    engine.subscribeForEvents(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVA), events::add, null);
    var qualityProfile = getQualityProfile(adminWsClient, "SonarLint IT Java");
    deactivateRule(adminWsClient, qualityProfile, "java:S106");
    waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
      assertThat(events).isNotEmpty();
      assertThat(events.getLast())
        .isInstanceOfSatisfying(RuleSetChangedEvent.class, e -> {
          assertThat(e.getDeactivatedRules()).containsOnly("java:S106");
          assertThat(e.getActivatedRules()).isEmpty();
          assertThat(e.getProjectKeys()).containsOnly(PROJECT_KEY_JAVA);
        });
    });

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA, "src/main/java/foo/Foo.java"), issueListener, null, null);
    assertThat(issueListener.getIssues())
      .extracting(Issue::getRuleKey)
      .containsOnly("java:S2325");
  }

  @Test
  void updatesStorageWhenIssueResolvedOnServer() throws InterruptedException {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6));

    updateProject(PROJECT_KEY_JAVA);
    Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
    engine.subscribeForEvents(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVA), events::add, null);
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
          assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA);
        });
    });

    var serverIssues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY_JAVA, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/Foo.java");

    assertThat(serverIssues)
      .extracting(ServerIssue::getRuleKey, ServerIssue::isResolved)
      .contains(tuple("java:S106", true));
  }
  
  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(singletonList(value))
      .setComponent(moduleKey));
  }

  private void updateProject(String projectKey) {
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), null);
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, MAIN_BRANCH_NAME, null);
  }

  private static void analyzeMavenProject(String projectKey, String projectDirName) {
    analyzeMavenProject(ORCHESTRATOR, projectDirName, Map.of("sonar.projectKey", projectKey));
  }

  private static void analyzeMavenProject(String projectKey) {
    analyzeMavenProject(projectKey, projectKey);
  }

}
