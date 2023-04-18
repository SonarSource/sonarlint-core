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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;

import static org.sonarsource.sonarlint.core.serverconnection.ServerConnection.computeConnectionStorageRoot;
import static org.sonarsource.sonarlint.core.serverconnection.ServerConnection.computeProjectsStorageRoot;

public class StorageFacade {

  private Path globalStorageRoot;

  public void initialize(Path globalStorageRoot) {
    this.globalStorageRoot = globalStorageRoot;
  }

  public ProjectStorage projectsStorageFacade(String connectionId) {
    var connectionStorageRoot = computeConnectionStorageRoot(globalStorageRoot, connectionId);
    var projectsStorageRoot = computeProjectsStorageRoot(connectionStorageRoot);
    return new ProjectStorage(projectsStorageRoot);
  }
}
