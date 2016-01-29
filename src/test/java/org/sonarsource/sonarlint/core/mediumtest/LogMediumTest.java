/*
 * SonarLint Core Library
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
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.IssueListener;
import org.sonarsource.sonarlint.core.LogOutput;
import org.sonarsource.sonarlint.core.LogOutput.Level;
import org.sonarsource.sonarlint.core.SonarLintClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class LogMediumTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private SonarLintClient sonarlint;
  private File baseDir;
  private Multimap<Level, String> logs = LinkedListMultimap.create();

  @Before
  public void prepare() throws IOException {
    sonarlint = SonarLintClient.builder()
      .addPlugin(this.getClass().getResource("/sonar-javascript-plugin-2.8.jar"))
      .setVerbose(false)
      .setLogOutput(new LogOutput() {

        @Override
        public void log(String formattedMessage, Level level) {
          logs.put(level, formattedMessage);
        }
      })
      .build();
    sonarlint.start();

    baseDir = temp.newFolder();
  }

  @After
  public void stop() {
    sonarlint.stop();
  }

  @Test
  public void changeLogVerbosity() throws Exception {

    AnalysisConfiguration.InputFile inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);

    sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {
      @Override
      public void handle(Issue issue) {
      }
    });

    assertThat(logs.get(Level.DEBUG)).isEmpty();

    logs.clear();

    sonarlint.setVerbose(true);

    sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {
      @Override
      public void handle(Issue issue) {
      }
    });

    assertThat(logs.get(Level.DEBUG)).isNotEmpty();
  }

  @Test
  public void alreadyStartedStopped() {
    try {
      sonarlint.start();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isExactlyInstanceOf(IllegalStateException.class).hasMessage("SonarLint Engine is already started");
    }

    sonarlint.stop();

    try {
      sonarlint.stop();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isExactlyInstanceOf(IllegalStateException.class).hasMessage("SonarLint Engine is not started");
    }

    sonarlint.start();
  }

  @Test
  public void handleException() throws Exception {

    AnalysisConfiguration.InputFile inputFile = prepareInputFile("foo.js", "function foo() {var x;}", false);

    try {
      sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {
        @Override
        public void handle(Issue issue) {
          throw new IllegalStateException("Fake");
        }
      });
    } catch (Exception e) {
      assertThat(e).hasCause(new IllegalStateException("Fake"));
    }

  }

  private AnalysisConfiguration.InputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    AnalysisConfiguration.InputFile inputFile = new AnalysisConfiguration.InputFile() {

      @Override
      public Path path() {
        return file.toPath();
      }

      @Override
      public boolean isTest() {
        return isTest;
      }

      @Override
      public Charset charset() {
        return StandardCharsets.UTF_8;
      }
    };
    return inputFile;
  }

}
