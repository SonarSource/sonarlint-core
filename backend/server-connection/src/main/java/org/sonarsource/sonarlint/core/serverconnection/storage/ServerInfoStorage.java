/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverapi.system.ServerStatusInfo;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.ServerSettings;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.ServerSettings.MQR_MODE_SETTING;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class ServerInfoStorage {
  public static final String SERVER_INFO_PB = "server_info.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public ServerInfoStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(SERVER_INFO_PB);
  }

  public void store(ServerStatusInfo serverStatus, Set<Feature> features, Map<String, String> globalSettings) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var serverInfoToStore = adapt(serverStatus, features, globalSettings);
    LOG.debug("Storing server info in {}", storageFilePath);
    rwLock.write(() -> writeToFile(serverInfoToStore, storageFilePath));
    LOG.debug("Stored server info");
  }

  public Optional<StoredServerInfo> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.ServerInfo.parser())))
      : Optional.empty());
  }

  private static Sonarlint.ServerInfo adapt(ServerStatusInfo serverStatus, Set<Feature> features, Map<String, String> globalSettings) {
    return Sonarlint.ServerInfo.newBuilder()
      .setVersion(serverStatus.version())
      .setServerId(serverStatus.id())
      .putAllGlobalSettings(globalSettings)
      .addAllSupportedFeatures(features.stream().map(Feature::getKey).toList())
      .build();
  }

  private static StoredServerInfo adapt(Sonarlint.ServerInfo serverInfo) {
    var globalSettings = serverInfo.getGlobalSettingsMap();
    if (globalSettings.isEmpty()) {
      // migration for not yet synchronized storage
      globalSettings = new HashMap<>();
      if (serverInfo.hasIsMqrMode()) {
        globalSettings.put(MQR_MODE_SETTING, Boolean.toString(serverInfo.getIsMqrMode()));
      }
      globalSettings.put(ServerSettings.EARLY_ACCESS_MISRA_ENABLED, Boolean.toString(serverInfo.getMisraEarlyAccessRulesEnabled()));
    }
    return new StoredServerInfo(Version.create(serverInfo.getVersion()),
      serverInfo.getSupportedFeaturesList().stream().map(Feature::fromKey).flatMap(Optional::stream).collect(Collectors.toSet()), new ServerSettings(globalSettings),
      serverInfo.getServerId());
  }

}
