/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
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
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache.Copier;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.sonarsource.sonarlint.core.TestUtils.createNoOpLogOutput;

public class BrokenStorageBrokenPluginMediumTest {

  private static final String SERVER_ID = "local";
  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static ConnectedSonarLintEngine sonarlint;
  private static File baseDir;

  @BeforeClass
  public static void prepare() throws Exception {
    Path slHome = temp.newFolder().toPath();
    Path pluginCache = slHome.resolve("plugins");

    /*
     * This storage contains one server id "local" with references to java and javascript plugins but javascript JAR is corrupted
     */
    Path storage = Paths.get(BrokenStorageBrokenPluginMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = slHome.resolve("storage");
    FileUtils.copyDirectory(storage.toFile(), tmpStorage.toFile());
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();
    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH)
      .setKey("javascript")
      .build());

    Path cachedJSPlugin = cache.get(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR, PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH, new Copier() {

      @Override
      public void copy(String filename, Path toFile) throws IOException {
        FileUtils.copyURLToFile(PluginLocator.getJavaScriptPluginUrl(), toFile.toFile());
      }
    });
    FileUtils.write(cachedJSPlugin.toFile(), "corrupted jar", StandardCharsets.UTF_8);

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

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setServerId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  @AfterClass
  public static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  public void broken_startup() throws Exception {
    assertThat(sonarlint.getState()).isEqualTo(ConnectedSonarLintEngine.State.UNKNOWN);

    ClientInputFile inputFile = prepareJavaInputFile();

    final List<Issue> issues = new ArrayList<>();
    try {
      sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey(null)
        .setBaseDir(baseDir.toPath())
        .addInputFile(inputFile).build(),
        new StoreIssueListener(issues), null, null);
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isExactlyInstanceOf(GlobalStorageUpdateRequiredException.class);
    }
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
