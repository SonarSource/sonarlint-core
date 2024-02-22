/*
 * SonarLint Core - Medium Tests
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintModuleFileSystem;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.PluginDetails;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.nodejs.NodeJsHelper;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileListener;
import testutils.OnDiskTestClientInputFile;
import testutils.TestUtils;

import static mediumtest.fixtures.ClientFileSystemFixtures.aClientFileSystemWith;
import static mediumtest.fixtures.ClientFileSystemFixtures.anEmptyClientFileSystem;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedIssueMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String EMPTY_PROJECT_KEY = "test-project";
  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static SonarLintAnalysisEngine engine;
  private static SonarLintTestRpcServer backend;

  @BeforeAll
  static void prepare(@TempDir Path slHome) {
    var nodeJsHelper = new NodeJsHelper();
    var detectedNodeJs = nodeJsHelper.detect(null);

    var config = EngineConfiguration.builder()
      .setSonarLintUserHome(slHome)
      .setLogOutput(createNoOpLogOutput())
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, storage -> storage.withPlugins(TestPlugin.JAVASCRIPT, TestPlugin.JAVA)
        .withProject(EMPTY_PROJECT_KEY)
        .withProject(JAVA_MODULE_KEY, project -> project
          .withRuleSet("java", ruleSet -> ruleSet
            .withActiveRule("java:S106", "MAJOR")
            .withActiveRule("java:S1220", "MINOR")
            .withActiveRule("java:S1481", "BLOCKER"))))
      .withBoundConfigScope(JAVA_MODULE_KEY, CONNECTION_ID, JAVA_MODULE_KEY)
      .withClientNodeJsPath(detectedNodeJs.getPath())
      .withEnabledLanguageInStandaloneMode(JAVA)
      .withEnabledLanguageInStandaloneMode(JS)
      .build();
    engine = new SonarLintAnalysisEngine(config, backend, CONNECTION_ID);
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    if (engine != null) {
      engine.stop();
      engine = null;
    }
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void testContainerInfo() {
    assertThat(engine.getPluginDetails()).extracting(PluginDetails::key).containsOnly("java", "javascript");
  }

  @Test
  void simpleJavaBound(@TempDir Path baseDir) throws Exception {
    var inputFile = prepareJavaInputFile(baseDir);

    final List<RawIssue> issues = new ArrayList<>();
    engine.analyze(AnalysisConfiguration.builder()
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      issues::add, null, null, JAVA_MODULE_KEY);

    assertThat(issues).extracting("ruleKey", "textRange", "inputFile.path", "severity")
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", new TextRangeDto(4, 4, 4, 14), inputFile.getPath(), IssueSeverity.MAJOR),
        tuple("java:S1220", null, inputFile.getPath(), IssueSeverity.MINOR),
        tuple("java:S1481", new TextRangeDto(3, 8, 3, 9), inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  @Test
  void emptyQPJava(@TempDir Path baseDir) throws IOException {
    var inputFile = prepareJavaInputFile(baseDir);

    final List<RawIssue> issues = new ArrayList<>();
    engine.analyze(AnalysisConfiguration.builder()
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      issues::add, null, null, EMPTY_PROJECT_KEY);

    assertThat(issues).isEmpty();
  }

  @Test
  void declare_module_should_create_a_module_container_with_loaded_extensions() throws Exception {
    engine
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null)))).get();

    ModuleContainer moduleContainer = engine.getAnalysisEngine().getModuleRegistry().getContainerFor("key");

    assertThat(moduleContainer).isNotNull();
    assertThat(moduleContainer.getComponentsByType(SonarLintModuleFileSystem.class)).isNotEmpty();
  }

  @Test
  void stop_module_should_stop_the_module_container() throws Exception {
    engine
      .declareModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null)))).get();
    ModuleContainer moduleContainer = engine.getAnalysisEngine().getModuleRegistry().getContainerFor("key");

    engine.stopModule("key").get();

    assertThat(moduleContainer.getSpringContext().isActive()).isFalse();
  }

  @Test
  void should_forward_module_file_event_to_listener() throws Exception {
    // should not be located in global container in real life but easier for testing
    var moduleFileListener = new FakeModuleFileListener();
    engine.getAnalysisEngine().getGlobalAnalysisContainer().add(moduleFileListener);
    var clientInputFile = new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null);
    engine.declareModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem())).get();

    engine.fireModuleFileEvent("moduleKey", ClientModuleFileEvent.of(clientInputFile, ModuleFileEvent.Type.CREATED)).get();

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


}
