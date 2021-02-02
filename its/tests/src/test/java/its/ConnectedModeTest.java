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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.util.NetworkUtils;
import its.tools.ItUtils;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.MovedContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.Hotspots;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.WsResponse;
import org.sonarqube.ws.client.qualityprofiles.ActivateRuleRequest;
import org.sonarqube.ws.client.qualityprofiles.SearchRequest;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.hotspot.SecurityHotspotsService;

import static its.tools.ItUtils.SONAR_VERSION;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class ConnectedModeTest extends AbstractConnectedTest {

  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String PROJECT_KEY_JAVA_CUSTOM_SENSOR = "sample-java-custom-sensor";
  private static final String PROJECT_KEY_GLOBAL_EXTENSION = "sample-global-extension";
  private static final String PROJECT_KEY_JAVA_PACKAGE = "sample-java-package";
  private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";
  private static final String PROJECT_KEY_JAVA_EMPTY = "sample-java-empty";
  private static final String PROJECT_KEY_PHP = "sample-php";
  private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
  private static final String PROJECT_KEY_JAVASCRIPT_CUSTOM = "sample-javascript-custom";
  private static final String PROJECT_KEY_PYTHON = "sample-python";
  private static final String PROJECT_KEY_WEB = "sample-web";
  private static final String PROJECT_KEY_KOTLIN = "sample-kotlin";
  private static final String PROJECT_KEY_RUBY = "sample-ruby";
  private static final String PROJECT_KEY_SCALA = "sample-scala";
  private static final String PROJECT_KEY_XML = "sample-xml";

  private static String javaRuleKey(String key) {
    return ItUtils.javaVersion.equals("LATEST_RELEASE") ? ("java:" + key) : ("squid:" + key);
  }

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .defaultForceAuthentication()
    .setSonarVersion(SONAR_VERSION)
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", ItUtils.javaVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.python", "sonar-python-plugin", ItUtils.pythonVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.php", "sonar-php-plugin", ItUtils.phpVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", ItUtils.javascriptVersion))
    // With recent version of SonarJS, SonarTS is required
    .addPlugin(MavenLocation.of("org.sonarsource.typescript", "sonar-typescript-plugin", ItUtils.typescriptVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.slang", "sonar-kotlin-plugin", ItUtils.kotlinVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.slang", "sonar-ruby-plugin", ItUtils.rubyVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.slang", "sonar-scala-plugin", ItUtils.scalaVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.html", "sonar-html-plugin", ItUtils.webVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.xml", "sonar-xml-plugin", ItUtils.xmlVersion))
    .addPlugin(FileLocation.of("../plugins/global-extension-plugin/target/global-extension-plugin.jar"))
    .addPlugin(FileLocation.of("../plugins/custom-sensor-plugin/target/custom-sensor-plugin.jar"))
    .addPlugin(FileLocation.of("../plugins/javascript-custom-rules/target/javascript-custom-rules-plugin.jar"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/global-extension.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-package.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-empty-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/javascript-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/javascript-custom.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/php-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/python-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/custom-sensor.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/web-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/kotlin-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/ruby-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/scala-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/xml-sonarlint.xml"))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private List<String> logs;

  private static Server server;
  private static int redirectPort;

  @BeforeClass
  public static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    sonarUserHome = temp.newFolder().toPath();

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVA, "Sample Java");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVA_PACKAGE, "Sample Java Package");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVA_EMPTY, "Sample Java Empty");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_PHP, "Sample PHP");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVASCRIPT_CUSTOM, "Sample Javascript Custom");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_PYTHON, "Sample Python");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_WEB, "Sample Web");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVA_CUSTOM_SENSOR, "Sample Java Custom");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_GLOBAL_EXTENSION, "Sample Global Extension");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_RUBY, "Sample Ruby");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_KOTLIN, "Sample Kotlin");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_SCALA, "Sample Scala");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_XML, "Sample XML");

    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_PACKAGE, "java", "SonarLint IT Java Package");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_EMPTY, "java", "SonarLint IT Java Empty");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_PHP, "php", "SonarLint IT PHP");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT_CUSTOM, "js", "SonarLint IT Javascript Custom");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_PYTHON, "py", "SonarLint IT Python");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_WEB, "web", "SonarLint IT Web");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_CUSTOM_SENSOR, "java", "SonarLint IT Custom Sensor");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_RUBY, "ruby", "SonarLint IT Ruby");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_KOTLIN, "kotlin", "SonarLint IT Kotlin");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_SCALA, "scala", "SonarLint IT Scala");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_XML, "xml", "SonarLint IT XML");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_GLOBAL_EXTENSION, "xoo", "SonarLint IT Global Extension");

    // Build project to have bytecode
    ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-java/pom.xml")).setGoals("clean compile"));

    prepareRedirectServer();
  }

  private static void prepareRedirectServer() throws Exception {
    redirectPort = NetworkUtils.getNextAvailablePort(InetAddress.getLoopbackAddress());
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    server = new Server(threadPool);
    // HTTP Configuration
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    // Moved handler
    MovedContextHandler movedContextHandler = new MovedContextHandler();
    movedContextHandler.setPermanent(true);
    movedContextHandler.setNewContextURL(ORCHESTRATOR.getServer().getUrl());
    server.setHandler(movedContextHandler);

    // http connector
    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    http.setPort(redirectPort);
    server.addConnector(http);
    server.start();
  }

  @AfterClass
  public static void after() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    Map<String, String> globalProps = new HashMap<>();
    globalProps.put("sonar.global.label", "It works");
    logs = new ArrayList<>();

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
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
      .addEnabledLanguage(Language.XOO)
      .setLogOutput((msg, level) -> {
        logs.add(msg);
      })
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setExtraProperties(globalProps)
      .build());
    assertThat(engine.getGlobalStorageStatus()).isNull();
    assertThat(engine.getState()).isEqualTo(State.NEVER_UPDATED);

    // This profile is altered in a test
    ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
  }

  @After
  public void stop() {
    adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
    adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")).setComponent(PROJECT_KEY_JAVA));
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void downloadProjects() {
    updateGlobal();
    assertThat(engine.allProjectsByKey()).hasSize(15);
    ORCHESTRATOR.getServer().provisionProject("foo-bar", "Foo");
    assertThat(engine.downloadAllProjects(getServerConfig(), null)).hasSize(16).containsKeys("foo-bar", PROJECT_KEY_JAVA, PROJECT_KEY_PHP);
    assertThat(engine.allProjectsByKey()).hasSize(16).containsKeys("foo-bar", PROJECT_KEY_JAVA, PROJECT_KEY_PHP);
  }

  @Test
  public void updateNoAuth() {
    adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
    try {
      engine.update(ServerConfiguration.builder()
        .url(ORCHESTRATOR.getServer().getUrl())
        .userAgent("SonarLint ITs")
        .build(), null);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).hasMessage("Not authorized. Please check server credentials.");
    } finally {
      adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("false"));
    }
  }

  @Test
  public void parsingErrorJava() throws IOException {
    String fileContent = "pac kage its; public class MyTest { }";
    Path testFile = temp.newFile("MyTestParseError.java").toPath();
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    SaveIssueListener issueListener = new SaveIssueListener();
    AnalysisResults results = engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  public void parsingErrorJavascript() throws IOException {
    String fileContent = "asd asd";
    Path testFile = temp.newFile("MyTest.js").toPath();
    Files.write(testFile, fileContent.getBytes(StandardCharsets.UTF_8));

    updateGlobal();
    updateProject(PROJECT_KEY_JAVASCRIPT);

    SaveIssueListener issueListener = new SaveIssueListener();
    AnalysisResults results = engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT, testFile.toString()), issueListener, null, null);

    assertThat(results.failedAnalysisFiles()).hasSize(1);
  }

  @Test
  public void globalUpdate() {
    updateGlobal();

    assertThat(engine.getState()).isEqualTo(State.UPDATED);
    assertThat(engine.getGlobalStorageStatus()).isNotNull();
    assertThat(engine.getGlobalStorageStatus().isStale()).isFalse();
    assertThat(engine.getGlobalStorageStatus().getServerVersion()).startsWith(StringUtils.substringBefore(ORCHESTRATOR.getServer().version().toString(), "-"));
    assertThat(engine.getRuleDetails(javaRuleKey("S106")).getHtmlDescription()).contains("When logging a message there are");

    assertThat(engine.getProjectStorageStatus(PROJECT_KEY_JAVA)).isNull();
  }

  @Test
  public void updateProject() {
    updateGlobal();

    updateProject(PROJECT_KEY_JAVA);

    assertThat(engine.getProjectStorageStatus(PROJECT_KEY_JAVA)).isNotNull();
  }

  @Test
  public void verifyExtendedDescription() {
    updateGlobal();

    assertThat(engine.getRuleDetails(javaRuleKey("S106")).getExtendedDescription()).isEmpty();

    String extendedDescription = "my dummy extended description";

    WsRequest request = new PostRequest("/api/rules/update")
      .setParam("key", javaRuleKey("S106"))
      .setParam("markdown_note", extendedDescription);
    try (WsResponse response = adminWsClient.wsConnector().call(request)) {
      assertThat(response.code()).isEqualTo(200);
    }

    updateGlobal();

    assertThat(engine.getRuleDetails(javaRuleKey("S106")).getExtendedDescription()).isEqualTo(extendedDescription);
  }

  @Test
  public void analysisJavascript() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVASCRIPT);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT, PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisJavascriptWithCustomRules() throws Exception {
    // old SonarJS versions does not report this correctly
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThan(6, 7));

    updateGlobal();
    updateProject(PROJECT_KEY_JAVASCRIPT_CUSTOM);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT_CUSTOM, PROJECT_KEY_JAVASCRIPT_CUSTOM, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).extracting("ruleKey", "startLine").containsOnly(
      tuple("custom-javascript-repo:S1", 3),
      tuple("custom-javascript-repo:S1", 7));
  }

  @Test
  public void analysisPHP() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_PHP);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_PHP, PROJECT_KEY_PHP, "src/Math.php"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisPython() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_PYTHON);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_PYTHON, PROJECT_KEY_PYTHON, "src/hello.py"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisWeb() throws IOException {
    updateGlobal();
    updateProject(PROJECT_KEY_WEB);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_WEB, PROJECT_KEY_WEB, "src/file.html"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisUseQualityProfile() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).hasSize(2);
  }

  @Test
  public void dontReportHotspots() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA_HOTSPOT);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_HOTSPOT, PROJECT_KEY_JAVA_HOTSPOT,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(7, 3)) {
      assertThat(issueListener.getIssues()).isEmpty();
    } else {
      // Hotspots are reported as vulnerability
      assertThat(issueListener.getIssues()).hasSize(1);
      assertThat(issueListener.getIssues().get(0).getType()).isEqualTo("VULNERABILITY");
    }
  }

  @Test
  public void canFetchHotspot() throws InvalidProtocolBufferException {
    assumeTrue("SonarQube should support opening security hotspots",
      ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 6));

    analyzeMavenProject(PROJECT_KEY_JAVA_HOTSPOT);
    SonarLintWsClient slClient = new SonarLintWsClient(getServerConfig());
    SecurityHotspotsService securityHotspotsService = new SecurityHotspotsService(slClient);

    Optional<RemoteHotspot> remoteHotspot = securityHotspotsService
      .fetch(new GetSecurityHotspotRequestParams(getFirstHotspotKey(slClient, PROJECT_KEY_JAVA_HOTSPOT), PROJECT_KEY_JAVA_HOTSPOT));

    assertThat(remoteHotspot).isNotEmpty();
    RemoteHotspot actualHotspot = remoteHotspot.get();
    assertThat(actualHotspot.message).isEqualTo("Make sure using this hardcoded IP address is safe here.");
    assertThat(actualHotspot.filePath).isEqualTo("src/main/java/foo/Foo.java");
    assertThat(actualHotspot.textRange).isEqualToComparingFieldByField(new TextRange(5, 14, 5, 29));
    assertThat(actualHotspot.author).isEmpty();
    assertThat(actualHotspot.status).isEqualTo(RemoteHotspot.Status.TO_REVIEW);
    assertThat(actualHotspot.resolution).isNull();
    assertThat(actualHotspot.rule.key).isEqualTo("java:S1313");

  }

  private String getFirstHotspotKey(SonarLintWsClient client, String projectKey) throws InvalidProtocolBufferException {
    org.sonarsource.sonarlint.core.util.ws.WsResponse wsResponse = client.get("/api/hotspots/search.protobuf?projectKey=" + projectKey);
    Parser<Hotspots.SearchWsResponse> parser = Hotspots.SearchWsResponse.parser();
    return parser.parseFrom(wsResponse.contentStream()).getHotspots(0).getKey();
  }

  @Test
  public void analysisIssueOnDirectory() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA_PACKAGE);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_PACKAGE, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "inputFile.path").containsOnly(
      tuple(javaRuleKey("S106"), Paths.get("projects/sample-java/src/main/java/foo/Foo.java").toAbsolutePath().toString()),
      tuple(javaRuleKey("S1228"), null));
  }

  @Test
  public void customSensorsNotExecuted() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA_CUSTOM_SENSOR);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_CUSTOM_SENSOR, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void globalExtension() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_GLOBAL_EXTENSION);

    assertThat(logs).contains("Start Global Extension It works");

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_GLOBAL_EXTENSION, PROJECT_KEY_GLOBAL_EXTENSION,
      "src/foo.glob",
      "sonar.xoo.file.suffixes", "glob"),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "message").containsOnly(
      tuple("global:inc", "Issue number 0"));

    issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_GLOBAL_EXTENSION, PROJECT_KEY_GLOBAL_EXTENSION,
      "src/foo.glob",
      "sonar.xoo.file.suffixes", "glob"),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).extracting("ruleKey", "message").containsOnly(
      tuple("global:inc", "Issue number 1"));

    engine.stop(true);
    assertThat(logs).contains("Stop Global Extension");
  }

  @Test
  public void analysisJavaPomXml() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA, "pom.xml"), issueListener, null, null);

    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisTemplateRule() throws Exception {
    SearchRequest searchReq = new SearchRequest();
    searchReq.setQualityProfile("SonarLint IT Java");
    searchReq.setProject(PROJECT_KEY_JAVA);
    searchReq.setDefaults("false");
    SearchWsResponse search = adminWsClient.qualityprofiles().search(searchReq);
    QualityProfile qp = null;
    for (QualityProfile q : search.getProfilesList()) {
      if (q.getName().equals("SonarLint IT Java")) {
        qp = q;
      }
    }
    assertThat(qp).isNotNull();

    WsRequest request = new PostRequest("/api/rules/create")
      .setParam("custom_key", "myrule")
      .setParam("name", "myrule")
      .setParam("markdown_description", "my_rule_description")
      .setParam("params", "methodName=echo;className=foo.Foo;argumentTypes=int")
      .setParam("template_key", javaRuleKey("S2253"))
      .setParam("severity", "MAJOR");
    try (WsResponse response = adminWsClient.wsConnector().call(request)) {
      assertTrue(response.isSuccessful());
    }

    request = new PostRequest("/api/qualityprofiles/activate_rule")
      .setParam("key", qp.getKey())
      .setParam("rule", javaRuleKey("myrule"));
    try (WsResponse response = adminWsClient.wsConnector().call(request)) {
      assertTrue("Unable to activate custom rule", response.isSuccessful());
    }

    try {

      updateGlobal();
      updateProject(PROJECT_KEY_JAVA);

      SaveIssueListener issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
        "src/main/java/foo/Foo.java",
        "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
        issueListener, null, null);

      assertThat(issueListener.getIssues()).hasSize(3);

      assertThat(engine.getRuleDetails(javaRuleKey("myrule")).getHtmlDescription()).contains("my_rule_description");

    } finally {

      request = new PostRequest("/api/rules/delete")
        .setParam("key", javaRuleKey("myrule"));
      try (WsResponse response = adminWsClient.wsConnector().call(request)) {
        assertTrue("Unable to delete custom rule", response.isSuccessful());
      }
    }
  }

  @Test
  public void analysisUseEmptyQualityProfile() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA_EMPTY);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_EMPTY, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);

    assertThat(issueListener.getIssues()).isEmpty();
  }

  @Test
  public void analysisUseConfiguration() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

    // Override default file suffixes in global props so that input file is not considered as a Java file
    setSettingsMultiValue(null, "sonar.java.file.suffixes", ".foo");
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).isEmpty();

    // Override default file suffixes in project props so that input file is considered as a Java file again
    setSettingsMultiValue(PROJECT_KEY_JAVA, "sonar.java.file.suffixes", ".java");
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    issueListener.clear();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA, PROJECT_KEY_JAVA,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath()),
      issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2);

  }

  @Test
  public void generateToken() {
    WsHelper ws = new WsHelperImpl();
    ServerConfiguration serverConfig = getServerConfig(true);

    String token = ws.generateAuthenticationToken(serverConfig, "name", false);
    assertThat(token).isNotNull();

    token = ws.generateAuthenticationToken(serverConfig, "name", true);
    assertThat(token).isNotNull();
  }

  @Test
  public void checkForUpdate() {
    updateGlobal();
    updateProject(PROJECT_KEY_JAVA);

    ServerConfiguration serverConfig = getServerConfig(true);

    StorageUpdateCheckResult result = engine.checkIfGlobalStorageNeedUpdate(serverConfig, null);
    assertThat(result.needUpdate()).isFalse();

    // restarting server should not lead to notify an update
    ORCHESTRATOR.restartServer();
    result = engine.checkIfGlobalStorageNeedUpdate(serverConfig, null);
    assertThat(result.needUpdate()).isFalse();

    // Change a global setting that is not in the whitelist
    setSettings(null, "sonar.foo", "bar");
    result = engine.checkIfGlobalStorageNeedUpdate(serverConfig, null);
    assertThat(result.needUpdate()).isFalse();

    // Change a global setting that *is* in the whitelist
    setSettingsMultiValue(null, "sonar.inclusions", "**/*");
    // Activate a new rule
    SearchWsResponse response = newAdminWsClient(ORCHESTRATOR).qualityprofiles().search(new SearchRequest().setLanguage("java"));
    String profileKey = response.getProfilesList().stream().filter(p -> p.getName().equals("SonarLint IT Java")).findFirst().get().getKey();
    adminWsClient.qualityprofiles().activateRule(new ActivateRuleRequest().setKey(profileKey).setRule(javaRuleKey("S1228")));

    result = engine.checkIfGlobalStorageNeedUpdate(serverConfig, null);
    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated", "Quality profile 'SonarLint IT Java' for language 'Java' updated");

    result = engine.checkIfProjectStorageNeedUpdate(serverConfig, PROJECT_KEY_JAVA, null);
    assertThat(result.needUpdate()).isFalse();

    // Change a project setting that is not in the whitelist
    setSettings(PROJECT_KEY_JAVA, "sonar.foo", "biz");
    result = engine.checkIfProjectStorageNeedUpdate(serverConfig, PROJECT_KEY_JAVA, null);
    assertThat(result.needUpdate()).isFalse();

    // Change a project setting that *is* in the whitelist
    setSettingsMultiValue(PROJECT_KEY_JAVA, "sonar.exclusions", "**/*.foo");

    result = engine.checkIfProjectStorageNeedUpdate(serverConfig, PROJECT_KEY_JAVA, null);
    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
  }

  @Test
  public void getProject() {
    WsHelper helper = new WsHelperImpl();
    assertThat(helper.getProject(getServerConfig(), "foo", null)).isNotPresent();
    assertThat(helper.getProject(getServerConfig(), PROJECT_KEY_RUBY, null)).isPresent();
  }

  @Test
  public void analysisRuby() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_RUBY);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_RUBY, PROJECT_KEY_RUBY, "src/hello.rb"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisKotlin() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_KOTLIN);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_KOTLIN, PROJECT_KEY_KOTLIN, "src/hello.kt"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisScala() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_SCALA);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_SCALA, PROJECT_KEY_SCALA, "src/Hello.scala"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisXml() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_XML);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_XML, PROJECT_KEY_XML, "src/foo.xml"), issueListener, (m, l) -> System.out.println(m), null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  private void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(singletonList(value))
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
    return getServerConfig(false);
  }

  private ServerConfiguration getServerConfig(boolean redirect) {
    return ServerConfiguration.builder()
      .url(redirect ? ("http://localhost:" + redirectPort) : ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build();
  }

  private static void analyzeMavenProject(String projectDirName) {
    Path projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    Path pom = projectDir.resolve("pom.xml");
    ORCHESTRATOR.executeBuild(MavenBuild.create(pom.toFile())
      .setCleanPackageSonarGoals()
      .setProperty("sonar.projectKey", projectDirName)
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
  }

}
