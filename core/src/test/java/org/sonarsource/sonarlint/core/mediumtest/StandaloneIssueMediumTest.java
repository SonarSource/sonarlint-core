/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class StandaloneIssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static StandaloneSonarLintEngineImpl sonarlint;
  private File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path sonarlintUserHome = temp.newFolder().toPath();
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addPlugin(PluginLocator.getJavaPluginUrl())
      .addPlugin(PluginLocator.getPhpPluginUrl())
      .addPlugin(PluginLocator.getPythonPluginUrl())
      .addPlugin(PluginLocator.getXooPluginUrl())
      .setSonarLintUserHome(sonarlintUserHome)
      .setLogOutput((msg, level) -> System.out.println(msg))
      .build();
    sonarlint = new StandaloneSonarLintEngineImpl(config);
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
  }

  @Before
  public void prepareBasedir() throws Exception {
    baseDir = temp.newFolder();
  }

  @Test
  public void simpleJavaScript() throws Exception {

    RuleDetails ruleDetails = sonarlint.getRuleDetails("javascript:UnusedVariable");
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables and functions should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo("js");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MINOR");
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable or a local function is declared but not used");

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), i -> issues.add(i));
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("javascript:UnusedVariable", 2, inputFile.getPath()));

  }

  @Test
  public void fileEncoding() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false, StandardCharsets.UTF_16);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 1, 9, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 12, inputFile.getPath()));
  }

  @Test
  public void simpleXoo() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 1, 9, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()),
      tuple("xoo:HasTag", 2, 12, inputFile.getPath()));
  }

  @Test
  public void analysisErrors() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);
    prepareInputFile("foo.xoo.error", "1,2,error analysing\n2,3,error analysing", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));
    assertThat(results.failedAnalysisFiles()).containsExactly(inputFile);
    assertThat(issues).extracting("ruleKey", "startLine", "startLineOffset", "inputFile.path").containsOnly(
      tuple("xoo:HasTag", 2, 6, inputFile.getPath()));
  }

  @Test
  public void simplePhp() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("php:S1172", 2, inputFile.getPath()));
  }

  @Test
  public void simplePython() throws Exception {

    ClientInputFile inputFile = prepareInputFile("foo.py", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("python:PrintStatementUsage", 2, inputFile.getPath()));
  }

  @Test
  public void simpleJava() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaPomXml() throws Exception {
    ClientInputFile inputFile = prepareInputFile("pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S3421", 6, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void supportJavaSuppressWarning() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  @SuppressWarnings(\"squid:S106\")\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.of()), issue -> issues.add(issue));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MINOR"));
  }

  @Test
  public void simpleJavaWithBytecode() throws Exception {
    ClientInputFile inputFile = TestUtils.createInputFile(new File("src/test/projects/java-with-bytecode/src/Foo.java").getAbsoluteFile().toPath(), false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile),
      ImmutableMap.of("sonar.java.binaries", new File("src/test/projects/java-with-bytecode/bin").getAbsolutePath())),
      issue -> issues.add(issue));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 5, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      // FIXME bug in Java plugin 4.8.0.9441 tuple("squid:UnusedPrivateMethod", 8, inputFile.getPath()),
      tuple("squid:S1186", 8, inputFile.getPath()));
  }

  @Test
  public void testJavaSurefireDontCrashAnalysis() throws Exception {

    File surefireReport = new File(baseDir, "reports/TEST-FooTest.xml");
    FileUtils.write(surefireReport, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<testsuite name=\"FooTest\" time=\"0.121\" tests=\"1\" errors=\"0\" skipped=\"0\" failures=\"0\">\n" +
      "<testcase name=\"errorAnalysis\" classname=\"FooTest\" time=\"0.031\"/>\n" +
      "</testsuite>");

    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    ClientInputFile inputFileTest = prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void testFoo() {\n"
        + "  }\n"
        + "}",
      true);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile, inputFileTest),
        ImmutableMap.of("sonar.junit.reportsPath", "reports/")),
      issue -> issues.add(issue));

    assertThat(results.fileCount()).isEqualTo(2);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      tuple("squid:S1481", 3, inputFile.getPath()),
      tuple("squid:S2187", 1, inputFileTest.getPath()));
  }

  @Test
  public void concurrentAnalysis() throws Throwable {
    final ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final Path workDir = temp.newFolder().toPath();

    int parallelExecutions = 4;

    ExecutorService executor = Executors.newFixedThreadPool(parallelExecutions);

    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < parallelExecutions; i++) {

      Runnable worker = new Runnable() {
        @Override
        public void run() {
          sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), workDir, Arrays.asList(inputFile), ImmutableMap.of()), issue -> {
          });
        }
      };
      results.add(executor.submit(worker));
    }
    executor.shutdown();

    while (!executor.isTerminated()) {
    }

    for (Future<?> future : results) {
      try {
        future.get();
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }

  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    ClientInputFile inputFile = TestUtils.createInputFile(file.toPath(), isTest, encoding);
    return inputFile;
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8);
  }

}
