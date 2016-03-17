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
import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleUpdateStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class StorageManager {

  private static final char ESCAPE_CHAR = '%';
  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";
  public static final String PROPERTIES_PB = "properties.pb";
  public static final String MODULE_CONFIGURATION_PB = "configuration.pb";
  public static final String RULES_PB = "rules.pb";
  public static final String UPDATE_STATUS_PB = "update_status.pb";
  public static final String SERVER_INFO_PB = "server_info.pb";
  public static final String ACTIVE_RULES_FOLDER = "active_rules";
  public static final String MODULE_LIST_PB = "module_list.pb";
  private final Path serverStorageRoot;
  private final Path globalStorageRoot;
  private final Path moduleStorageRoot;
  private final GlobalUpdateStatus updateStatus;

  public StorageManager(GlobalConfiguration configuration) {
    serverStorageRoot = configuration.getStorageRoot().resolve(configuration.getServerId());
    FileUtils.forceMkDirs(serverStorageRoot);
    globalStorageRoot = serverStorageRoot.resolve("global");
    FileUtils.forceMkDirs(globalStorageRoot);
    moduleStorageRoot = serverStorageRoot.resolve("modules");
    FileUtils.forceMkDirs(moduleStorageRoot);
    updateStatus = initUpdateStatus();
  }

  public Path getGlobalStorageRoot() {
    return globalStorageRoot;
  }

  public Path getModuleStorageRoot(String moduleKey) {
    return moduleStorageRoot.resolve(encodeForFs(moduleKey));
  }

  private static String encodeForFs(String moduleKey) {
    int len = moduleKey.length();
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      char ch = moduleKey.charAt(i);
      if (ch < ' ' || ch >= 0x7F || ch == File.separatorChar || (ch == '.' && i == 0) || ch == ESCAPE_CHAR) {
        sb.append(ESCAPE_CHAR);
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

  public Path getModuleUpdateStatusPath(String moduleKey) {
    return getModuleStorageRoot(moduleKey).resolve(UPDATE_STATUS_PB);
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

  public Path getActiveRulesPath(String qProfileKey) {
    return getGlobalStorageRoot().resolve(ACTIVE_RULES_FOLDER).resolve(qProfileKey + ".pb");
  }

  public Path getUpdateStatusPath() {
    return getGlobalStorageRoot().resolve(UPDATE_STATUS_PB);
  }

  public Path getServerInfoPath() {
    return getGlobalStorageRoot().resolve(SERVER_INFO_PB);
  }

  @CheckForNull
  public GlobalUpdateStatus getGlobalUpdateStatus() {
    return updateStatus;
  }

  @CheckForNull
  private GlobalUpdateStatus initUpdateStatus() {
    Path updateStatusPath = getUpdateStatusPath();
    if (Files.exists(updateStatusPath)) {
      final Sonarlint.UpdateStatus updateStatusFromStorage = ProtobufUtil.readFile(updateStatusPath, Sonarlint.UpdateStatus.parser());
      final Sonarlint.ServerInfos serverInfoFromStorage = ProtobufUtil.readFile(getServerInfoPath(), Sonarlint.ServerInfos.parser());
      return new GlobalUpdateStatus() {

        @Override
        public String getServerVersion() {
          return serverInfoFromStorage.getVersion();
        }

        @Override
        public Date getLastUpdateDate() {
          return new Date(updateStatusFromStorage.getUpdateTimestamp());
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

  public ModuleUpdateStatus getModuleUpdateStatus(String moduleKey) {
    Path updateStatusPath = getModuleUpdateStatusPath(moduleKey);
    if (Files.exists(updateStatusPath)) {
      final Sonarlint.UpdateStatus updateStatusFromStorage = ProtobufUtil.readFile(updateStatusPath, Sonarlint.UpdateStatus.parser());
      return new ModuleUpdateStatus() {

        @Override
        public Date getLastUpdateDate() {
          return new Date(updateStatusFromStorage.getUpdateTimestamp());
        }
      };
    }
    return null;
  }

  public Sonarlint.ModuleList readModuleListFromStorage() {
    return ProtobufUtil.readFile(getModuleListPath(), Sonarlint.ModuleList.parser());
  }
}
