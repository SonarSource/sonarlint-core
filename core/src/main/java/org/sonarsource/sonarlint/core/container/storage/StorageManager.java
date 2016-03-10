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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleSyncStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class StorageManager {

  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";
  public static final String PROPERTIES_PB = "properties.pb";
  public static final String MODULE_CONFIGURATION_PB = "configuration.pb";
  public static final String RULES_PB = "rules.pb";
  public static final String SYNC_STATUS_PB = "sync_status.pb";
  public static final String SERVER_INFO_PB = "server_info.pb";
  public static final String ACTIVE_RULES_FOLDER = "active_rules";
  public static final String MODULE_LIST_PB = "module_list.pb";
  private final Path serverStorageRoot;
  private final Path globalStorageRoot;
  private final Path moduleStorageRoot;
  private final GlobalSyncStatus syncStatus;

  public StorageManager(GlobalConfiguration configuration) {
    serverStorageRoot = configuration.getStorageRoot().resolve(configuration.getServerId());
    FileUtils.forceMkDirs(serverStorageRoot);
    globalStorageRoot = serverStorageRoot.resolve("global");
    FileUtils.forceMkDirs(globalStorageRoot);
    moduleStorageRoot = serverStorageRoot.resolve("modules");
    FileUtils.forceMkDirs(moduleStorageRoot);
    syncStatus = initSyncStatus();
  }

  public Path getGlobalStorageRoot() {
    return globalStorageRoot;
  }

  public Path getModuleStorageRoot(String moduleKey) {
    return moduleStorageRoot.resolve(encodeForFs(moduleKey));
  }

  private String encodeForFs(String moduleKey) {
    char fileSep = File.separatorChar;
    char escape = '%';
    int len = moduleKey.length();
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      char ch = moduleKey.charAt(i);
      if (ch < ' ' || ch >= 0x7F || ch == fileSep || (ch == '.' && i == 0) || ch == escape) {
        sb.append(escape);
        if (ch < 0x10) {
          sb.append('0');
        }
        sb.append(Integer.toHexString(ch));
      } else {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  public Path getModuleConfigurationPath(String moduleKey) {
    return getModuleStorageRoot(moduleKey).resolve(MODULE_CONFIGURATION_PB);
  }

  public Path getModuleSyncStatusPath(String moduleKey) {
    return getModuleStorageRoot(moduleKey).resolve(SYNC_STATUS_PB);
  }

  public Path getPluginReferencesPath() {
    return globalStorageRoot.resolve(PLUGIN_REFERENCES_PB);
  }

  public Path getGlobalPropertiesPath() {
    return globalStorageRoot.resolve(PROPERTIES_PB);
  }

  public Path getModuleListPath() {
    return globalStorageRoot.resolve(MODULE_LIST_PB);
  }

  public Path getRulesPath() {
    return globalStorageRoot.resolve(RULES_PB);
  }

  public Path getActiveRulesPath(String qProfileKey) {
    return globalStorageRoot.resolve(ACTIVE_RULES_FOLDER).resolve(qProfileKey + ".pb");
  }

  public Path getSyncStatusPath() {
    return globalStorageRoot.resolve(SYNC_STATUS_PB);
  }

  public Path getServerInfoPath() {
    return globalStorageRoot.resolve(SERVER_INFO_PB);
  }

  @CheckForNull
  public GlobalSyncStatus getGlobalSyncStatus() {
    return syncStatus;
  }

  @CheckForNull
  private GlobalSyncStatus initSyncStatus() {
    Path syncStatusPath = getSyncStatusPath();
    if (Files.exists(syncStatusPath)) {
      final Sonarlint.SyncStatus syncStatusFromStorage = ProtobufUtil.readFile(syncStatusPath, Sonarlint.SyncStatus.parser());
      final Sonarlint.ServerInfos serverInfoFromStorage = ProtobufUtil.readFile(getServerInfoPath(), Sonarlint.ServerInfos.parser());
      return new GlobalSyncStatus() {

        @Override
        public String getServerVersion() {
          return serverInfoFromStorage.getVersion();
        }

        @Override
        public Date getLastSyncDate() {
          return new Date(syncStatusFromStorage.getSyncTimestamp());
        }
      };
    }
    return null;
  }

  public Sonarlint.Rules readRulesFromStorage() {
    return ProtobufUtil.readFile(getRulesPath(), Sonarlint.Rules.parser());
  }

  public Sonarlint.GlobalProperties readGlobalPropertiesFromStorage() {
    return ProtobufUtil.readFile(getGlobalPropertiesPath(), Sonarlint.GlobalProperties.parser());
  }

  public Sonarlint.ModuleConfiguration readModuleConfigFromStorage(String moduleKey) {
    return ProtobufUtil.readFile(getModuleConfigurationPath(moduleKey), Sonarlint.ModuleConfiguration.parser());
  }

  public ModuleSyncStatus getModuleSyncStatus(String moduleKey) {
    Path syncStatusPath = getModuleSyncStatusPath(moduleKey);
    if (Files.exists(syncStatusPath)) {
      final Sonarlint.SyncStatus syncStatusFromStorage = ProtobufUtil.readFile(syncStatusPath, Sonarlint.SyncStatus.parser());
      return new ModuleSyncStatus() {

        @Override
        public Date getLastSyncDate() {
          return new Date(syncStatusFromStorage.getSyncTimestamp());
        }
      };
    }
    return null;
  }

  public Sonarlint.ModuleList readModuleListFromStorage() {
    return ProtobufUtil.readFile(getModuleListPath(), Sonarlint.ModuleList.parser());
  }
}
