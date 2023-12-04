/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;

public class ModuleRegistry {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConcurrentHashMap<Object, ModuleContainer> moduleContainersByKey = new ConcurrentHashMap<>();
  private final SpringComponentContainer parent;

  public ModuleRegistry(SpringComponentContainer parent, @Nullable ClientModulesProvider modulesProvider) {
    this.parent = parent;
    if (modulesProvider != null) {
      modulesProvider.getModules().forEach(this::registerModule);
    }
  }

  public ModuleContainer registerModule(ClientModuleInfo moduleInfo) {
    return moduleContainersByKey.computeIfAbsent(moduleInfo.key(), id -> createContainer(id, moduleInfo.fileSystem()));
  }

  private ModuleContainer createContainer(Object moduleKey, @Nullable ClientModuleFileSystem clientFileSystem) {
    LOG.debug("Creating container for module '" + moduleKey + "'");
    var moduleContainer = new ModuleContainer(parent, false);
    if (clientFileSystem != null) {
      moduleContainer.add(clientFileSystem);
    }
    moduleContainer.startComponents();
    return moduleContainer;
  }

  public ModuleContainer createTransientContainer(Iterable<ClientInputFile> filesToAnalyze) {
    LOG.debug("Creating transient module container");
    var moduleContainer = new ModuleContainer(parent, true);
    moduleContainer.add(new TransientModuleFileSystem(filesToAnalyze));
    moduleContainer.startComponents();
    return moduleContainer;
  }

  public void unregisterModule(Object moduleKey) {
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
  public ModuleContainer getContainerFor(Object moduleKey) {
    return moduleContainersByKey.get(moduleKey);
  }
}
