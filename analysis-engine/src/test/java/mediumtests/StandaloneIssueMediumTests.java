/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.GlobalAnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.Language;
import org.sonarsource.sonarlint.core.analysis.api.RuleKey;
import org.sonarsource.sonarlint.core.analysis.container.ComponentContainer;
import org.sonarsource.sonarlint.core.analysis.container.module.SonarLintApiModuleFileSystemAdapter;
import testutils.NodeJsHelper;
import testutils.OnDiskTestClientInputFile;
import testutils.PluginLocator;
import testutils.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

class StandaloneIssueMediumTests {

  private static Path sonarlintUserHome;
  private static Path fakeTypeScriptProjectPath;

  private static final String A_JAVA_FILE_PATH = "Foo.java";
  private static AnalysisEngine sonarlint;
  private File baseDir;
  // commercial plugins might not be available
  // (if you pass -Dcommercial to maven, a profile will be activated that downloads the commercial plugins)
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  private static ActiveRule s1220 = createActiveRule("java:S1220", "MINOR");
  private static ActiveRule java_s1481 = createActiveRule("java:S1481", "MINOR");
  private static ActiveRule s106 = createActiveRule("java:S106", "MAJOR");
  private static ActiveRule s1135 = createActiveRule("java:S1135", "INFO");
  private static ActiveRule s1313 = createActiveRule("java:S1313", "INFO");
  private static ActiveRule s3421 = createActiveRule("java:S3421", "MINOR");
  private static ActiveRule s1144 = createActiveRule("java:S1144", "INFO");
  private static ActiveRule s1186 = createActiveRule("java:S1186", "INFO");
  private static ActiveRule s1228 = createActiveRule("java:S1228", "MINOR");
  private static ActiveRule s2094 = createActiveRule("java:S2094", "MINOR");
  private static ActiveRule hasTag = createActiveRule("xoo:HasTag", "INFO");
  private static ActiveRule js_s1481 = createActiveRule("javascript:S1481", "MINOR");
  private static ActiveRule s3827 = createActiveRule("javascript:S3827", "MINOR");
  private static ActiveRule s1764 = createActiveRule("typescript:S1764", "MINOR");
  private static ActiveRule cS3805 = createActiveRule("c:S3805", "MINOR");
  private static ActiveRule s1172 = createActiveRule("php:S1172", "MINOR");
  private static ActiveRule printStatementUsage = createActiveRule("python:PrintStatementUsage", "MINOR");

  static {
    hasTag.setParams(Map.of("tag", "xoo"));
    // The CFamilySensor relies on internal key
    cS3805.setInternalKey("S3805");
  }

  @BeforeAll
  static void prepare(@TempDir Path temp) throws Exception {
    sonarlintUserHome = temp.resolve("home");
    fakeTypeScriptProjectPath = temp.resolve("ts");

    Path packagejson = fakeTypeScriptProjectPath.resolve("package.json");
    FileUtils.write(packagejson.toFile(), "{"
      + "\"devDependencies\": {\n" +
      "    \"typescript\": \"2.6.1\"\n" +
      "  }"
      + "}", StandardCharsets.UTF_8);
    ProcessBuilder pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(fakeTypeScriptProjectPath.toFile())
      .inheritIO();
    Process process = pb.start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("sonar.typescript.internal.typescriptLocation", fakeTypeScriptProjectPath.resolve("node_modules").toString());
    // See test sonarjs_should_honor_global_and_analysis_level_properties
    extraProperties.put("sonar.javascript.globals", "GLOBAL1");

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    GlobalAnalysisConfiguration.Builder configBuilder = GlobalAnalysisConfiguration.builder()
      .addPlugin(PluginLocator.getJavaScriptPluginUrl())
      .addPlugin(PluginLocator.getJavaPluginUrl())
      .addPlugin(PluginLocator.getPhpPluginUrl())
      .addPlugin(PluginLocator.getPythonPluginUrl())
      .addPlugin(PluginLocator.getXooPluginUrl())
      .addEnabledLanguages(Language.JS, Language.JAVA, Language.PHP, Language.PYTHON, Language.TS, Language.C, Language.XOO)
      .setSonarLintUserHome(sonarlintUserHome)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setExtraProperties(extraProperties);

    if (COMMERCIAL_ENABLED) {
      configBuilder.addPlugin(PluginLocator.getCppPluginUrl());
    }
    sonarlint = new AnalysisEngine(configBuilder.build());
  }

  @AfterAll
  static void stop() throws IOException {
    if (sonarlint != null) {
      sonarlint.stop();
    }
  }

  @BeforeEach
  void prepareBasedir(@TempDir Path temp) throws Exception {
    baseDir = Files.createTempDirectory(temp, "baseDir").toFile();
  }

  @Test
  void simpleJavaScript() throws Exception {
    String content = "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}";
    ClientInputFile inputFile = prepareInputFile("foo.js", content, false);
    // SLCORE-160
    ClientInputFile inputFileInNodeModules = prepareInputFile("node_modules/foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile, inputFileInNodeModules)
        .addActiveRule(js_s1481)
        .build(),
      issues::add, null,
      null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("javascript:S1481", 2, "foo.js"));
  }

  @Test
  void sonarjs_should_honor_global_and_analysis_level_properties() throws Exception {

    String content = "function foo() {\n"
      + "  console.log(LOCAL1); // Noncompliant\n"
      + "  console.log(GLOBAL1); // GLOBAL1 defined as global varibale in global settings\n"
      + "}";
    ClientInputFile inputFile = prepareInputFile("foo.js", content, false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRule(s3827)
        .build(),
      issues::add, null,
      null);
    assertThat(issues.stream().filter(i -> i.getRuleKey().equals("javascript:S3827")))
      .extracting(Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
        tuple(2, "foo.js"));

    // Change globals using analysis property
    issues.clear();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .putExtraProperty("sonar.javascript.globals", "LOCAL1")
        .addActiveRule(s3827)
        .build(),
      issues::add, null,
      null);
    assertThat(issues.stream().filter(i -> i.getRuleKey().equals("javascript:S3827")))
      .extracting(Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
        tuple(3, "foo.js"));
  }

  @Test
  void simpleTypeScript() throws Exception {

    final File tsConfig = new File(baseDir, "tsconfig.json");
    FileUtils.write(tsConfig, "{}", StandardCharsets.UTF_8);

    ClientInputFile inputFile = prepareInputFile("foo.ts", "function foo() {\n"
      + "  if(bar() && bar()) { return 42; }\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRule(s1764)
      .build(), issues::add, null,
      null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("typescript:S1764", 2, "foo.ts"));

  }

  @Test
  void fileEncoding() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false, StandardCharsets.UTF_16, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRule(hasTag)
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("xoo:HasTag", 1, 9, "foo.xoo"),
      tuple("xoo:HasTag", 2, 6, "foo.xoo"),
      tuple("xoo:HasTag", 2, 12, "foo.xoo"));
  }

  @Test
  void simpleXoo() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function xoo() {\n"
      + "  var xoo1, xoo2;\n"
      + "  var xoo; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRule(hasTag)
        .build(),
      issues::add, null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("xoo:HasTag", 1, 9, "foo.xoo"),
      tuple("xoo:HasTag", 2, 6, "foo.xoo"),
      tuple("xoo:HasTag", 2, 12, "foo.xoo"));
  }

  @Test
  void simpleC() throws Exception {
    assumeTrue(COMMERCIAL_ENABLED);
    // prepareInputFile("foo.h", "", false, StandardCharsets.UTF_8, Language.C);
    // prepareInputFile("foo2.h", "", false, StandardCharsets.UTF_8, Language.C);
    ClientInputFile inputFile = prepareInputFile("foo.c", "#import \"foo.h\"\n"
      + "#import \"foo2.h\" //NOSONAR\n", false, StandardCharsets.UTF_8, Language.C);

    String buildWrapperContent = "{\"version\":0,\"captures\":[" +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __cplusplus 201703L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{\"compiler\":\"clang\",\"cwd\":\"" +
      baseDir.toString().replace("\\", "\\\\") +
      "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"foo.c\"]}]}";

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRule(cS3805)
        .putExtraProperty("sonar.cfamily.build-wrapper-content", buildWrapperContent)
        .build(),
      issues::add, (m, l) -> System.out.println(m), null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("c:S3805", 1, 0, "foo.c"),
      // FIXME no sonar is not supported by the CFamily analyzer
      tuple("c:S3805", 2, 0, "foo.c"));
  }

  @Test
  void analysisErrors() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);
    prepareInputFile("foo.xoo.error", "1,2,error analysing\n2,3,error analysing", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRule(hasTag)
        .build(),
      issues::add, null, null);
    assertThat(results.failedAnalysisFiles()).containsExactly(inputFile);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("xoo:HasTag", 2, 6, "foo.xoo"));
  }

  @Test
  void returnLanguagePerFile() throws IOException {
    ClientInputFile inputFile = prepareInputFile("foo.xoo", "function foo() {\n"
      + "  var xoo;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    AnalysisResults results = sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .build(),
      issues::add, null, null);
    assertThat(results.languagePerFile()).containsExactly(entry(inputFile, Language.XOO));
  }

  @Test
  void simplePhp() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.php", "<?php\n"
      + "function writeMsg($fname) {\n"
      + "    $i = 0; // NOSONAR\n"
      + "    echo \"Hello world!\";\n"
      + "}\n"
      + "?>", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRule(s1172)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("php:S1172", 2, "foo.php"));
  }

  @Test
  void simplePython() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo.py", "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRule(printStatementUsage)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("python:PrintStatementUsage", 2, "foo.py"));
  }

  // SLCORE-162
  @Test
  void useRelativePathToEvaluatePathPatterns() throws Exception {
    final File file = new File(baseDir, "foo.tmp"); // Temporary file doesn't have the correct file suffix
    FileUtils.write(file, "def my_function(name):\n"
      + "    print \"Hello\"\n"
      + "    print \"world!\" # NOSONAR\n"
      + "\n", StandardCharsets.UTF_8);
    ClientInputFile inputFile = new OnDiskTestClientInputFile(file.toPath(), "foo.py", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRule(printStatementUsage)
      .build(), issues::add,
      null, null);
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("python:PrintStatementUsage", 2, "foo.py"));
  }

  @Test
  void simpleJava() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // TODO full line issue\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRules(s106, s1135, s1220, java_s1481)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, Issue::getEndLine, Issue::getEndLineOffset,
      i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
        tuple("java:S1220", null, null, null, null, A_JAVA_FILE_PATH, "MINOR"),
        tuple("java:S1481", 3, 8, 3, 9, A_JAVA_FILE_PATH, "MINOR"),
        tuple("java:S106", 4, 4, 4, 14, A_JAVA_FILE_PATH, "MAJOR"),
        tuple("java:S1135", 5, 0, 5, 27, A_JAVA_FILE_PATH, "INFO"));
  }

  @Test
  void supportHotspots() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "package foo;\n"
        + "public class Foo {\n"
        + "  String ip = \"192.168.12.42\"; // Hotspot\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRule(s1313)
        .build(),
      issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, Issue::getStartLineOffset, Issue::getEndLine, Issue::getEndLineOffset,
      i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
        tuple("java:S1313", 3, 14, 3, 29, A_JAVA_FILE_PATH, "INFO"));
  }

  @Test
  void simpleJavaPomXml() throws Exception {
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
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRule(s3421)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S3421", 6, "pom.xml", "MINOR"));
  }

  @Test
  void supportJavaSuppressWarning() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  @SuppressWarnings(\"java:S106\")\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .addActiveRules(s1220, java_s1481)
      .build(), issues::add,
      null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH, "MINOR"),
      tuple("java:S1481", 4, A_JAVA_FILE_PATH, "MINOR"));
  }

  @Test
  void simpleJavaWithBytecode() throws Exception {
    Path projectWithByteCode = new File("src/test/projects/java-with-bytecode").getAbsoluteFile().toPath();
    ClientInputFile inputFile = TestUtils.createInputFile(projectWithByteCode.resolve("src/Foo.java"), "src/Foo.java", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(projectWithByteCode)
        .addInputFile(inputFile)
        .addActiveRules(s106, s1220, s1144, s1186)
        .putExtraProperty("sonar.java.binaries", projectWithByteCode.resolve("bin").toString())
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath()).containsOnly(
      tuple("java:S106", 5, "src/Foo.java"),
      tuple("java:S1220", null, "src/Foo.java"),
      tuple("java:S1144", 8, "src/Foo.java"),
      tuple("java:S1186", 8, "src/Foo.java"));
  }

  @Test
  void simpleJavaWithExcludedRules() throws Exception {
    ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRules(s1220, java_s1481)
        .build(),
      issues::add, null, null);

    // No S106
    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, A_JAVA_FILE_PATH, "MINOR"),
      tuple("java:S1481", 3, A_JAVA_FILE_PATH, "MINOR"));
  }

  @Test
  void simpleJavaWithIssueOnDir() throws Exception {
    ClientInputFile inputFile = prepareInputFile("foo/Foo.java",
      "package foo;\n"
        + "public class Foo {\n"
        + "}",
      false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile)
        .addActiveRules(s1228, s2094)
        .build(),
      issues::add, null, null);

    assertThat(issues).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile() != null ? i.getInputFile().relativePath() : null, Issue::getSeverity).containsOnly(
      tuple("java:S2094", 2, "foo/Foo.java", "MINOR"),
      tuple("java:S1228", null, null, "MINOR"));
  }

  @Test
  void concurrentAnalysis() throws Throwable {
    final ClientInputFile inputFile = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);

    int parallelExecutions = 4;

    ExecutorService executor = Executors.newFixedThreadPool(parallelExecutions);

    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < parallelExecutions; i++) {

      Runnable worker = () -> sonarlint.analyze(
        AnalysisConfiguration.builder()
          .setBaseDir(baseDir.toPath())
          .addInputFile(inputFile)
          .addActiveRule(s106)
          .build(),
        issue -> {
        }, null, null);
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

  @Test
  void lazy_init_file_metadata() throws Exception {
    final ClientInputFile inputFile1 = prepareInputFile(A_JAVA_FILE_PATH,
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
    File unexistingPath = new File(baseDir, "missing.bin");
    assertThat(unexistingPath).doesNotExist();
    ClientInputFile inputFile2 = new OnDiskTestClientInputFile(unexistingPath.toPath(), "missing.bin", false, StandardCharsets.UTF_8, null);

    final List<Issue> issues = new ArrayList<>();
    final List<String> logs = new CopyOnWriteArrayList<>();
    AnalysisResults analysisResults = sonarlint.analyze(
      AnalysisConfiguration.builder()
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .addActiveRule(s106)
        .build(),
      issues::add,
      (m, l) -> logs.add(m), null);

    assertThat(analysisResults.failedAnalysisFiles()).isEmpty();
    assertThat(analysisResults.indexedFileCount()).isEqualTo(2);
    assertThat(logs)
      .contains("Initializing metadata of file " + inputFile1.uri())
      .doesNotContain("Initializing metadata of file " + inputFile2.uri());
  }

  @Test
  void declare_module_should_create_a_module_container_with_loaded_extensions() {
    // aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null))
    sonarlint.startModule("key");

    ComponentContainer moduleContainer = sonarlint.getGlobalContainer().getModuleRegistry().getContainerFor("key");

    assertThat(moduleContainer).isNotNull();
    assertThat(moduleContainer.getComponentsByType(SonarLintApiModuleFileSystemAdapter.class)).isNotEmpty();
  }

  @Test
  void stop_module_should_stop_the_module_container() {
    // aClientFileSystemWith(new OnDiskTestClientInputFile(Paths.get("main.py"), "main.py", false, StandardCharsets.UTF_8, null))
    sonarlint.startModule("key");
    ComponentContainer moduleContainer = sonarlint.getGlobalContainer().getModuleRegistry().getContainerFor("key");

    sonarlint.stopModule("key");

    assertThat(moduleContainer.getPicoContainer().getLifecycleState().isStarted()).isFalse();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding, @Nullable Language language) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    return prepareInputFile(relativePath, content, isTest, StandardCharsets.UTF_8, null);
  }

  private static ActiveRule createActiveRule(String ruleKey, String severity) {
    return new ActiveRule(RuleKey.parse(ruleKey), null, severity, null, null);
  }

}
