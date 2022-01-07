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
package org.sonarsource.sonarlint.core.mediumtest.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.container.storage.ServerProjectsStore;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.PluginLocator;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.junit.jupiter.api.Assertions.fail;
import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.util.PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR;
import static org.sonarsource.sonarlint.core.util.PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH;
import static org.sonarsource.sonarlint.core.util.PluginLocator.SONAR_JAVA_PLUGIN_JAR;
import static org.sonarsource.sonarlint.core.util.PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH;

public class StorageFixture {
  public static StorageBuilder newStorage(String connectionId) {
    return new StorageBuilder(connectionId);
  }

  public static class Storage {
    private final Path path;
    private final List<Path> pluginPaths;
    private final List<ProjectStorageFixture.ProjectStorage> projectStorages;

    private Storage(Path path, List<Path> pluginPaths, List<ProjectStorageFixture.ProjectStorage> projectStorages) {
      this.path = path;
      this.pluginPaths = pluginPaths;
      this.projectStorages = projectStorages;
    }

    public Path getPath() {
      return path;
    }

    public List<Path> getPluginPaths() {
      return pluginPaths;
    }

    public List<ProjectStorageFixture.ProjectStorage> getProjectStorages() {
      return projectStorages;
    }
  }

  public static class StorageBuilder {
    private final String connectionId;
    private boolean isStale;
    private final List<Plugin> plugins = new ArrayList<>();
    private final List<ProjectStorageFixture.ProjectStorageBuilder> projectBuilders = new ArrayList<>();

    private StorageBuilder(String connectionId) {
      this.connectionId = connectionId;
    }

    public StorageBuilder stale() {
      isStale = true;
      return this;
    }

    public StorageBuilder withJSPlugin() {
      return withPlugin(PluginLocator.getJavaScriptPluginPath(), SONAR_JAVASCRIPT_PLUGIN_JAR, SONAR_JAVASCRIPT_PLUGIN_JAR_HASH, "javascript");
    }

    public StorageBuilder withJavaPlugin() {
      return withPlugin(PluginLocator.getJavaPluginPath(), SONAR_JAVA_PLUGIN_JAR, SONAR_JAVA_PLUGIN_JAR_HASH, "java");
    }

    private StorageBuilder withPlugin(Path path, String jarName, String hash, String key) {
      plugins.add(new Plugin(path, jarName, hash, key));
      return this;
    }

    public StorageBuilder withProject(String projectKey, Consumer<ProjectStorageFixture.ProjectStorageBuilder> consumer) {
      var builder = new ProjectStorageFixture.ProjectStorageBuilder(projectKey);
      consumer.accept(builder);
      projectBuilders.add(builder);
      return this;
    }

    public StorageBuilder withProject(String projectKey) {
      projectBuilders.add(new ProjectStorageFixture.ProjectStorageBuilder(projectKey));
      return this;
    }

    public Storage create(Path rootPath) {
      var storagePath = rootPath.resolve("storage");
      var connectionStorage = storagePath.resolve(encodeForFs(connectionId));
      var globalFolderPath = connectionStorage.resolve("global");
      var pluginsFolderPath = connectionStorage.resolve("plugins");
      var projectsFolderPath = connectionStorage.resolve("projects");
      org.sonarsource.sonarlint.core.client.api.util.FileUtils.mkdirs(globalFolderPath);
      org.sonarsource.sonarlint.core.client.api.util.FileUtils.mkdirs(pluginsFolderPath);

      var pluginPaths = createPlugins(pluginsFolderPath);
      createPluginReferences(pluginsFolderPath);
      createUpdateStatus(globalFolderPath, isStale ? "0" : ProjectStoragePaths.STORAGE_VERSION);
      createServerInfo(globalFolderPath);
      createProjectList(globalFolderPath);

      List<ProjectStorageFixture.ProjectStorage> projectStorages = new ArrayList<>();
      projectBuilders.forEach(project -> projectStorages.add(project.create(projectsFolderPath)));
      return new Storage(storagePath, pluginPaths, projectStorages);
    }

    private List<Path> createPlugins(Path pluginsFolderPath) {
      List<Path> pluginPaths = new ArrayList<>();
      plugins.forEach(plugin -> {
        var pluginPath = pluginsFolderPath.resolve(plugin.jarName);
        try {
          Files.copy(plugin.path, pluginPath);
        } catch (IOException e) {
          fail("Cannot copy plugin " + plugin.jarName, e);
        }
        pluginPaths.add(pluginPath);
      });
      return pluginPaths;
    }

    private void createPluginReferences(Path pluginsFolderPath) {
      var builder = Sonarlint.PluginReferences.newBuilder();
      plugins.forEach(plugin -> builder.putPluginsByKey(plugin.key, Sonarlint.PluginReferences.PluginReference.newBuilder()
        .setFilename(plugin.jarName)
        .setHash(plugin.hash)
        .setKey(plugin.key)
        .build()));
      ProtobufUtil.writeToFile(builder.build(), pluginsFolderPath.resolve("plugin_references.pb"));
    }

    private static void createUpdateStatus(Path storage, String version) {
      var storageStatus = Sonarlint.StorageStatus.newBuilder()
        .setStorageVersion(version)
        .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
        .setUpdateTimestamp(new Date().getTime())
        .build();
      ProtobufUtil.writeToFile(storageStatus, storage.resolve(ProjectStoragePaths.STORAGE_STATUS_PB));
    }

    private static void createServerInfo(Path globalFolderPath) {
      ProtobufUtil.writeToFile(Sonarlint.ServerInfos.newBuilder().setVersion("7.9").build(),
        globalFolderPath.resolve(ServerInfoStore.SERVER_INFO_PB));
    }

    private void createProjectList(Path globalFolderPath) {
      Map<String, Sonarlint.ProjectList.Project> projectsByKey = projectBuilders.stream().collect(Collectors.toMap(
        ProjectStorageFixture.ProjectStorageBuilder::getProjectKey, p -> Sonarlint.ProjectList.Project.newBuilder()
          .setKey(p.getProjectKey())
          .setName(p.getProjectKey())
          .build()));
      ProtobufUtil.writeToFile(Sonarlint.ProjectList.newBuilder().putAllProjectsByKey(projectsByKey).build(),
        globalFolderPath.resolve(ServerProjectsStore.PROJECT_LIST_PB));
    }

    private static class Plugin {
      private final Path path;
      private final String jarName;
      private final String hash;
      private final String key;

      private Plugin(Path path, String jarName, String hash, String key) {
        this.path = path;
        this.jarName = jarName;
        this.hash = hash;
        this.key = key;
      }
    }
  }
}
