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

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.UserPaths;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.repository.ServerIssuesRepository;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class ConnectionStorageCleanupHandler {
  private final ServerIssuesRepository serverIssuesRepository;
  private final Path globalStorageRoot;

  public ConnectionStorageCleanupHandler(ServerIssuesRepository serverIssuesRepository, UserPaths userPaths) {
    this.serverIssuesRepository = serverIssuesRepository;
    this.globalStorageRoot = userPaths.getStorageRoot();
  }

  @EventListener
  public void handleEvent(ConnectionConfigurationRemovedEvent connectionConfigurationRemovedEvent) {
    var removedConnectionId = connectionConfigurationRemovedEvent.getRemovedConnectionId();
    serverIssuesRepository.close(removedConnectionId);
    var connectionStorageRoot = globalStorageRoot.resolve(encodeForFs(removedConnectionId));
    FileUtils.deleteRecursively(connectionStorageRoot);
  }
}

