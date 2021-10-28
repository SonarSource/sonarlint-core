/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ModuleInfo;
import org.sonarsource.sonarlint.core.analysis.api.ModulesProvider;
import org.sonarsource.sonarlint.core.analysis.container.ComponentContainer;

public class ModuleRegistry {
  private static final Logger LOG = Loggers.get(ModuleRegistry.class);

  private final Map<Object, ModuleContainer> modules = new HashMap<>();
  private final ComponentContainer parent;

  public ModuleRegistry(ComponentContainer parent, ModulesProvider modulesProvider) {
    this.parent = parent;
    if (modulesProvider != null) {
      modulesProvider.getModules().forEach(this::registerModule);
    }
  }

  public ModuleContainer registerModule(ModuleInfo module) {
    ModuleContainer moduleContainer = createContainer(module);
    modules.put(module.key(), moduleContainer);
    return moduleContainer;
  }

  public ModuleContainer createContainer(ModuleInfo module) {
    Object moduleKey = module.key();
    if (modules.containsKey(moduleKey)) {
      // this could happen if the creation is delayed while the engine is updating but the provider already returned the module
      LOG.info("Module container already started with key=" + moduleKey);
      return modules.get(moduleKey);
    }
    LOG.info("Creating container for module with key=" + moduleKey);
    ModuleContainer moduleContainer = new ModuleContainer(parent);
    ClientFileSystem clientFileSystem = module.fileSystem();
    if (clientFileSystem != null) {
      moduleContainer.add(clientFileSystem);
    }
    moduleContainer.startComponents();
    return moduleContainer;
  }

  public void unregisterModule(Object moduleKey) {
    if (!modules.containsKey(moduleKey)) {
      // can this happen ?
      return;
    }
    ModuleContainer moduleContainer = modules.remove(moduleKey);
    moduleContainer.stopComponents();
  }

  public void stopAll() {
    modules.values().forEach(ComponentContainer::stopComponents);
    modules.clear();
  }

  @CheckForNull
  public ModuleContainer getContainerFor(Object moduleKey) {
    return modules.get(moduleKey);
  }
}
