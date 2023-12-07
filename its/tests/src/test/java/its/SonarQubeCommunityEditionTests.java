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

import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.ConsoleLogOutput;
import its.utils.ItUtils;
import its.utils.OrchestratorUtils;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.issues.DoTransitionRequest;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.issues.SetSeverityRequest;
import org.sonarqube.ws.client.issues.SetTypeRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static its.utils.ItUtils.SONAR_VERSION;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class SonarQubeCommunityEditionTests extends AbstractConnectedTests {
  public static final String CONNECTION_ID = "orchestrator";

  public static final String XOO_PLUGIN_KEY = "xoo";
  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .addPlugin(MavenLocation.of("org.sonarsource.sonarqube", "sonar-xoo-plugin", SONAR_VERSION))
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private static WsClient adminWsClient;

  @TempDir
  private static Path sonarUserHome;

  private static SonarLintRpcServer backend;

  private static BackendJsonRpcLauncher serverLauncher;

  @BeforeAll
  static void startBackend() throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    try {
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO,
          IT_TELEMETRY_ATTRIBUTES, new FeatureFlagsDto(false, true, false, false, false, false, false), sonarUserHome.resolve("storage"),
          sonarUserHome.resolve("workDir"),
          Collections.emptySet(), Collections.emptyMap(), Set.of(JAVA), Collections.emptySet(),
          List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), Collections.emptyList(), sonarUserHome.toString(),
          Map.of(), false))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    serverLauncher.getJavaImpl().shutdown().get();
  }

  @BeforeAll
  static void createSonarLintUser() {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithXoo {

    private static final String XOO_PROJECT_KEY = "sample-xoo";

    private ConnectedSonarLintEngine engine;
    private final List<String> logs = new ArrayList<>();

    private Issue wfIssue;
    private Issue fpIssue;
    private Issue overridenSeverityIssue;
    private Issue overridenTypeIssue;

    @BeforeAll
    void prepare() {
      provisionProject(ORCHESTRATOR, XOO_PROJECT_KEY, "Sample Xoo");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/xoo-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(XOO_PROJECT_KEY, XOO_PLUGIN_KEY, "SonarLint IT Xoo");

      analyzeProject("sample-xoo-v1", XOO_PROJECT_KEY);
      // Second analysis with less issues to have closed issues
      analyzeProject("sample-xoo-v2", XOO_PROJECT_KEY);

      // Mark a few issues as closed WF and closed FP
      var issueSearchResponse = adminWsClient.issues()
        .search(new SearchRequest().setStatuses(List.of("OPEN")).setTypes(List.of("CODE_SMELL")).setComponentKeys(List.of(XOO_PROJECT_KEY)));
      wfIssue = issueSearchResponse.getIssues(0);
      fpIssue = issueSearchResponse.getIssues(1);
      // Change severity and type
      overridenSeverityIssue = issueSearchResponse.getIssues(2);
      overridenTypeIssue = issueSearchResponse.getIssues(3);

      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(wfIssue.getKey()).setTransition("wontfix"));
      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(fpIssue.getKey()).setTransition("falsepositive"));

      adminWsClient.issues().setSeverity(new SetSeverityRequest().setIssue(overridenSeverityIssue.getKey()).setSeverity("BLOCKER"));
      adminWsClient.issues().setType(new SetTypeRequest().setIssue(overridenTypeIssue.getKey()).setType("BUG"));

      // Ensure an hotspot has been reported on server side
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 2)) {
        assertThat(adminWsClient.hotspots().search(new org.sonarqube.ws.client.hotspots.SearchRequest().setProjectKey(XOO_PROJECT_KEY)).getHotspotsList()).isNotEmpty();
      } else {
        assertThat(
          adminWsClient.issues().search(new SearchRequest().setTypes(List.of("SECURITY_HOTSPOT")).setComponentKeys(List.of(XOO_PROJECT_KEY))).getIssuesList())
          .isNotEmpty();
      }
    }

    @BeforeEach
    public void start(@TempDir Path sonarUserHome) throws IOException {
      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId("orchestrator")
        .setSonarLintUserHome(sonarUserHome)
        .setLogOutput((msg, level) -> logs.add(msg))
        .setExtraProperties(new HashMap<>())
        .build());
    }

    @AfterEach
    public void stop() {
      engine.stop(true);
    }

    @Test
    void download_all_issues_not_limited_to_10k() {
      engine.updateProject(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), XOO_PROJECT_KEY, null);

      engine.downloadAllServerIssues(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), XOO_PROJECT_KEY, MAIN_BRANCH_NAME, null);

      var file1Issues = engine.getServerIssues(new ProjectBinding(XOO_PROJECT_KEY, "", ""), MAIN_BRANCH_NAME, "src/500lines.xoo");
      var file2Issues = engine.getServerIssues(new ProjectBinding(XOO_PROJECT_KEY, "", ""), MAIN_BRANCH_NAME, "src/10000lines.xoo");

      // Number of issues is not limited to 10k
      assertThat(file1Issues.size() + file2Issues.size()).isEqualTo(10_500);

      Map<String, ServerIssue> allIssues = new HashMap<>();
      engine.getServerIssues(new ProjectBinding(XOO_PROJECT_KEY, "", ""), MAIN_BRANCH_NAME, "src/500lines.xoo").forEach(i -> allIssues.put(i.getKey(), i));
      engine.getServerIssues(new ProjectBinding(XOO_PROJECT_KEY, "", ""), MAIN_BRANCH_NAME, "src/10000lines.xoo").forEach(i -> allIssues.put(i.getKey(), i));

      assertThat(allIssues).hasSize(10_500);
      assertThat(allIssues.get(wfIssue.getKey()).isResolved()).isTrue();
      assertThat(allIssues.get(fpIssue.getKey()).isResolved()).isTrue();
      assertThat(allIssues.get(overridenSeverityIssue.getKey()).getUserSeverity()).isEqualTo(IssueSeverity.BLOCKER);
      assertThat(allIssues.get(overridenTypeIssue.getKey()).getType()).isEqualTo(RuleType.BUG);

      // No hotspots
      assertThat(allIssues.values()).allSatisfy(i -> assertThat(i.getType()).isIn(RuleType.CODE_SMELL, RuleType.BUG, RuleType.VULNERABILITY));
    }

    @Nested
    // TODO Can be removed when switching to Java 16+ and changing prepare() to static
    @TestInstance(Lifecycle.PER_CLASS)
    class PathPrefix {
      private static final String MULTI_MODULE_PROJECT_KEY = "com.sonarsource.it.samples:multi-modules-sample";

      @BeforeAll
      void analyzeMultiModuleProject() {
        // Project has 5 modules: B, B/B1, B/B2, A, A/A1 and A/A2
        analyzeMavenProject(ORCHESTRATOR, "multi-modules-sample");
      }

      @Test
      void should_apply_path_prefixes_when_importing_entire_project() throws IOException {
        engine.updateProject(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), MULTI_MODULE_PROJECT_KEY, null);

        // entire project imported in IDE
        var projectDir = Paths.get("projects/multi-modules-sample").toAbsolutePath();
        var ideFiles = ItUtils.collectAllFiles(projectDir).stream()
          .map(f -> projectDir.relativize(f).toString())
          .collect(Collectors.toList());

        var projectBinding = engine.calculatePathPrefixes(MULTI_MODULE_PROJECT_KEY, ideFiles);
        assertThat(projectBinding.serverPathPrefix()).isEmpty();
        assertThat(projectBinding.idePathPrefix()).isEmpty();
        engine.downloadAllServerIssuesForFile(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), projectBinding,
          "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java", MAIN_BRANCH_NAME, null);
        var serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
        if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
          assertThat(serverIssues).isEmpty();
          assertThat(logs).contains("Skip downloading file issues on SonarQube 9.6+");
        } else {
          assertThat(serverIssues).hasSize(2);
        }
        engine.syncServerIssues(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), MULTI_MODULE_PROJECT_KEY, MAIN_BRANCH_NAME, null);
        if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
          assertThat(logs).contains("Incremental issue sync is not supported. Skipping.");
        }
        serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
        assertThat(serverIssues).hasSize(2);
      }

      @Test
      void should_apply_path_prefixes_when_importing_module() throws IOException {
        engine.updateProject(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), MULTI_MODULE_PROJECT_KEY, null);

        // only module B1 imported in IDE
        var projectDirB1 = Paths.get("projects/multi-modules-sample/module_b/module_b1").toAbsolutePath();
        var ideFiles = ItUtils.collectAllFiles(projectDirB1).stream()
          .map(f -> projectDirB1.relativize(f).toString())
          .collect(Collectors.toList());

        var projectBinding = engine.calculatePathPrefixes(MULTI_MODULE_PROJECT_KEY, ideFiles);
        assertThat(projectBinding.serverPathPrefix()).isEqualTo("module_b/module_b1");
        assertThat(projectBinding.idePathPrefix()).isEmpty();
        engine.downloadAllServerIssuesForFile(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), projectBinding,
          "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java", MAIN_BRANCH_NAME, null);
        var serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
        if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
          assertThat(serverIssues).isEmpty();
          assertThat(logs).contains("Skip downloading file issues on SonarQube 9.6+");
        } else {
          assertThat(serverIssues).hasSize(2);
        }
        engine.syncServerIssues(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), MULTI_MODULE_PROJECT_KEY, MAIN_BRANCH_NAME, null);
        if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
          assertThat(logs).contains("Incremental issue sync is not supported. Skipping.");
        }
        serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
        assertThat(serverIssues).hasSize(2);
      }
    }
  }

  @Nested
  class StorageSynchronizationWithOnlyJavaEnabled {

    private static final String PROJECT_KEY_LANGUAGE_MIX = "sample-language-mix";

    private ConnectedSonarLintEngine engineWithJavaOnly;

    @BeforeEach
    void prepare(@TempDir Path sonarUserHome) throws IOException {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_LANGUAGE_MIX, "Sample Language Mix");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/python-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "java", "SonarLint IT Java");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "py", "SonarLint IT Python");

      // Build project to have bytecode and analyze
      ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-language-mix/pom.xml"))
        .setCleanPackageSonarGoals()
        .setProperty("sonar.projectKey", PROJECT_KEY_LANGUAGE_MIX)
        .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
        .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));

      engineWithJavaOnly = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setLogOutput(new ConsoleLogOutput(false))
        .setConnectionId("orchestrator")
        .setSonarLintUserHome(sonarUserHome)
        .setExtraProperties(new HashMap<>())
        // authorize only Java to check that Python is left aside during sync
        .addEnabledLanguage(Language.JAVA)
        .build());

    }

    @AfterEach
    public void stop() {
      engineWithJavaOnly.stop(true);
    }

    @Test
    // SonarQube should support pulling issues
    @OnlyOnSonarQube(from = "9.6")
    void sync_all_issues_of_enabled_languages() {
      engineWithJavaOnly.syncServerIssues(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), PROJECT_KEY_LANGUAGE_MIX, MAIN_BRANCH_NAME,
        null);

      var javaIssues = engineWithJavaOnly.getServerIssues(new ProjectBinding(PROJECT_KEY_LANGUAGE_MIX, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/Foo.java");
      var pythonIssues = engineWithJavaOnly.getServerIssues(new ProjectBinding(PROJECT_KEY_LANGUAGE_MIX, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/main.py");

      assertThat(javaIssues).hasSize(2);
      assertThat(pythonIssues).isEmpty();
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(Lifecycle.PER_CLASS)
  class PluginRequirementsAndDependencies {

    private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";
    private static final String CUSTOM_JAVA_PLUGIN_KEY = "custom";
    private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
    private static final String PROJECT_KEY_TYPESCRIPT = "sample-typescript";

    @BeforeAll
    void prepare() throws Exception {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
      provisionProject(ORCHESTRATOR, PROJECT_KEY_TYPESCRIPT, "Sample Typescript");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/javascript-sonarlint.xml"));
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/typescript-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_TYPESCRIPT, "ts", "SonarLint IT Typescript");
    }

    @TempDir
    private Path sonarUserHome;

    private ConnectedSonarLintEngine engine;
    private final List<String> logs = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() {
      FileUtils.deleteQuietly(sonarUserHome.toFile());
    }

    private ConnectedSonarLintEngine createEngine(Consumer<ConnectedGlobalConfiguration.Builder> configurator) {
      var nodeJsHelper = new NodeJsHelper((m, l) -> System.out.println(l + " " + m));
      nodeJsHelper.detect(null);

      var builder = ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId("orchestrator")
        .setSonarLintUserHome(sonarUserHome)
        .setLogOutput((msg, level) -> {
          logs.add(msg);
        })
        .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion());
      configurator.accept(builder);
      return new ConnectedSonarLintEngineImpl(builder.build());
    }

    @AfterEach
    void stop() {
      try {
        engine.stop(true);
      } catch (Exception e) {
        // Ignore
      }
    }

    @Test
    void dontDownloadPluginIfNotEnabledLanguage() {
      engine = createEngine(e -> e.addEnabledLanguages(Language.JS, Language.PHP, Language.TS));
      engine.sync(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), emptySet(), null);
      assertThat(logs).contains("[SYNC] Code analyzer 'java' is disabled in SonarLint (language not enabled). Skip downloading it.");
      // TypeScript plugin has been merged in SonarJS in SQ 8.5
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 5)) {
        assertThat(engine.getPluginDetails().stream().map(PluginDetails::key))
          .containsOnly(Language.JS.getPluginKey(), Language.PHP.getPluginKey(), CUSTOM_JAVA_PLUGIN_KEY, XOO_PLUGIN_KEY);
      } else {
        assertThat(engine.getPluginDetails().stream().map(PluginDetails::key))
          .containsOnly(Language.JS.getPluginKey(), Language.PHP.getPluginKey(), OLD_SONARTS_PLUGIN_KEY, CUSTOM_JAVA_PLUGIN_KEY, XOO_PLUGIN_KEY);
      }
    }

    @Test
    void dontFailIfMissingDependentPlugin() {
      engine = createEngine(e -> e.addEnabledLanguages(Language.PHP));
      engine.sync(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), emptySet(), null);
      assertThat(logs).contains("Plugin 'Java Custom Rules Plugin' dependency on 'java' is unsatisfied. Skip loading it.");
      assertThat(engine.getPluginDetails()).extracting(PluginDetails::key, PluginDetails::skipReason)
        .contains(tuple(CUSTOM_JAVA_PLUGIN_KEY, Optional.of(new SkipReason.UnsatisfiedDependency("java"))));
    }

    @Test
    void dontLoadExcludedPlugin() {
      engine = createEngine(e -> e.addEnabledLanguages(Language.JAVA, Language.JS, Language.PHP));
      engine.sync(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), emptySet(), null);
      assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).contains(Language.JAVA.getPluginKey());
      engine.stop(false);

      engine = createEngine(e -> e.addEnabledLanguages(Language.JS, Language.PHP));
      // The description of SonarJava changed in 6.3, embedded in SQ 8.3
      var javaDescription = ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 3) ? "Java Code Quality and Security" : "SonarJava";
      var expectedLog = String.format("Plugin '%s' is excluded because language 'Java' is not enabled. Skip loading it.", javaDescription);
      assertThat(logs).contains(expectedLog);
      assertThat(engine.getPluginDetails()).extracting(PluginDetails::key, PluginDetails::skipReason)
        .contains(tuple(Language.JAVA.getPluginKey(), Optional.of(new SkipReason.LanguagesNotEnabled(asList(Language.JAVA)))));
    }

    // SLCORE-259
    @Test
    void analysisJavascriptWithoutTypescript() throws Exception {
      engine = createEngine(e -> e.addEnabledLanguages(Language.JS, Language.PHP));
      engine.sync(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), Set.of(PROJECT_KEY_JAVASCRIPT), null);
      assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).contains("javascript");
      assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).doesNotContain(OLD_SONARTS_PLUGIN_KEY);

      engine.updateProject(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), PROJECT_KEY_JAVASCRIPT, null);
      engine.sync(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), Set.of(PROJECT_KEY_JAVASCRIPT), null);
      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT, PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }
  }

  private static void analyzeProject(String projectDirName, String projectKey) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    ORCHESTRATOR.executeBuild(SonarScanner.create(projectDir.toFile())
      .setProjectKey(projectKey)
      .setSourceDirs("src")
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
  }

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD));
        }
        return super.getCredentials(connectionId);
      }

    };
  }
}
