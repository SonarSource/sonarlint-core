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
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class StandaloneIssueMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static StandaloneSonarLintEngineImpl sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws IOException {
    StandaloneGlobalConfiguration config = StandaloneGlobalConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addPlugin(PluginLocator.getJavaPluginUrl())
      .addPlugin(PluginLocator.getPhpPluginUrl())
      .setSonarLintUserHome(temp.newFolder().toPath())
      .setLogOutput(new LogOutput() {
        @Override
        public void log(String formattedMessage, Level level) {
          // Don't pollute logs
        }
      })
      .build();
    sonarlint = new StandaloneSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    sonarlint.stop();
  }

  @Test
  public void simpleJavaScript() throws Exception {

    RuleDetails ruleDetails = sonarlint.getRuleDetails("javascript:UnusedVariable");
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo("js");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MAJOR");
    assertThat(ruleDetails.getTags()).containsOnly("unused");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable is declared but not used");

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {
        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("javascript:UnusedVariable", 2, inputFile.getPath()));

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
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {
        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("php:S1172", 2, inputFile.getPath()));

  }

  @Test
  public void simpleJava() throws Exception {
    ClientInputFile inputFile = prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MAJOR"));
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
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 4, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simpleJavaWithBytecode() throws Exception {
    ClientInputFile inputFile = createInputFile(new File("src/test/projects/java-with-bytecode/src/Foo.java").getAbsoluteFile().toPath(), false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile),
      ImmutableMap.<String, String>of("sonar.java.binaries", new File("src/test/projects/java-with-bytecode/bin").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple("squid:S106", 5, inputFile.getPath()),
      tuple("squid:S1220", null, inputFile.getPath()),
      tuple("squid:UnusedPrivateMethod", 8, inputFile.getPath()),
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
        ImmutableMap.<String, String>of("sonar.junit.reportsPath", "reports/")),
      new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

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
          sonarlint.analyze(new StandaloneAnalysisConfiguration(baseDir.toPath(), workDir, Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
            new IssueListener() {
              @Override
              public void handle(Issue issue) {
                // Ignore
              }
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

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = createInputFile(file.toPath(), isTest);
    return inputFile;
  }

  private ClientInputFile createInputFile(final Path path, final boolean isTest) {
    ClientInputFile inputFile = new ClientInputFile() {

      @Override
      public Path getPath() {
        return path;
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
