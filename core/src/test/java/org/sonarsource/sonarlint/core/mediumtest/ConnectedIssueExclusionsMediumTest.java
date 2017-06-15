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
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.PluginLocator;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;

public class ConnectedIssueExclusionsMediumTest {

  private static final String JAVA_MODULE_KEY = "test-project-2";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static File baseDir;
  private static StoragePaths storagePaths;
  private static StorageReader storageReader;
  private static ModuleConfiguration originalModuleConfig;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    Path pluginCache = slHome.resolve("plugins");

    /*
     * This storage contains one server id "local" and two modules: "test-project" (with an empty QP) and "test-project-2" (with default QP)
     */
    Path storage = Paths.get(ConnectedIssueExclusionsMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = slHome.resolve("storage");
    FileUtils.copyDirectory(storage.toFile(), tmpStorage.toFile());
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();

    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVA_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH)
      .setKey("java")
      .build());
    cache.get(PluginLocator.SONAR_JAVA_PLUGIN_JAR, PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH,
      (filename, target) -> FileUtils.copyURLToFile(PluginLocator.getJavaPluginUrl(), target.toFile()));

    ProtobufUtil.writeToFile(builder.build(), tmpStorage.resolve("local").resolve("global").resolve(StoragePaths.PLUGIN_REFERENCES_PB));

    // update versions in test storage and create an empty stale module storage
    writeModuleStatus(tmpStorage, "test-project", VersionUtils.getLibraryVersion());
    writeModuleStatus(tmpStorage, JAVA_MODULE_KEY, VersionUtils.getLibraryVersion());
    writeStatus(tmpStorage, VersionUtils.getLibraryVersion());

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId("local")
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
    storagePaths = sonarlint.getGlobalContainer().getComponentByType(StoragePaths.class);
    storageReader = sonarlint.getGlobalContainer().getComponentByType(StorageReader.class);
    originalModuleConfig = storageReader.readModuleConfig(JAVA_MODULE_KEY);

    baseDir = temp.newFolder();
  }

  private static void writeModuleStatus(Path storage, String name, String version) throws IOException {
    Path module = storage.resolve("local").resolve("modules").resolve(name);

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StoragePaths.STORAGE_VERSION)
      .setClientUserAgent("agent")
      .setSonarlintCoreVersion(version)
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(module);
    ProtobufUtil.writeToFile(storageStatus, module.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

  private static void writeStatus(Path storage, String version) throws IOException {
    Path module = storage.resolve("local").resolve("global");

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

  @Before
  public void restoreConfig() {
    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of());
  }

  @Test
  public void issueExclusions() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S106", 4, inputFile2.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile2.getPath(), "MAJOR"));

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).isEmpty();

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S106", 4, inputFile2.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"));

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "Foo2.java",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile1.getPath(), "MAJOR"));

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "Foo2.java",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "squid:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", "Foo.java",
      "sonar.issue.ignore.multicriteria.2.ruleKey", "squid:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S106", 4, inputFile2.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"));
  }

  @Test
  public void issueInclusions() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S106", 4, inputFile2.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile2.getPath(), "MAJOR"));

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo2.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S106", 4, inputFile2.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile2.getPath(), "MAJOR"));

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo2.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile2.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile2.getPath(), "MAJOR"));

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo2.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "squid:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", "Foo.java",
      "sonar.issue.enforce.multicriteria.2.ruleKey", "squid:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("squid:S106", 4, inputFile1.getPath(), "MAJOR"),
      tuple("squid:S1220", null, inputFile1.getPath(), "MINOR"),
      tuple("squid:S1220", null, inputFile2.getPath(), "MINOR"),
      tuple("squid:S1481", 3, inputFile2.getPath(), "MAJOR"));
  }

  private List<Issue> collectIssues(ClientInputFile inputFile1, ClientInputFile inputFile2) throws IOException {
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      new ConnectedAnalysisConfiguration(JAVA_MODULE_KEY, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile1, inputFile2), ImmutableMap.<String, String>of()),
      new StoreIssueListener(issues));
    return issues;
  }

  private void updateModuleConfig(StoragePaths storagePaths, ModuleConfiguration originalModuleConfig, Map<String, String> props) {
    Builder newBuilder = ModuleConfiguration.newBuilder(originalModuleConfig);
    newBuilder.getMutableProperties().putAll(props);
    ProtobufUtil.writeToFile(newBuilder.build(), storagePaths.getModuleConfigurationPath(JAVA_MODULE_KEY));
  }

  private ClientInputFile prepareJavaInputFile1() throws IOException {
    return prepareInputFile("Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareJavaInputFile2() throws IOException {
    return prepareInputFile("Foo2.java",
      "public class Foo2 {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);
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
