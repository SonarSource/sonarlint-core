/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.ClientFileSystem;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.PluginLocator;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;
import static org.sonarsource.sonarlint.core.container.storage.StoragePaths.encodeForFs;

public class ConnectedExtraPluginMediumTest {

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
     * This storage contains one server id "local" and two projects: "test-project" (with an empty QP) and "test-project-2" (with default
     * QP)
     */
    Path storage = Paths.get(ConnectedExtraPluginMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = slHome.resolve("storage");
    FileUtils.copyDirectory(storage.toFile(), tmpStorage.toFile());
    Files.move(tmpStorage.resolve(encodeForFs("local")), tmpStorage.resolve(StoragePaths.encodeForFs(SERVER_ID)));
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();
    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH)
      .setKey("javascript")
      .build());
    cache.get(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR, PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH,
      (filename, toFile) -> FileUtils.copyURLToFile(PluginLocator.getJavaScriptPluginUrl(), toFile.toFile()));

    ProtobufUtil.writeToFile(builder.build(), tmpStorage.resolve(StoragePaths.encodeForFs(SERVER_ID)).resolve("global").resolve(StoragePaths.PLUGIN_REFERENCES_PB));

    // update versions in test storage and create an empty stale project storage
    writeProjectStatus(tmpStorage, "test-project", StoragePaths.STORAGE_VERSION);
    writeProjectStatus(tmpStorage, JAVA_MODULE_KEY, StoragePaths.STORAGE_VERSION);
    writeProjectStatus(tmpStorage, "stale_module", "0");
    writeStatus(tmpStorage, VersionUtils.getLibraryVersion());

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA, Language.JS, Language.PHP)
      .addExtraPlugin(Language.JAVA.getPluginKey(), PluginLocator.getJavaPluginUrl())
      .addExtraPlugin(Language.PHP.getPluginKey(), PluginLocator.getPhpPluginUrl())
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setModulesProvider(() -> singletonList(new ModuleInfo("key", mock(ClientFileSystem.class))))
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  private static void writeProjectStatus(Path storage, String name, String version) throws IOException {
    Path project = storage.resolve(StoragePaths.encodeForFs(SERVER_ID)).resolve("projects").resolve(StoragePaths.encodeForFs(name));

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(version)
      .setSonarlintCoreVersion("1.0")
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(project);
    ProtobufUtil.writeToFile(storageStatus, project.resolve(StoragePaths.STORAGE_STATUS_PB));
  }

  private static void writeStatus(Path storage, String version) throws IOException {
    Path module = storage.resolve(StoragePaths.encodeForFs(SERVER_ID)).resolve("global");

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StoragePaths.STORAGE_VERSION)
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
  public void readRuleDescriptionFromExtraPlugin() {
    assertThat(sonarlint.getRuleDetails("php:S3334").getSeverity()).isEqualTo("BLOCKER");
  }


  @Test
  public void analyzeFileWithExtraPlugin() throws Exception {
    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey(JAVA_MODULE_KEY)
      .setBaseDir(baseDir.toPath())
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S106", 4, inputFile.getPath(), "MAJOR"),
      tuple("java:S1220", null, inputFile.getPath(), "MINOR"),
      tuple("java:S1481", 3, inputFile.getPath(), "MINOR"),
      tuple("java:S113", null, inputFile.getPath(), "MINOR"),
      tuple("java:S1228", null, null, "MINOR"),
      tuple("java:S1106", 1, inputFile.getPath(), "MINOR"),
      tuple("java:S1106", 2, inputFile.getPath(), "MINOR"),
      tuple("java:S1451", null, inputFile.getPath(), "BLOCKER"),
      tuple("java:NoSonar", 5, inputFile.getPath(), "MAJOR"));
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

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest) throws IOException {
    final File file = new File(baseDir, relativePath);
    FileUtils.write(file, content);
    ClientInputFile inputFile = TestUtils.createInputFile(file.toPath(), relativePath, isTest);
    return inputFile;
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
