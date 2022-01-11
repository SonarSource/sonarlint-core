/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.PluginReferenceStore;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.PluginLocator;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;
import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;

public class ConnectedIssueExclusionsMediumTest {

  private static final String FILE1_PATH = "Foo.java";
  private static final String FILE2_PATH = "Foo2.java";
  private static final String SERVER_ID = "local";
  private static final String JAVA_MODULE_KEY = "test-project-2";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static File baseDir;
  private static ProjectStoragePaths projectStoragePaths;
  private static StorageReader storageReader;
  private static ProjectConfiguration originalModuleConfig;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    Path pluginCache = slHome.resolve("plugins");

    /*
     * This storage contains one server id "local" and two projects: "test-project" (with an empty QP) and "test-project-2" (with default
     * QP)
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

    Path globalFolderPath = tmpStorage.resolve(encodeForFs(SERVER_ID)).resolve("global");
    org.sonarsource.sonarlint.core.client.api.util.FileUtils.mkdirs(globalFolderPath);
    new PluginReferenceStore(new StorageFolder.Default(globalFolderPath)).store(builder.build());

    // update versions in test storage and create an empty stale module storage
    writeProjectStatus(tmpStorage, "test-project", VersionUtils.getLibraryVersion());
    writeProjectStatus(tmpStorage, JAVA_MODULE_KEY, VersionUtils.getLibraryVersion());
    writeStatus(tmpStorage, VersionUtils.getLibraryVersion());

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguage(Language.JAVA)
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
    projectStoragePaths = sonarlint.getGlobalContainer().getComponentByType(ProjectStoragePaths.class);
    storageReader = sonarlint.getGlobalContainer().getComponentByType(StorageReader.class);
    originalModuleConfig = storageReader.readProjectConfig(JAVA_MODULE_KEY);

    baseDir = temp.newFolder();
  }

  private static void writeProjectStatus(Path storage, String name, String version) throws IOException {
    Path module = storage.resolve(encodeForFs(SERVER_ID)).resolve("projects").resolve(encodeForFs(name));

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(ProjectStoragePaths.STORAGE_VERSION)
      .setSonarlintCoreVersion(version)
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(module);
    ProtobufUtil.writeToFile(storageStatus, module.resolve(ProjectStoragePaths.STORAGE_STATUS_PB));
  }

  private static void writeStatus(Path storage, String version) throws IOException {
    Path module = storage.resolve(encodeForFs(SERVER_ID)).resolve("global");

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(ProjectStoragePaths.STORAGE_VERSION)
      .setSonarlintCoreVersion(version)
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(module);
    ProtobufUtil.writeToFile(storageStatus, module.resolve(ProjectStoragePaths.STORAGE_STATUS_PB));
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
    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of());
  }

  @Test
  public void issueExclusions() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).isEmpty();

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.ignore.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.ignore.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.ignore.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"));
  }

  @Test
  public void issueExclusionsByRegexp() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL1"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "NOSL(1|2)"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).isEmpty();
  }

  @Test
  public void issueExclusionsByBlock() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "SON.*-OFF",
      "sonar.issue.ignore.block.1.endBlockRegexp", "SON.*-ON"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));
  }

  @Test
  public void issueInclusions() throws Exception {
    ClientInputFile inputFile1 = prepareJavaInputFile1();
    ClientInputFile inputFile2 = prepareJavaInputFile2();

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "Foo*.java",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE1_PATH, "BLOCKER"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*S1481"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 4, FILE2_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));

    updateProjectConfig(projectStoragePaths, originalModuleConfig, ImmutableMap.of("sonar.issue.enforce.multicriteria", "1,2",
      "sonar.issue.enforce.multicriteria.1.resourceKey", FILE2_PATH,
      "sonar.issue.enforce.multicriteria.1.ruleKey", "java:S1481",
      "sonar.issue.enforce.multicriteria.2.resourceKey", FILE1_PATH,
      "sonar.issue.enforce.multicriteria.2.ruleKey", "java:S106"));
    assertThat(collectIssues(inputFile1, inputFile2)).extracting(Issue::getRuleKey, Issue::getStartLine, i -> i.getInputFile().relativePath(), Issue::getSeverity).containsOnly(
      tuple("java:S106", 5, FILE1_PATH, "MAJOR"),
      tuple("java:S1220", null, FILE1_PATH, "MINOR"),
      tuple("java:S1220", null, FILE2_PATH, "MINOR"),
      tuple("java:S1481", 3, FILE2_PATH, "BLOCKER"));
  }

  private List<Issue> collectIssues(ClientInputFile inputFile1, ClientInputFile inputFile2) throws IOException {
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(
      ConnectedAnalysisConfiguration.builder()
        .setProjectKey(JAVA_MODULE_KEY)
        .setBaseDir(baseDir.toPath())
        .addInputFiles(inputFile1, inputFile2)
        .build(),
      new StoreIssueListener(issues), null, null);
    return issues;
  }

  private void updateProjectConfig(ProjectStoragePaths projectStoragePaths, ProjectConfiguration originalProjectConfig, Map<String, String> props) {
    Builder newBuilder = ProjectConfiguration.newBuilder(originalProjectConfig);
    newBuilder.putAllProperties(props);
    ProtobufUtil.writeToFile(newBuilder.build(), projectStoragePaths.getProjectConfigurationPath(JAVA_MODULE_KEY));
  }

  private ClientInputFile prepareJavaInputFile1() throws IOException {
    return prepareInputFile(FILE1_PATH,
      "/*NOSL1*/ public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    // SONAR-OFF\n"
        + "    System.out.println(\"Foo\");\n"
        + "    // SONAR-ON\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareJavaInputFile2() throws IOException {
    return prepareInputFile(FILE2_PATH,
      "/*NOSL2*/ public class Foo2 {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

  static class StoreIssueListener implements IssueListener {
    private final List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }
  }

}
