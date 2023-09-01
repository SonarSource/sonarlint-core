/*
 * SonarLint Core - Analysis Engine
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
package mediumtests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.sonarapi.SonarLintModuleFileSystem;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static testutils.ClientFileSystemFixtures.aClientFileSystemWith;
import static testutils.ClientFileSystemFixtures.anEmptyClientFileSystem;

class AnalysisEngineRealPluginMediumTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private AnalysisEngine analysisEngine;
  private volatile boolean engineStopped = true;
  private final ProgressMonitor progressMonitor = new ProgressMonitor(null);

  @BeforeEach
  void prepare(@TempDir Path workDir) throws IOException {
    var enabledLanguages = Set.of(Language.PYTHON);
    var analysisGlobalConfig = AnalysisEngineConfiguration.builder()
      .setClientPid(1234L)
      .setWorkDir(workDir)
      .build();
    var result = new PluginsLoader().load(new PluginsLoader.Configuration(Set.of(findPythonJarPath()), enabledLanguages, Optional.empty()));
    this.analysisEngine = new AnalysisEngine(analysisGlobalConfig, result.getLoadedPlugins(), logTester.getLogOutput());
    engineStopped = false;
  }

  @AfterEach
  void cleanUp() {
    if (!engineStopped) {
      this.analysisEngine.stop();
    }
  }

  @Test
  void should_analyze_a_single_file_outside_of_any_module(@TempDir Path baseDir) throws Exception {
    var content = "def foo():\n"
      + "  x = 9; # trailing comment\n";
    var inputFile = preparePythonInputFile(baseDir, content);

    var analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    List<Issue> issues = new ArrayList<>();
    analysisEngine.analyze(null, analysisConfig, issues::add, null, progressMonitor).get();
    assertThat(issues)
      .extracting("ruleKey", "message", "inputFile", "flows", "quickFixes", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("python:S139", "Move this trailing comment on the previous empty line.", inputFile, List.of(), List.of(), 2, 9, 2, 27));
  }

  @Test
  void should_analyze_a_file_inside_a_module(@TempDir Path baseDir) throws Exception {
    var content = "def foo():\n"
      + "  x = 9; # trailing comment\n";
    var inputFile = preparePythonInputFile(baseDir, content);

    var analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    List<Issue> issues = new ArrayList<>();
    analysisEngine.registerModule(new ClientModuleInfo("moduleKey", anEmptyClientFileSystem()));
    analysisEngine.analyze("moduleKey", analysisConfig, issues::add, null, progressMonitor).get();
    assertThat(issues)
      .extracting("ruleKey", "message", "inputFile", "flows", "quickFixes", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("python:S139", "Move this trailing comment on the previous empty line.", inputFile, List.of(), List.of(), 2, 9, 2, 27));
  }

  @Test
  void should_not_log_any_error_when_stopping() {
    // let the engine block waiting for the first command
    pause(500);

    analysisEngine.stop();

    // let the engine stop properly
    pause(1000);
    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).isEmpty();
  }

  @Test
  void declare_module_should_create_a_module_container_with_loaded_extensions() throws Exception {
    analysisEngine
      .registerModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null))));

    var moduleContainer = analysisEngine.getModuleRegistry().getContainerFor("key");

    assertThat(moduleContainer).isNotNull();
    assertThat(moduleContainer.getComponentsByType(SonarLintModuleFileSystem.class)).isNotEmpty();
  }

  @Test
  void stop_module_should_stop_the_module_container() throws Exception {
    analysisEngine
      .registerModule(new ClientModuleInfo("key", aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null))));
    var moduleContainer = analysisEngine.getModuleRegistry().getContainerFor("key");

    await().untilAsserted(() -> assertThat(moduleContainer.getSpringContext().isActive()).isTrue());

    analysisEngine.unregisterModule("key");

    await().untilAsserted(() -> assertThat(moduleContainer.getSpringContext().isActive()).isFalse());
  }

  private ClientInputFile preparePythonInputFile(Path baseDir, String content) throws IOException {
    final var file = new File(baseDir.toFile(), "file.py");
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return new OnDiskTestClientInputFile(file.toPath(), "file.py", false, StandardCharsets.UTF_8, Language.PYTHON);
  }

  private static Path findPythonJarPath() throws IOException {
    var pluginsFolderPath = Paths.get("target/plugins/");
    try (var files = Files.list(pluginsFolderPath)) {
      return files.filter(x -> x.getFileName().toString().endsWith(".jar"))
        .filter(x -> x.getFileName().toString().contains("python"))
        .findFirst().orElseThrow(() -> new RuntimeException("Unable to locate the python plugin"));
    }
  }

  private static ActiveRule trailingCommentRule() {
    var pythonActiveRule = new ActiveRule("python:S139", "py");
    pythonActiveRule.setParams(Map.of("legalTrailingCommentPattern", "^#\\s*+[^\\s]++$"));
    return pythonActiveRule;
  }

  private static void pause(long period) {
    try {
      Thread.sleep(period);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
