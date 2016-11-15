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
package org.sonarsource.sonarlint.core.container.storage;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.container.model.DefaultGlobalStorageStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class StorageManager {

  /**
   * Version of the storage. This should be incremented each time an incompatible change is made to the storage.
   */
  public static final String STORAGE_VERSION = "1";

  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";
  public static final String PROPERTIES_PB = "properties.pb";
  public static final String MODULE_CONFIGURATION_PB = "configuration.pb";
  public static final String RULES_PB = "rules.pb";
  public static final String QUALITY_PROFILES_PB = "quality_profiles.pb";
  public static final String STORAGE_STATUS_PB = "storage_status.pb";
  public static final String SERVER_INFO_PB = "server_info.pb";
  public static final String ACTIVE_RULES_FOLDER = "active_rules";
  public static final String MODULE_LIST_PB = "module_list.pb";
  public static final String SERVER_ISSUES_DIR = "server_issues";

  private final Path serverStorageRoot;
  private final Path globalStorageRoot;
  private final Path moduleStorageRoot;
  private final GlobalStorageStatus storageStatus;

  public StorageManager(ConnectedGlobalConfiguration configuration) {
    serverStorageRoot = configuration.getStorageRoot().resolve(encodeForFs(configuration.getServerId()));
    globalStorageRoot = serverStorageRoot.resolve("global");
    moduleStorageRoot = serverStorageRoot.resolve("modules");
    storageStatus = initStorageStatus();
  }

  public Path getServerStorageRoot() {
    return serverStorageRoot;
  }

  public Path getGlobalStorageRoot() {
    return globalStorageRoot;
  }

  public Path getModuleStorageRoot(String moduleKey) {
    return moduleStorageRoot.resolve(encodeForFs(moduleKey));
  }

  public static String encodeForFs(String name) {
    try {
      return URLEncoder.encode(name, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Unable to encode name: " + name, e);
    }
  }

  public Path getModuleConfigurationPath(String moduleKey) {
    return getModuleStorageRoot(moduleKey).resolve(MODULE_CONFIGURATION_PB);
  }

  public Path getModuleUpdateStatusPath(String moduleKey) {
    return getModuleStorageRoot(moduleKey).resolve(STORAGE_STATUS_PB);
  }

  public Path getPluginReferencesPath() {
    return getGlobalStorageRoot().resolve(PLUGIN_REFERENCES_PB);
  }

  public Path getGlobalPropertiesPath() {
    return getGlobalStorageRoot().resolve(PROPERTIES_PB);
  }

  public Path getModuleListPath() {
    return getGlobalStorageRoot().resolve(MODULE_LIST_PB);
  }

  public Path getRulesPath() {
    return getGlobalStorageRoot().resolve(RULES_PB);
  }

  public Path getQProfilesPath() {
    return getGlobalStorageRoot().resolve(QUALITY_PROFILES_PB);
  }

  public Path getActiveRulesPath(String qProfileKey) {
    return getGlobalStorageRoot().resolve(ACTIVE_RULES_FOLDER).resolve(encodeForFs(qProfileKey) + ".pb");
  }

  public Path getStorageStatusPath() {
    return getGlobalStorageRoot().resolve(STORAGE_STATUS_PB);
  }

  public Path getServerInfosPath() {
    return getGlobalStorageRoot().resolve(SERVER_INFO_PB);
  }

  public Path getServerIssuesPath(String moduleKey) {
    return getModuleStorageRoot(moduleKey).resolve(SERVER_ISSUES_DIR);
  }

  @CheckForNull
  public GlobalStorageStatus getGlobalStorageStatus() {
    return storageStatus;
  }

  @CheckForNull
  private GlobalStorageStatus initStorageStatus() {
    Path storageStatusPath = getStorageStatusPath();
    if (Files.exists(storageStatusPath)) {
      final Sonarlint.StorageStatus currentStorageStatus = ProtobufUtil.readFile(storageStatusPath, Sonarlint.StorageStatus.parser());
      final boolean stale = !currentStorageStatus.getStorageVersion().equals(STORAGE_VERSION);

      String version = null;
      if (!stale) {
        final Sonarlint.ServerInfos serverInfoFromStorage = ProtobufUtil.readFile(getServerInfosPath(), Sonarlint.ServerInfos.parser());
        version = serverInfoFromStorage.getVersion();
      }

      return new DefaultGlobalStorageStatus(version, new Date(currentStorageStatus.getUpdateTimestamp()), stale);
    }
    return null;
  }

  public Sonarlint.ServerInfos readServerInfosFromStorage() {
    return ProtobufUtil.readFile(getServerInfosPath(), Sonarlint.ServerInfos.parser());
  }

  public Sonarlint.ServerIssues readServerIssesFromStorage(String moduleKey) {
    return ProtobufUtil.readFile(getServerIssuesPath(moduleKey), Sonarlint.ServerIssues.parser());
  }

  public Sonarlint.Rules readRulesFromStorage() {
    return ProtobufUtil.readFile(getRulesPath(), Sonarlint.Rules.parser());
  }

  public Sonarlint.QProfiles readQProfilesFromStorage() {
    return ProtobufUtil.readFile(getQProfilesPath(), Sonarlint.QProfiles.parser());
  }

  public Sonarlint.GlobalProperties readGlobalPropertiesFromStorage() {
    return ProtobufUtil.readFile(getGlobalPropertiesPath(), Sonarlint.GlobalProperties.parser());
  }

  public Sonarlint.PluginReferences readPluginReferencesFromStorage() {
    return ProtobufUtil.readFile(getPluginReferencesPath(), Sonarlint.PluginReferences.parser());
  }

  public Sonarlint.ModuleConfiguration readModuleConfigFromStorage(String moduleKey) {
    return ProtobufUtil.readFile(getModuleConfigurationPath(moduleKey), Sonarlint.ModuleConfiguration.parser());
  }

  public Sonarlint.ModuleList readModuleListFromStorage() {
    return ProtobufUtil.readFile(getModuleListPath(), Sonarlint.ModuleList.parser());
  }
}
