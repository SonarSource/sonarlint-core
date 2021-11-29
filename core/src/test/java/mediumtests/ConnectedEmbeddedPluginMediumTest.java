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
package mediumtests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.PluginReferenceStore;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.PluginLocator;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;
import static testutils.TestUtils.createNoOpLogOutput;

public class ConnectedEmbeddedPluginMediumTest {

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
    Path storage = Paths.get(ConnectedEmbeddedPluginMediumTest.class.getResource("/sample-storage").toURI());
    Path tmpStorage = slHome.resolve("storage");
    FileUtils.copyDirectory(storage.toFile(), tmpStorage.toFile());
    Files.move(tmpStorage.resolve(encodeForFs("local")), tmpStorage.resolve(ProjectStoragePaths.encodeForFs(SERVER_ID)));
    PluginCache cache = PluginCache.create(pluginCache);

    PluginReferences.Builder builder = PluginReferences.newBuilder();
    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH)
      .setKey("javascript")
      .build());
    cache.get(PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR, PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH,
      (filename, toFile) -> FileUtils.copyURLToFile(PluginLocator.getJavaScriptPluginUrl(), toFile.toFile()));

    builder.addReference(PluginReference.newBuilder()
      .setFilename(PluginLocator.SONAR_JAVA_PLUGIN_JAR)
      .setHash(PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH)
      .setKey("java")
      .build());
    cache.get(PluginLocator.SONAR_JAVA_PLUGIN_JAR, PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH,
      (filename, toFile) -> FileUtils.copyURLToFile(PluginLocator.getJavaPluginUrl(), toFile.toFile()));

    Path globalFolderPath = tmpStorage.resolve(encodeForFs(SERVER_ID)).resolve("global");
    org.sonarsource.sonarlint.core.client.api.util.FileUtils.mkdirs(globalFolderPath);
    new PluginReferenceStore(new StorageFolder.Default(globalFolderPath)).store(builder.build());

    // update versions in test storage and create an empty stale project storage
    writeProjectStatus(tmpStorage, "test-project", ProjectStoragePaths.STORAGE_VERSION);
    writeProjectStatus(tmpStorage, JAVA_MODULE_KEY, ProjectStoragePaths.STORAGE_VERSION);
    writeProjectStatus(tmpStorage, "stale_module", "0");
    writeStatus(tmpStorage, VersionUtils.getLibraryVersion());

    NodeJsHelper nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(tmpStorage)
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA, Language.JS)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .useEmbeddedPlugin("java", PluginLocator.getJavaPluginUrl())
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);

    baseDir = temp.newFolder();
  }

  private static void writeProjectStatus(Path storage, String name, String version) throws IOException {
    Path project = storage.resolve(ProjectStoragePaths.encodeForFs(SERVER_ID)).resolve("projects").resolve(ProjectStoragePaths.encodeForFs(name));

    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(version)
      .setSonarlintCoreVersion("1.0")
      .setUpdateTimestamp(new Date().getTime())
      .build();
    Files.createDirectories(project);
    ProtobufUtil.writeToFile(storageStatus, project.resolve(ProjectStoragePaths.STORAGE_STATUS_PB));
  }

  private static void writeStatus(Path storage, String version) throws IOException {
    Path module = storage.resolve(ProjectStoragePaths.encodeForFs(SERVER_ID)).resolve("global");

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

  @Test
  public void rule_description_come_from_embedded() {
    assertThat(sonarlint.getRuleDetails("java:S106").getHtmlDescription())
      .isEqualTo("<p>When logging a message there are several important requirements which must be fulfilled:</p>\n"
        + "<ul>\n"
        + "  <li> The user must be able to easily retrieve the logs </li>\n"
        + "  <li> The format of all logged message must be uniform to allow the user to easily read the log </li>\n"
        + "  <li> Logged data must actually be recorded </li>\n"
        + "  <li> Sensitive data must only be logged securely </li>\n"
        + "</ul>\n"
        + "<p>If a program directly writes to the standard outputs, there is absolutely no way to comply with those requirements. That's why defining and using a\n"
        + "dedicated logger is highly recommended.</p>\n"
        + "<h2>Noncompliant Code Example</h2>\n"
        + "<pre>\n"
        + "System.out.println(\"My Message\");  // Noncompliant\n"
        + "</pre>\n"
        + "<h2>Compliant Solution</h2>\n"
        + "<pre>\n"
        + "logger.log(\"My Message\");\n"
        + "</pre>\n"
        + "<h2>See</h2>\n"
        + "<ul>\n"
        + "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/RoElAQ\">CERT, ERR02-J.</a> - Prevent exceptions while logging data </li>\n"
        + "</ul>");
  }

}
