/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.mediumtest;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpIssueListener;

public class LogMediumTest {

  private final class MyCustomException extends RuntimeException {
    private MyCustomException(String message) {
      super(message);
    }
  }

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private StandaloneSonarLintEngine sonarlint;
  private File baseDir;
  private Multimap<LogOutput.Level, String> logs;
  private StandaloneGlobalConfiguration config;

  @Before
  public void prepare() throws IOException {
    logs = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());
    config = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .setLogOutput(createLogOutput(logs))
      .build();
    sonarlint = new StandaloneSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @After
  public void stop() {
    sonarlint.stop();
  }

  private LogOutput createLogOutput(final Multimap<LogOutput.Level, String> logs) {
    return new LogOutput() {
      @Override
      public void log(String formattedMessage, Level level) {
        logs.put(level, formattedMessage);
      }
    };
  }

  private StandaloneAnalysisConfiguration createConfig(ClientInputFile inputFile) throws IOException {
    return StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .build();
  }

  /**
   * If this test starts to fail randomly, check if any other test class in the core module is using {@link org.sonar.api.utils.log.LogTester} without
   * setting the root level back to debug in @AfterClass! 
   */
  @Test
  public void changeLogOutputForAnalysis() throws Exception {
    logs.clear();
    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);
    sonarlint.analyze(createConfig(inputFile), createNoOpIssueListener(), null, null);
    assertThat(logs.get(LogOutput.Level.DEBUG)).isNotEmpty();
    logs.clear();

    final Multimap<LogOutput.Level, String> logs2 = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());

    sonarlint.analyze(createConfig(inputFile), createNoOpIssueListener(), createLogOutput(logs2), null);
    assertThat(logs.get(LogOutput.Level.DEBUG)).isEmpty();
    assertThat(logs2.get(LogOutput.Level.DEBUG)).isNotEmpty();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    return TestUtils.createInputFile(file.toPath(), relativePath, false);
  }
}
