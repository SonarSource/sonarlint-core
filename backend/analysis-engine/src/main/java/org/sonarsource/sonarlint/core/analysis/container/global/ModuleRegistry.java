/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;

public class ModuleRegistry {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConcurrentHashMap<String, ModuleContainer> moduleContainersByKey = new ConcurrentHashMap<>();
  private final SpringComponentContainer parent;

  public ModuleRegistry(SpringComponentContainer parent, Supplier<List<ClientModuleInfo>> modulesProvider) {
    this.parent = parent;
    modulesProvider.get().forEach(this::registerModule);
  }

  public void registerModule(ClientModuleInfo moduleInfo) {
    moduleContainersByKey.computeIfAbsent(moduleInfo.key(), id -> createContainer(id, moduleInfo.fileSystem()));
  }

  private ModuleContainer createContainer(Object moduleKey, @Nullable ClientModuleFileSystem clientFileSystem) {
    LOG.debug("Creating container for module '" + moduleKey + "'");
    var moduleContainer = new ModuleContainer(parent);
    if (clientFileSystem != null) {
      moduleContainer.add(clientFileSystem);
    }
    moduleContainer.startComponents();
    return moduleContainer;
  }

  public void unregisterModule(String moduleKey) {
    if (!moduleContainersByKey.containsKey(moduleKey)) {
      // can this happen ?
      return;
    }
    var moduleContainer = moduleContainersByKey.remove(moduleKey);
    moduleContainer.stopComponents();
  }

  public void stopAll() {
    moduleContainersByKey.values().forEach(SpringComponentContainer::stopComponents);
    moduleContainersByKey.clear();
  }

  @CheckForNull
  public ModuleContainer getContainerFor(String moduleKey) {
    return moduleContainersByKey.get(moduleKey);
  }
}
