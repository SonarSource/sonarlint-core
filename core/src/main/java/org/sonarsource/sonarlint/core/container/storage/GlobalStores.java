/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;

import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;

public class GlobalStores {
  private final ServerProjectsStore serverProjectsStore;
  private final ServerInfoStore serverInfoStore;
  private final StorageStatusStore storageStatusStore;
  private final Path storageRoot;
  private final ServerStorage globalStorage;

  public GlobalStores(ConnectedGlobalConfiguration globalConfiguration) {
    storageRoot = globalConfiguration.getStorageRoot().resolve(encodeForFs(globalConfiguration.getConnectionId()));
    var globalStorageRoot = storageRoot.resolve("global");
    globalStorage = new ServerStorage(globalStorageRoot);
    this.serverProjectsStore = new ServerProjectsStore(globalStorage);
    this.serverInfoStore = new ServerInfoStore(globalStorage);
    this.storageStatusStore = new StorageStatusStore(globalStorage);
  }

  public ServerStorage getGlobalStorage() {
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
    FileUtils.deleteRecursively(storageRoot);
  }
}
