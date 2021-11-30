/*
 * SonarLint Core - Local Storage
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
package org.sonarsource.sonarlint.core.storage;

import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class ServerInfoStore {
  public static final String SERVER_INFO_PB = "server_info.pb";

  private final StorageFolder storageFolder;
  private final RWLock rwLock = new RWLock();

  public ServerInfoStore(StorageFolder storageFolder) {
    this.storageFolder = storageFolder;
  }

  public void store(Sonarlint.ServerInfos serverInfo) {
    rwLock.write(() -> storageFolder.writeAction(dest -> ProtobufUtil.writeToFile(serverInfo, dest.resolve(SERVER_INFO_PB))));
  }

  public Sonarlint.ServerInfos getAll() {
    return rwLock.read(() -> storageFolder.readAction(source -> ProtobufUtil.readFile(source.resolve(SERVER_INFO_PB), Sonarlint.ServerInfos.parser())));
  }
}
