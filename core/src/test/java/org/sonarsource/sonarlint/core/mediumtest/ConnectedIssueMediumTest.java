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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache.Copier;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.PluginLocator;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpIssueListener;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;

public class ConnectedIssueMediumTest {

  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    Path pluginCache = slHome.resolve("plugins");

    /*
     * This storage contains one server id "local" and two modules: "test-project" (with an empty QP) and "test-project-2" (with default QP)
     */
    Path storage = Paths.get(ConnectedIssueMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = slHome.resolve("storage");
    FileUtils.copyDirectory(storage.toFile(), tmpStorage.toFile());
    Files.move(tmpStorage.resolve("local"), tmpStorage.resolve(StoragePaths.encodeForFs(SERVER_ID)));
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();
    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH)
      .setKey("javascript")
      .build());
    cache.get(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR, PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH, new Copier() {

      @Override
      public void copy(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(PluginLocator.getJavaScriptPluginUrl(), toFile.toFile());
      }
    });

    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVA_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH)
      .setKey("java")
      .build());
    cache.get(PluginLocator.SONAR_JAVA_PLUGIN_JAR, PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH, new Copier() {

      @Override
      public void copy(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(PluginLocator.getJavaPluginUrl(), toFile.toFile());
      }
    });

    ProtobufUtil.writeToFile(builder.build(), tmpStorage.resolve(StoragePaths.encodeForFs(SERVER_ID)).resolve("global").resolve(StoragePaths.PLUGIN_REFERENCES_PB));

    // update versions in test storage and create an empty stale module storage
    writeModuleStatus(tmpStorage, "test-project", StoragePaths.STORAGE_VERSION);
    writeModuleStatus(tmpStorage, JAVA_MODULE_KEY, StoragePaths.STORAGE_VERSION);
    writeModuleStatus(tmpStorage, "stale_module", "0");
    writeStatus(tmpStorage, VersionUtils.getLibraryVersion());

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  private static void writeModuleStatus(Path storage, String name, String version) throws IOException {
    Path module = storage.resolve(StoragePaths.encodeForFs(SERVER_ID)).resolve("modules").resolve(name);

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(version)
      .setClientUserAgent("agent")
      .setSonarlintCoreVersion("1.0")
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(module);
    ProtobufUtil.writeToFile(storageStatus, module.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

  private static void writeStatus(Path storage, String version) throws IOException {
    Path module = storage.resolve(StoragePaths.encodeForFs(SERVER_ID)).resolve("global");

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StoragePaths.STORAGE_VERSION)
      .setClientUserAgent("agent")
      .setSonarlintCoreVersion(version)
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(module);
    ProtobufUtil.writeToFile(storageStatus, module.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

  @AfterClass
  public static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  public void testContainerInfo() {
    assertThat(sonarlint.getLoadedAnalyzers()).extracting("key").containsOnly("java", "javascript");
    assertThat(sonarlint.allModulesByKey().keySet()).containsOnly("test-project", "test-project-2");
  }

  @Test
  public void testStaleModule() throws IOException {
    assertThat(sonarlint.getModuleStorageStatus("stale_module").isStale()).isTrue();
    ConnectedAnalysisConfiguration config = new ConnectedAnalysisConfiguration("stale_module",
      baseDir.toPath(),
      temp.newFolder().toPath(),
      Collections.<ClientInputFile>emptyList(),
      ImmutableMap.<String, String>of());

    try {
      sonarlint.analyze(config, createNoOpIssueListener());
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(StorageException.class)
        .hasMessage("Stored data for module 'stale_module' is stale because it was created with a different version of SonarLint. Please update the binding.");
    }
  }

  @Test
  public void simpleJavaScriptUnbinded() throws Exception {

    String ruleKey = "javascript:UnusedVariable";
    RuleDetails ruleDetails = sonarlint.getRuleDetails(ruleKey);
    assertThat(ruleDetails.getKey()).isEqualTo(ruleKey);
    assertThat(ruleDetails.getName()).isEqualTo("Unused local variables should be removed");
    assertThat(ruleDetails.getLanguage()).isEqualTo("js");
    assertThat(ruleDetails.getSeverity()).isEqualTo("MAJOR");
    assertThat(ruleDetails.getHtmlDescription()).contains("<p>", "If a local variable is declared but not used");
    assertThat(ruleDetails.getExtendedDescription()).isEmpty();

    ClientInputFile inputFile = prepareInputFile("foo.js", "function foo() {\n"
      + "  var x;\n"
      + "  var y; //NOSONAR\n"
      + "}", false);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new ConnectedAnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new StoreIssueListener(issues));
    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path").containsOnly(
      tuple(ruleKey, 2, inputFile.getPath()));

  }

  @Test
  public void simpleJavaUnbinded() throws Exception {
    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new ConnectedAnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new StoreIssueListener(issues));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simpleJavaTestUnbinded() throws Exception {
    ClientInputFile inputFile = prepareJavaTestInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(new ConnectedAnalysisConfiguration(null, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new StoreIssueListener(issues));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S2187", 1, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void simpleJavaBinded() throws Exception {
    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new ConnectedAnalysisConfiguration(JAVA_MODULE_KEY, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new StoreIssueListener(issues));

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile.getPath(), "MAJOR"));
  }

  @Test
  public void emptyQPJava() throws IOException {
    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new ConnectedAnalysisConfiguration("test-project", baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile), ImmutableMap.<String, String>of()),
      new StoreIssueListener(issues));

    assertThat(issues).isEmpty();
  }

  private ClientInputFile prepareJavaInputFile() throws IOException {
    return prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareJavaTestInputFile() throws IOException {
    return prepareInputFile("FooTest.java",
      "public class FooTest {\n"
        + "  public void foo() {\n"
        + "  }\n"
        + "}",
      true);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = TestUtils.createInputFile(file.toPath(), isTest);
    return inputFile;
  }

  static class StoreIssueListener implements IssueListener {
    private List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }
  }

}
