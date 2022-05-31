/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.serverconnection.storage.ConnectionStorageFolder;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerProjectsStore;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageStatusStore;

public class GlobalStores {
  private final ServerProjectsStore serverProjectsStore;
  private final ServerInfoStore serverInfoStore;
  private final StorageStatusStore storageStatusStore;
  private final Path connectionStorageRoot;
  private final ConnectionStorageFolder globalStorage;

  public GlobalStores(Path connectionStorageRoot) {
    this.connectionStorageRoot = connectionStorageRoot;
    var globalStorageRoot = connectionStorageRoot.resolve("global");
    globalStorage = new ConnectionStorageFolder(globalStorageRoot);
    this.serverProjectsStore = new ServerProjectsStore(globalStorage);
    this.serverInfoStore = new ServerInfoStore(globalStorage);
    this.storageStatusStore = new StorageStatusStore(globalStorage);
  }

  public ConnectionStorageFolder getGlobalStorage() {
    return globalStorage;
  }

  public ServerProjectsStore getServerProjectsStore() {
    return serverProjectsStore;
  }

  public ServerInfoStore getServerInfoStore() {
    return serverInfoStore;
  }

  public StorageStatusStore getStorageStatusStore() {
    return storageStatusStore;
  }

  public void deleteAll() {
    FileUtils.deleteRecursively(connectionStorageRoot);
  }
}
