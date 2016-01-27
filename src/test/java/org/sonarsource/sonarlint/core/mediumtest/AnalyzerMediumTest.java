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
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.IssueListener;
import org.sonarsource.sonarlint.core.SonarLintClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AnalyzerMediumTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testJavaScript() throws Exception {
    SonarLintClient batch = SonarLintClient.builder().addPlugin(this.getClass().getResource("/sonar-javascript-plugin-2.8.jar")).build();

    batch.start();

    File baseDir = temp.newFolder();
    final File file = new File(baseDir, "foo.js");
    FileUtils.write(file, "function foo() {var x;}");
    AnalysisConfiguration.InputFile inputFile = new AnalysisConfiguration.InputFile() {

      @Override
      public Path path() {
        return file.toPath();
      }

      @Override
      public boolean isTest() {
        return false;
      }

      @Override
      public Charset charset() {
        return StandardCharsets.UTF_8;
      }
    };

    batch.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {

      @Override
      public void handle(Issue issue) {
        System.out.println(issue);
      }

    });

    batch.stop();
  }

  @Test
  public void testJava() throws Exception {

    SonarLintClient batch = SonarLintClient.builder().addPlugin(this.getClass().getResource("/sonar-java-plugin-3.9.jar")).build();

    batch.start();

    File baseDir = temp.newFolder();
    final File file = new File(baseDir, "Foo.java");
    FileUtils.write(file, "public class Foo {\n"
      + "  public void foo() {\n"
      + "    int x;\n"
      + "    System.out.println(\"Foo\");\n"
      + "    System.out.println(\"Foo\"); //NOSONAR\n"
      + "  }\n"
      + "}");
    AnalysisConfiguration.InputFile inputFile = new AnalysisConfiguration.InputFile() {

      @Override
      public Path path() {
        return file.toPath();
      }

      @Override
      public boolean isTest() {
        return false;
      }

      @Override
      public Charset charset() {
        return StandardCharsets.UTF_8;
      }
    };

    final List<IssueListener.Issue> issues = new ArrayList<>();
    batch.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {

      @Override
      public void handle(Issue issue) {
        System.out.println(issue);
        issues.add(issue);
      }

    });

    assertThat(issues).extracting("ruleKey", "startLine", "filePath").containsOnly(
      tuple("squid:S106", 4, file.toPath()),
      tuple("squid:S1220", null, file.toPath()),
      tuple("squid:S1481", 3, file.toPath()));
    batch.stop();
  }
}
