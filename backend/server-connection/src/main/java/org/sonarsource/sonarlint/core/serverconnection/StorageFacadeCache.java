/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

// using a static cache is required for compatibility between the legacy engines and the new backend, can be removed when dropping engines
public class StorageFacadeCache {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final StorageFacadeCache uniqueFacade = new StorageFacadeCache();

  public static StorageFacadeCache get() {
    return uniqueFacade;
  }

  private final Map<Path, StorageFacade> facadePerRootPath = new ConcurrentHashMap<>();

  public StorageFacade getOrCreate(Path globalStorageRoot, Path workDir) {
    return facadePerRootPath.computeIfAbsent(globalStorageRoot, k -> {
      LOG.debug("Creating a new StorageFacade for storageRoot={} and workDir={}", globalStorageRoot, workDir);
      return new StorageFacade(globalStorageRoot, workDir);
    });
  }

  public void close(Path globalStorageRoot) {
    var removed = facadePerRootPath.remove(globalStorageRoot);
    if (removed != null) {
      // close the storage only once
      removed.close();
    }
  }
}
