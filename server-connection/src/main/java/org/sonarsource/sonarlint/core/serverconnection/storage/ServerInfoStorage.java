/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil.writeToFile;

public class ServerInfoStorage {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String SERVER_INFO_PB = "server_info.pb";

  private final Path rootPath;
  private final RWLock rwLock = new RWLock();

  public ServerInfoStorage(Path rootPath) {
    this.rootPath = rootPath;
  }

  public void store(ServerInfo serverInfo) {
    var serverInfoFilePath = getServerInfoFilePath();
    FileUtils.mkdirs(serverInfoFilePath.getParent());
    var serverInfoToStore = adapt(serverInfo);
    LOG.debug("Storing server info in {}", serverInfoFilePath);
    rwLock.write(() -> writeToFile(serverInfoToStore, serverInfoFilePath));
  }

  public Optional<StoredServerInfo> getServerInfo() {
    var filePath = getServerInfoFilePath();
    return rwLock.read(() -> Files.exists(filePath) ? Optional.of(adapt(ProtobufUtil.readFile(filePath, Sonarlint.ServerInfo.parser()))) : Optional.empty());
  }

  private Path getServerInfoFilePath() {
    return rootPath.resolve(SERVER_INFO_PB);
  }

  private static Sonarlint.ServerInfo adapt(ServerInfo serverInfo) {
    return Sonarlint.ServerInfo.newBuilder().setVersion(serverInfo.getVersion()).build();
  }

  private static StoredServerInfo adapt(Sonarlint.ServerInfo serverInfo) {
    return new StoredServerInfo(Version.create(serverInfo.getVersion()));
  }
}
