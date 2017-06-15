/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteModule;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module;

public class AllModulesReader implements Supplier<Map<String, RemoteModule>> {
  private final StorageReader storageReader;

  public AllModulesReader(StorageReader storageReader) {
    this.storageReader = storageReader;
  }

  @Override
  public Map<String, RemoteModule> get() {
    Map<String, RemoteModule> results = new HashMap<>();
    ModuleList readModuleListFromStorage = storageReader.readModuleList();
    Map<String, Module> modulesByKey = readModuleListFromStorage.getModulesByKeyMap();
    for (Map.Entry<String, Module> entry : modulesByKey.entrySet()) {
      results.put(entry.getKey(), new DefaultRemoteModule(entry.getValue()));
    }
    return results;
  }
}
