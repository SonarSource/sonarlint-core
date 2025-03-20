/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.mediumtests;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.AnalysisScheduler;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisSchedulerConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

class AnalysisSchedulerMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final Consumer<List<ClientInputFile>> NO_OP_ANALYSIS_STARTED_CONSUMER = inputFiles -> {
  };
  private static final Supplier<Boolean> ANALYSIS_READY_SUPPLIER = () -> true;
  private static final Consumer<Issue> NO_OP_ISSUE_LISTENER = issue -> {
  };

  private AnalysisScheduler analysisScheduler;
  private volatile boolean engineStopped = true;
  private final ProgressMonitor progressMonitor = new ProgressMonitor(null);

  @BeforeEach
  void prepare(@TempDir Path workDir) throws IOException {
    var enabledLanguages = Set.of(SonarLanguage.PYTHON);
    var analysisGlobalConfig = AnalysisSchedulerConfiguration.builder()
      .setClientPid(1234L)
      .setWorkDir(workDir)
      .build();
    var result = new PluginsLoader().load(new PluginsLoader.Configuration(Set.of(findPythonJarPath()), enabledLanguages, false, Optional.empty()), Set.of());
    this.analysisScheduler = new AnalysisScheduler(analysisGlobalConfig, result.getLoadedPlugins(), logTester.getLogOutput());
    engineStopped = false;
  }

  @AfterEach
  void cleanUp() {
    if (!engineStopped) {
      this.analysisScheduler.stop();
    }
  }

  @Test
  void should_analyze_a_single_file_outside_of_any_module(@TempDir Path baseDir) throws Exception {
    var content = """
      def foo():
        x = 9; # trailing comment
      """;
    var inputFile = preparePythonInputFile(baseDir, content);

    var analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    List<Issue> issues = new ArrayList<>();
    var command = new AnalyzeCommand(null, () -> analysisConfig, issues::add, null, progressMonitor, NO_OP_ANALYSIS_STARTED_CONSUMER, ANALYSIS_READY_SUPPLIER);
    analysisScheduler.post(command);
    command.getResult().get();
    assertThat(issues).hasSize(1);
    assertThat(issues)
      .extracting("ruleKey", "message", "inputFile", "flows", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("python:S139", "Move this trailing comment on the previous empty line.", inputFile, List.of(), 2, 9, 2, 27));
    assertThat(issues.get(0).quickFixes()).hasSize(1);
  }

  @Test
  void should_analyze_a_file_inside_a_module(@TempDir Path baseDir) throws Exception {
    var content = """
      def foo():
        x = 9; # trailing comment
      """;
    ClientInputFile inputFile = preparePythonInputFile(baseDir, content);

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    List<Issue> issues = new ArrayList<>();
    analysisScheduler.post(new RegisterModuleCommand(new ClientModuleInfo("moduleKey", aModuleFileSystem())));
    var analyzeCommand = new AnalyzeCommand("moduleKey", () -> analysisConfig, issues::add, null, progressMonitor, NO_OP_ANALYSIS_STARTED_CONSUMER, ANALYSIS_READY_SUPPLIER);
    analysisScheduler.post(analyzeCommand);
    analyzeCommand.getResult().get();
    assertThat(issues).hasSize(1);
    assertThat(issues)
      .extracting("ruleKey", "message", "inputFile", "flows", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("python:S139", "Move this trailing comment on the previous empty line.", inputFile, List.of(), 2, 9, 2, 27));
    assertThat(issues.get(0).quickFixes()).hasSize(1);
  }

  @Test
  void should_fail_the_future_if_the_analyze_command_execution_fails() {
    var command = new AnalyzeCommand("moduleKey", () -> {
      throw new RuntimeException("Kaboom");
    }, issue -> {
    }, null, progressMonitor, NO_OP_ANALYSIS_STARTED_CONSUMER, ANALYSIS_READY_SUPPLIER);
    analysisScheduler.post(command);

    assertThat(command.getResult()).failsWithin(300, TimeUnit.MILLISECONDS)
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(RuntimeException.class)
      .withMessage("Kaboom");
  }

  @Test
  void should_cancel_progress_monitor_of_executing_analyze_command_when_stopping(@TempDir Path baseDir) throws IOException {
    var content = """
      def foo():
        x = 9; # trailing comment
      """;
    ClientInputFile inputFile = preparePythonInputFile(baseDir, content);

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    var analyzeCommand = new AnalyzeCommand("moduleKey", () -> analysisConfig, NO_OP_ISSUE_LISTENER, null, progressMonitor, inputFiles -> pause(300), ANALYSIS_READY_SUPPLIER);
    analysisScheduler.post(analyzeCommand);

    analysisScheduler.stop();
    engineStopped = true;

    await().until(analyzeCommand.getResult()::isDone);
    assertThat(analyzeCommand.getResult())
      .isCancelled();
    assertThat(progressMonitor.isCanceled()).isTrue();
  }

  @Test
  void should_cancel_pending_commands_when_stopping(@TempDir Path baseDir) throws IOException {
    var content = """
      def foo():
        x = 9; # trailing comment
      """;
    ClientInputFile inputFile = preparePythonInputFile(baseDir, content);

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    var analyzeCommand = new AnalyzeCommand("moduleKey", () -> analysisConfig, NO_OP_ISSUE_LISTENER, null, progressMonitor, inputFiles -> pause(300), ANALYSIS_READY_SUPPLIER);
    var secondAnalyzeCommand = new AnalyzeCommand("moduleKey", () -> analysisConfig, NO_OP_ISSUE_LISTENER, null, progressMonitor, NO_OP_ANALYSIS_STARTED_CONSUMER,
      ANALYSIS_READY_SUPPLIER);
    analysisScheduler.post(analyzeCommand);
    analysisScheduler.post(secondAnalyzeCommand);

    analysisScheduler.stop();
    engineStopped = true;

    await().until(analyzeCommand.getResult()::isDone);
    assertThat(analyzeCommand.getResult())
      .isCancelled();
    assertThat(secondAnalyzeCommand.getResult())
      .isCancelled();
    assertThat(progressMonitor.isCanceled()).isTrue();
  }

  @Test
  void should_interrupt_executing_thread_when_stopping(@TempDir Path baseDir) throws IOException {
    var content = """
      def foo():
        x = 9; # trailing comment
      """;
    ClientInputFile inputFile = preparePythonInputFile(baseDir, content);

    AnalysisConfiguration analysisConfig = AnalysisConfiguration.builder()
      .addInputFiles(inputFile)
      .addActiveRules(trailingCommentRule())
      .setBaseDir(baseDir)
      .build();
    var threadTermination = new AtomicReference<String>();
    var analyzeCommand = new AnalyzeCommand("moduleKey", () -> analysisConfig, NO_OP_ISSUE_LISTENER, null, progressMonitor, inputFiles -> {
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        threadTermination.set("INTERRUPTED");
        return;
      }
      threadTermination.set("FINISHED");
    }, ANALYSIS_READY_SUPPLIER);
    analysisScheduler.post(analyzeCommand);
    // let the engine run the first command
    pause(200);

    analysisScheduler.stop();
    engineStopped = true;

    await().until(analyzeCommand.getResult()::isDone);
    assertThat(threadTermination).hasValue("INTERRUPTED");
  }

  @Test
  void should_not_log_any_error_when_stopping() {
    // let the engine block waiting for the first command
    pause(500);

    analysisScheduler.stop();

    // let the engine stop properly
    pause(1000);
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  private ClientInputFile preparePythonInputFile(Path baseDir, String content) throws IOException {
    final var file = new File(baseDir.toFile(), "file.py");
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return new OnDiskTestClientInputFile(file.toPath(), "file.py", false, StandardCharsets.UTF_8, SonarLanguage.PYTHON);
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

  private static void pause(long period) {
    try {
      Thread.sleep(period);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
