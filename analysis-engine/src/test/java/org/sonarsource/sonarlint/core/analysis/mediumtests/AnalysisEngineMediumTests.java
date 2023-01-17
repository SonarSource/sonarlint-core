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
package org.sonarsource.sonarlint.core.analysis.mediumtests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class AnalysisEngineMediumTests {
  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private AnalysisEngine analysisEngine;
  private volatile boolean engineStopped = true;
  private final ProgressMonitor progressMonitor = new ProgressMonitor(null);

  @BeforeEach
  void prepare(@TempDir Path workDir) throws IOException {
    var enabledLanguages = Set.of(Language.PYTHON);
    var analysisGlobalConfig = AnalysisEngineConfiguration.builder()
      .addEnabledLanguages(enabledLanguages)
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
    analysisEngine.post(new AnalyzeCommand(null, analysisConfig, issues::add, null), progressMonitor).get();
    assertThat(issues)
      .extracting("ruleKey", "message", "inputFile", "flows", "quickFixes", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("python:S139", "Move this trailing comment on the previous empty line.", inputFile, List.of(), List.of(), 2, 9, 2, 27));
  }

  @Test
  void should_analyze_a_file_inside_a_module(@TempDir Path baseDir) throws Exception {
    var content = "def foo():\n"
      + "  x = 9; # trailing comment\n";
    ClientInputFile inputFile = preparePythonInputFile(baseDir, content);

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    List<Issue> issues = new ArrayList<>();
    analysisEngine.post(new RegisterModuleCommand(new ClientModuleInfo("moduleKey", aModuleFileSystem())), progressMonitor).get();
    analysisEngine.post(new AnalyzeCommand("moduleKey", analysisConfig, issues::add, null), progressMonitor).get();
    assertThat(issues)
      .extracting("ruleKey", "message", "inputFile", "flows", "quickFixes", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("python:S139", "Move this trailing comment on the previous empty line.", inputFile, List.of(), List.of(), 2, 9, 2, 27));
  }

  @Test
  void should_fail_the_future_if_the_command_execution_fails() {
    var futureResult = analysisEngine.post((moduleRegistry, progress) -> {
      throw new RuntimeException("Kaboom");
    }, progressMonitor);

    await().until(futureResult::isCompletedExceptionally);
    futureResult.exceptionally(e -> assertThat(e)
      .isInstanceOf(RuntimeException.class)
      .hasMessage("Kaboom"));
  }

  @Test
  void should_execute_pending_commands_when_gracefully_finishing() {
    var futureWaitCommand1 = analysisEngine.post(waitCommand(1000L), progressMonitor);
    var futureWaitCommand2 = analysisEngine.post(waitCommand(1000L), progressMonitor);
    var futureWaitCommand3 = analysisEngine.post(waitCommand(1000L), progressMonitor);

    analysisEngine.finishGracefully();
    engineStopped = true;

    await().until(futureWaitCommand3::isDone);
    assertThat(futureWaitCommand3).isCompletedWithValue("SUCCESS");
    assertThat(futureWaitCommand1).isCompleted();
    assertThat(futureWaitCommand2).isCompleted();
  }

  @Test
  void should_cancel_progress_monitor_of_executing_command_when_stopping() {
    var futureLongCommand = analysisEngine.post((moduleRegistry, progressMonitor) -> {
      await().atMost(Duration.ofSeconds(5)).until(progressMonitor::isCanceled);
      return "CANCELED";
    }, progressMonitor);
    // let the engine run the command
    pause(500);

    analysisEngine.stop();
    engineStopped = true;

    await().until(futureLongCommand::isDone);
    assertThat(futureLongCommand).isCompletedWithValue("CANCELED");
  }

  @Test
  void should_cancel_pending_commands_when_stopping() {
    var futureLongCommand = analysisEngine.post((moduleRegistry, progressMonitor) -> {
      while (!engineStopped) {
        ;
      }
      return null;
    }, progressMonitor);
    var futureRegister = analysisEngine.post(new RegisterModuleCommand(new ClientModuleInfo("moduleKey", aModuleFileSystem())), progressMonitor);
    // let the engine run the first command
    pause(500);

    analysisEngine.stop();
    engineStopped = true;

    await().until(futureLongCommand::isDone);
    assertThat(futureRegister).isCancelled();
  }

  @Test
  void should_interrupt_executing_thread_when_stopping() {
    var futureLongCommand = analysisEngine.post((moduleRegistry, progressMonitor) -> {
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        return "INTERRUPTED";
      }
      return "FINISHED";
    }, progressMonitor);
    // let the engine run the first command
    pause(500);

    analysisEngine.stop();
    engineStopped = true;

    await().until(futureLongCommand::isDone);
    assertThat(futureLongCommand).isCompletedWithValue("INTERRUPTED");
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

  private ClientInputFile preparePythonInputFile(Path baseDir, String content) throws IOException {
    final var file = new File(baseDir.toFile(), "file.py");
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return new OnDiskTestClientInputFile(file.toPath(), "file.py", false, StandardCharsets.UTF_8, Language.PYTHON);
  }

  private static Path findPythonJarPath() throws IOException {
    var pluginsFolderPath = Paths.get("target/plugins/");
    return Files.list(pluginsFolderPath)
      .filter(x -> x.getFileName().toString().endsWith(".jar"))
      .filter(x -> x.getFileName().toString().contains("python"))
      .findFirst().orElseThrow(() -> new RuntimeException("Unable to locate the python plugin"));
  }

  private static ActiveRule trailingCommentRule() {
    var pythonActiveRule = new ActiveRule("python:S139", "py");
    pythonActiveRule.setParams(Map.of("legalTrailingCommentPattern", "^#\\s*+[^\\s]++$"));
    return pythonActiveRule;
  }

  private static ClientModuleFileSystem aModuleFileSystem() {
    return new ClientModuleFileSystem() {
      @Override
      public Stream<ClientInputFile> files(String suffix, InputFile.Type type) {
        return Stream.of();
      }

      @Override
      public Stream<ClientInputFile> files() {
        return Stream.of();
      }
    };
  }

  private static Command<String> waitCommand(long period) {
    return (moduleRegistry, progressMonitor) -> {
      pause(period);
      return "SUCCESS";
    };
  }

  private static void pause(long period) {
    try {
      Thread.sleep(period);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
