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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import javax.annotation.CheckForNull;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.container.model.DefaultGlobalStorageStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class StorageReader {

  private static final Logger LOG = Loggers.get(StorageReader.class);

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

  public Sonarlint.Rules readRules() {
    Path rulesPath = storagePaths.getRulesPath();
    if (Files.exists(rulesPath)) {
      return ProtobufUtil.readFile(rulesPath, Sonarlint.Rules.parser());
    } else {
      LOG.info("Unable to find rules in the SonarLint storage. You should update the storage.");
      return Sonarlint.Rules.newBuilder().build();
    }
  }

  public Sonarlint.ActiveRules readActiveRules(String qProfileKey) {
    Path activeRulesPath = storagePaths.getActiveRulesPath(qProfileKey);
    if (Files.exists(activeRulesPath)) {
      return ProtobufUtil.readFile(activeRulesPath, Sonarlint.ActiveRules.parser());
    } else {
      LOG.info("Unable to find the quality profile {} in the SonarLint storage. You should update the storage, or ignore this message if the profile is empty.", qProfileKey);
      return Sonarlint.ActiveRules.newBuilder().build();
    }
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

  public Sonarlint.ProjectConfiguration readProjectConfig(String projectKey) {
    return ProtobufUtil.readFile(storagePaths.getProjectConfigurationPath(projectKey), Sonarlint.ProjectConfiguration.parser());
  }

  public Sonarlint.ProjectList readProjectList() {
    return ProtobufUtil.readFile(storagePaths.getProjectListPath(), Sonarlint.ProjectList.parser());
  }

  public Sonarlint.ProjectComponents readProjectComponents(String projectKey) {
    return ProtobufUtil.readFile(storagePaths.getComponentListPath(projectKey), Sonarlint.ProjectComponents.parser());
  }
}
