/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class StandaloneNoPluginMediumTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private StandaloneSonarLintEngine sonarlint;
  private File baseDir;
  private Multimap<LogOutput.Level, String> logs = LinkedListMultimap.create();

  @Before
  public void prepare() throws IOException {
    sonarlint = new StandaloneSonarLintEngineImpl(StandaloneGlobalConfiguration.builder()
      .setLogOutput(new LogOutput() {
        @Override
        public void log(String formattedMessage, Level level) {
          logs.put(level, formattedMessage);
        }
      }).build());
    baseDir = temp.newFolder();
  }

  @After
  public void stop() {

    sonarlint.stop();
  }

  @Test
  public void dont_fail_if_no_plugin() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);

    AnalysisResults results = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {
        @Override
        public void handle(Issue issue) {
        }
      });

    assertThat(results.fileCount()).isEqualTo(1);
    assertThat(logs.get(Level.WARN)).contains("No analyzers installed");
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = new ClientInputFile() {

      @Override
      public Path getPath() {
        return file.toPath();
      }

      @Override
      public boolean isTest() {
        return isTest;
      }

      @Override
      public Charset getCharset() {
        return StandardCharsets.UTF_8;
      }

      @Override
      public <G> G getClientObject() {
        return null;
      }
    };
    return inputFile;
  }

}
