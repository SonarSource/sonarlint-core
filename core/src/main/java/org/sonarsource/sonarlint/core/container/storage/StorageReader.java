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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import javax.annotation.CheckForNull;

import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.container.model.DefaultGlobalStorageStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class StorageReader {
  private final StoragePaths storagePaths;
  private final GlobalStorageStatus storageStatus;

  public StorageReader(StoragePaths storagePaths) {
    this.storagePaths = storagePaths;
    this.storageStatus = initStorageStatus();
  }
  
  @CheckForNull
  public GlobalStorageStatus getGlobalStorageStatus() {
    return storageStatus;
  }

  @CheckForNull
  private GlobalStorageStatus initStorageStatus() {
    Path storageStatusPath = storagePaths.getStorageStatusPath();
    if (Files.exists(storageStatusPath)) {
      final Sonarlint.StorageStatus currentStorageStatus = ProtobufUtil.readFile(storageStatusPath, Sonarlint.StorageStatus.parser());
      final boolean stale = !currentStorageStatus.getStorageVersion().equals(StoragePaths.STORAGE_VERSION);

      String version = null;
      if (!stale) {
        final Sonarlint.ServerInfos serverInfoFromStorage = ProtobufUtil.readFile(storagePaths.getServerInfosPath(), Sonarlint.ServerInfos.parser());
        version = serverInfoFromStorage.getVersion();
      }

      return new DefaultGlobalStorageStatus(version, new Date(currentStorageStatus.getUpdateTimestamp()), stale);
    }
    return null;
  }

  public Sonarlint.ServerInfos readServerInfos() {
    return ProtobufUtil.readFile(storagePaths.getServerInfosPath(), Sonarlint.ServerInfos.parser());
  }

  public Sonarlint.ServerIssues readServerIsses(String moduleKey) {
    return ProtobufUtil.readFile(storagePaths.getServerIssuesPath(moduleKey), Sonarlint.ServerIssues.parser());
  }

  public Sonarlint.Rules readRules() {
    return ProtobufUtil.readFile(storagePaths.getRulesPath(), Sonarlint.Rules.parser());
  }
  
  public Sonarlint.ActiveRules readActiveRules(String qProfileKey) {
    return ProtobufUtil.readFile(storagePaths.getActiveRulesPath(qProfileKey), Sonarlint.ActiveRules.parser());
  }

  public Sonarlint.QProfiles readQProfiles() {
    return ProtobufUtil.readFile(storagePaths.getQProfilesPath(), Sonarlint.QProfiles.parser());
  }

  public Sonarlint.GlobalProperties readGlobalProperties() {
    return ProtobufUtil.readFile(storagePaths.getGlobalPropertiesPath(), Sonarlint.GlobalProperties.parser());
  }

  public Sonarlint.PluginReferences readPluginReferences() {
    return ProtobufUtil.readFile(storagePaths.getPluginReferencesPath(), Sonarlint.PluginReferences.parser());
  }

  public Sonarlint.ModuleConfiguration readModuleConfig(String moduleKey) {
    return ProtobufUtil.readFile(storagePaths.getModuleConfigurationPath(moduleKey), Sonarlint.ModuleConfiguration.parser());
  }

  public Sonarlint.ModuleList readModuleList() {
    return ProtobufUtil.readFile(storagePaths.getModuleListPath(), Sonarlint.ModuleList.parser());
  }
}
