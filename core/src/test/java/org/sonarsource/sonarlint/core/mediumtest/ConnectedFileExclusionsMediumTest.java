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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.TestUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;

public class ConnectedFileExclusionsMediumTest {

  private static final String MODULE_KEY = "test-project-2";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    /*
     * This storage contains one server id "local" and two modules: "test-project" (with an empty QP) and "test-project-2" (with default QP)
     */
    Path storage = Paths.get(ConnectedFileExclusionsMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = slHome.resolve("storage");
    FileUtils.copyDirectory(storage.toFile(), tmpStorage.toFile());

    PluginReferences.Builder builder = PluginReferences.newBuilder();

    ProtobufUtil.writeToFile(builder.build(), tmpStorage.resolve("local").resolve("global").resolve(StoragePaths.PLUGIN_REFERENCES_PB));

    writeModuleStatus(tmpStorage, MODULE_KEY, VersionUtils.getLibraryVersion());
    writeStatus(tmpStorage, VersionUtils.getLibraryVersion());

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId("local")
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

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

  @Test
  public void fileInclusionsExclusions() throws Exception {
    ClientInputFile mainFile1 = prepareInputFile("foo.xoo", "function xoo() {}", false);
    ClientInputFile mainFile2 = prepareInputFile("src/foo2.xoo", "function xoo() {}", false);
    ClientInputFile testFile1 = prepareInputFile("fooTest.xoo", "function xoo() {}", true);
    ClientInputFile testFile2 = prepareInputFile("test/foo2Test.xoo", "function xoo() {}", true);

    StoragePaths storagePaths = sonarlint.getGlobalContainer().getComponentByType(StoragePaths.class);
    StorageReader storageReader = sonarlint.getGlobalContainer().getComponentByType(StorageReader.class);
    ModuleConfiguration originalModuleConfig = storageReader.readModuleConfig(MODULE_KEY);

    AnalysisResults result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(4);

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.inclusions", "src/**"));
    result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(3);

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.inclusions", "file:**/src/**"));
    result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(3);

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.exclusions", "src/**"));
    result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(3);

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.test.inclusions", "test/**"));
    result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(3);

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.test.exclusions", "test/**"));
    result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(3);

    updateModuleConfig(storagePaths, originalModuleConfig, ImmutableMap.of("sonar.inclusions", "file:**/src/**", "sonar.test.exclusions", "**/*Test.*"));
    result = analyze(mainFile1, mainFile2, testFile1, testFile2);
    assertThat(result.fileCount()).isEqualTo(1);
  }

  private void updateModuleConfig(StoragePaths storagePaths, ModuleConfiguration originalModuleConfig, Map<String, String> props) {
    Builder newBuilder = ModuleConfiguration.newBuilder(originalModuleConfig);
    newBuilder.putAllProperties(props);
    ProtobufUtil.writeToFile(newBuilder.build(), storagePaths.getModuleConfigurationPath(MODULE_KEY));
  }

  private AnalysisResults analyze(ClientInputFile mainFile1, ClientInputFile mainFile2, ClientInputFile testFile1, ClientInputFile testFile2) throws IOException {
    AnalysisResults result = sonarlint.analyze(
      new ConnectedAnalysisConfiguration(MODULE_KEY, baseDir.toPath(), temp.newFolder().toPath(), Arrays.asList(mainFile1, mainFile2, testFile1, testFile2),
        ImmutableMap.<String, String>of()),
      issue -> {
      });
    return result;
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
