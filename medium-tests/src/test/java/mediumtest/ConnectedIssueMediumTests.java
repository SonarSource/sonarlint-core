/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintModuleFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileListener;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.OnDiskTestClientInputFile;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static mediumtest.fixtures.ClientFileSystemFixtures.aClientFileSystemWith;
import static mediumtest.fixtures.ClientFileSystemFixtures.anEmptyClientFileSystem;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedIssueMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

  @BeforeAll
  static void prepare(@TempDir Path slHome) throws Exception {
    var storage = newStorage(SERVER_ID)
      .withPlugins(TestPlugin.JAVASCRIPT, TestPlugin.JAVA)
      .withProject("test-project")
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          .withActiveRule("java:S106", "MAJOR")
          .withActiveRule("java:S1220", "MINOR")
          .withActiveRule("java:S1481", "BLOCKER")))
      .create(slHome);

    var nodeJsHelper = new NodeJsHelper(logTester.getLogOutput());
    nodeJsHelper.detect(null);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA, Language.JS)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() {
    if (sonarlint != null) {
      sonarlint.stop();
      sonarlint = null;
    }
  }

  private SonarLintTestRpcServer backend;

  @BeforeEach
  void prepareBackend() {
    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", mockWebServerExtension.url("/"))
      .build(fakeClient);
  }

  @AfterEach
  void stopBackend() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void testContainerInfo() {
    assertThat(sonarlint.getPluginDetails()).extracting("key").containsOnly("java", "javascript");
  }

  @Test
  void simpleJavaBound(@TempDir Path baseDir) throws Exception {
    var inputFile = prepareJavaInputFile(baseDir);
    mockWebServerExtension.addProtobufResponse("/api/rules/show.protobuf?key=java:S1481",
      Rules.ShowResponse.newBuilder().setRule(Rules.Rule.newBuilder().setLang(Language.JAVA.getLanguageKey()).setSeverity("INFO").setType(RuleType.BUG)).build());

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey(JAVA_MODULE_KEY)
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S106", 4, inputFile.getPath(), IssueSeverity.MAJOR),
      tuple("java:S1220", null, inputFile.getPath(), IssueSeverity.MINOR),
      tuple("java:S1481", 3, inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  @Test
  void emptyQPJava(@TempDir Path baseDir) throws IOException {
    var inputFile = prepareJavaInputFile(baseDir);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey("test-project")
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  void declare_module_should_create_a_module_container_with_loaded_extensions() throws Exception {
    sonarlint
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null)))).get();

    ModuleContainer moduleContainer = sonarlint.getAnalysisEngine().getModuleRegistry().getContainerFor("key");

    assertThat(moduleContainer).isNotNull();
    assertThat(moduleContainer.getComponentsByType(SonarLintModuleFileSystem.class)).isNotEmpty();
  }

  @Test
  void stop_module_should_stop_the_module_container() throws Exception {
    sonarlint
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null)))).get();
    ModuleContainer moduleContainer = sonarlint.getAnalysisEngine().getModuleRegistry().getContainerFor("key");

    sonarlint.stopModule("key").get();

    assertThat(moduleContainer.getSpringContext().isActive()).isFalse();
  }

  @Test
  void should_forward_module_file_event_to_listener() throws Exception {
    // should not be located in global container in real life but easier for testing
    var moduleFileListener = new FakeModuleFileListener();
    sonarlint.getAnalysisEngine().getGlobalAnalysisContainer().add(moduleFileListener);
    var clientInputFile = new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null);
    sonarlint.declareModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem())).get();

    sonarlint.fireModuleFileEvent("moduleKey", ClientModuleFileEvent.of(clientInputFile, ModuleFileEvent.Type.CREATED)).get();

    assertThat(moduleFileListener.events).hasSize(1);
  }

  @SonarLintSide(lifespan = "MODULE")
  static class FakeModuleFileListener implements ModuleFileListener {
    private final List<ModuleFileEvent> events = new ArrayList<>();

    @Override
    public void process(ModuleFileEvent event) {
      events.add(event);
    }
  }

  private ClientInputFile prepareJavaInputFile(Path baseDir) throws IOException {
    return prepareInputFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(Path baseDir, String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

  static class StoreIssueListener implements IssueListener {
    private final List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }
  }

}
