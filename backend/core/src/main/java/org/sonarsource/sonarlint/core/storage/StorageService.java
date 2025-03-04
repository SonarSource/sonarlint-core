/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.storage;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.springframework.context.event.EventListener;

public class StorageService {
  private final Path globalStorageRoot;
  private final Path workDir;
  private final Map<String, ConnectionStorage> connectionStorageById = new ConcurrentHashMap<>();

  public StorageService(UserPaths userPaths) {
    this.globalStorageRoot = userPaths.getStorageRoot();
    this.workDir = userPaths.getWorkDir();
  }

  public ConnectionStorage connection(String connectionId) {
    return connectionStorageById.computeIfAbsent(connectionId, k -> new ConnectionStorage(globalStorageRoot, workDir, connectionId));
  }

  public SonarProjectStorage binding(Binding binding) {
    return connection(binding.connectionId()).project(binding.sonarProjectKey());
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationRemovedEvent connectionConfigurationRemovedEvent) {
    var removedConnectionId = connectionConfigurationRemovedEvent.getRemovedConnectionId();
    var connectionStorage = connection(removedConnectionId);
    connectionStorage.close();
    connectionStorage.delete();
  }

  @PreDestroy
  public void close() {
    connectionStorageById.values().forEach(ConnectionStorage::close);
  }

}
