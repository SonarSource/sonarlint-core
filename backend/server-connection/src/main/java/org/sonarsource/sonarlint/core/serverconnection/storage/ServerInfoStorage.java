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
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.system.ServerStatusInfo;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class ServerInfoStorage {
  public static final String SERVER_INFO_PB = "server_info.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public ServerInfoStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(SERVER_INFO_PB);
  }

  public void store(ServerStatusInfo serverStatus, @Nullable Boolean isMQRMode) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var serverInfoToStore = adapt(serverStatus, isMQRMode);
    LOG.debug("Storing server info in {}", storageFilePath);
    rwLock.write(() -> writeToFile(serverInfoToStore, storageFilePath));
    LOG.debug("Stored server info");
  }

  public Optional<StoredServerInfo> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.ServerInfo.parser())))
      : Optional.empty());
  }

  private static Sonarlint.ServerInfo adapt(ServerStatusInfo serverStatus, @Nullable Boolean isMQRMode) {
    var serverInfoBuilder = Sonarlint.ServerInfo.newBuilder().setVersion(serverStatus.getVersion());
    if (isMQRMode != null) {
      serverInfoBuilder.setIsMqrMode(isMQRMode);
    }
    return serverInfoBuilder.build();
  }

  private static StoredServerInfo adapt(Sonarlint.ServerInfo serverInfo) {
    if (serverInfo.hasIsMqrMode()) {
      return new StoredServerInfo(Version.create(serverInfo.getVersion()), serverInfo.getIsMqrMode());
    } else {
      return new StoredServerInfo(Version.create(serverInfo.getVersion()), null);
    }
  }

}
