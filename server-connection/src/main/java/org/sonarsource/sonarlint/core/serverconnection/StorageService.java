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
import org.sonarsource.sonarlint.core.commons.Binding;

public class StorageService {

  private StorageFacade storageFacade;

  public void initialize(Path globalStorageRoot, Path workDir) {
    storageFacade = StorageFacadeCache.get().getOrCreate(globalStorageRoot, workDir);
  }

  public StorageFacade getStorageFacade() {
    return storageFacade;
  }

  public ConnectionStorage connection(String connectionId) {
    return storageFacade.connection(connectionId);
  }

  public SonarProjectStorage binding(Binding binding) {
    return storageFacade.connection(binding.getConnectionId()).project(binding.getSonarProjectKey());
  }

  public void close() {
    StorageFacadeCache.get().close(storageFacade);
  }
}
