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
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.AnalysisResults;
import org.sonarsource.sonarlint.core.IssueListener;
import org.sonarsource.sonarlint.core.SonarLintClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class IssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static SonarLintClient sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws IOException {
    sonarlint = SonarLintClient.builder()
      .addPlugin(IssueMediumTest.class.getResource("/sonar-javascript-plugin-2.8.jar"))
      .addPlugin(IssueMediumTest.class.getResource("/sonar-java-plugin-3.9.jar"))
      .addPlugin(IssueMediumTest.class.getResource("/sonar-php-plugin-2.7.jar"))
      .setSonarLintUserHome(temp.newFolder().toPath())
      .setVerbose(true)
      .build();
    sonarlint.start();

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
  }

  @Test
  public void simpleJavaScript() throws Exception {

    assertThat(sonarlint.getHtmlRuleDescription("javascript:UnusedVariable")).contains("<p>", "If a local variable is declared but not used");

    AnalysisConfiguration.InputFile inputFile = prepareInputFile("foo.js", "function foo() {\n"
      + "  var x;\n"
      + "}", false);

    final List<IssueListener.Issue> issues = new ArrayList<>();
    sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {
      @Override
      public void handle(Issue issue) {
        issues.add(issue);
      }
    });
    assertThat(issues).extracting("ruleKey", "startLine", "filePath").containsOnly(
      tuple("javascript:UnusedVariable", 2, inputFile.path()));

  }

  @Test
  public void simplePhp() throws Exception {

    AnalysisConfiguration.InputFile inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<IssueListener.Issue> issues = new ArrayList<>();
    sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {
      @Override
      public void handle(Issue issue) {
        issues.add(issue);
      }
    });
    assertThat(issues).extracting("ruleKey", "startLine", "filePath").containsOnly(
      tuple("php:S1172", 2, inputFile.path()));

  }

  @Test
  public void simpleJava() throws Exception {
    AnalysisConfiguration.InputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<IssueListener.Issue> issues = new ArrayList<>();
    sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()), new IssueListener() {

      @Override
      public void handle(Issue issue) {
        issues.add(issue);
      }
    });

    assertThat(issues).extracting("ruleKey", "startLine", "filePath").containsOnly(
      tuple("squid:S106", 4, inputFile.path()),
      tuple("squid:S1220", null, inputFile.path()),
      tuple("squid:S1481", 3, inputFile.path()));
  }

  @Test
  public void simpleJavaWithBytecode() throws Exception {
    AnalysisConfiguration.InputFile inputFile = createInputFile(new File("src/test/projects/java-with-bytecode/src/Foo.java").getAbsoluteFile().toPath(), false);

    final List<IssueListener.Issue> issues = new ArrayList<>();
    sonarlint.analyze(new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile),
      ImmutableMap.<String, String>of("sonar.java.binaries", new File("src/test/projects/java-with-bytecode/bin").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).extracting("ruleKey", "startLine", "filePath").containsOnly(
      tuple("squid:S106", 5, inputFile.path()),
      tuple("squid:S1220", null, inputFile.path()),
      tuple("squid:UnusedPrivateMethod", 8, inputFile.path()),
      tuple("squid:S1186", 8, inputFile.path()));
  }

  @Test
  public void testJavaSurefireDontCrashAnalysis() throws Exception {

    File surefireReport = new File(baseDir, "reports/TEST-FooTest.xml");
    FileUtils.write(surefireReport, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<testsuite name=\"FooTest\" time=\"0.121\" tests=\"1\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
      "<testcase name=\"errorAnalysis\" classname=\"FooTest\" time=\"0.031\"/>\n" +
      "</testsuite>");

    AnalysisConfiguration.InputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    AnalysisConfiguration.InputFile inputFileTest = prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void testFoo() {\n"
        + "  }\n"
        + "}",
      true);

    final List<IssueListener.Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      new AnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile, inputFileTest),
        ImmutableMap.<String, String>of("sonar.junit.reportsPath", "reports/")),
      new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(results.fileCount()).isEqualTo(2);

    assertThat(issues).extracting("ruleKey", "startLine", "filePath").containsOnly(
      tuple("squid:S106", 4, inputFile.path()),
      tuple("squid:S1220", null, inputFile.path()),
      tuple("squid:S1481", 3, inputFile.path()),
      tuple("squid:S2187", 1, inputFileTest.path()));
  }

  private AnalysisConfiguration.InputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    AnalysisConfiguration.InputFile inputFile = createInputFile(file.toPath(), isTest);
    return inputFile;
  }

  private AnalysisConfiguration.InputFile createInputFile(final Path path, final boolean isTest) {
    AnalysisConfiguration.InputFile inputFile = new AnalysisConfiguration.InputFile() {

      @Override
      public Path path() {
        return path;
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
