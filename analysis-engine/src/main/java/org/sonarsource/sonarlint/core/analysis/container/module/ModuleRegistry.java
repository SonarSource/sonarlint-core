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

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;

public class ModuleRegistry {
  private static final Logger LOG = Loggers.get(ModuleRegistry.class);

  private final ConcurrentHashMap<String, ModuleContainer> moduleContainersById = new ConcurrentHashMap<>();
  private final GlobalAnalysisContainer parent;
  private final ClientFileSystem clientFs;

  public ModuleRegistry(GlobalAnalysisContainer parent, ClientFileSystem clientFs) {
    this.parent = parent;
    this.clientFs = clientFs;
  }

  public ModuleContainer registerModule(String moduleId) {
    return moduleContainersById.computeIfAbsent(moduleId, id -> createContainer(id, new DefaultModuleFileSystem(id, clientFs)));
  }

  private ModuleContainer createContainer(String moduleId, ModuleFileSystem moduleFileSystem) {
    LOG.debug("Creating container for module '" + moduleId + "'");
    ModuleContainer moduleContainer = new ModuleContainer(parent, false);
    moduleContainer.add(moduleFileSystem);
    moduleContainer.startComponents();
    return moduleContainer;
  }

  public ModuleContainer createTranscientContainer(Iterable<ClientInputFile> filesToAnalyze) {
    LOG.debug("Creating transcient module container");
    ModuleContainer moduleContainer = new ModuleContainer(parent, true);
    moduleContainer.add(new TranscientModuleFileSystem(filesToAnalyze));
    moduleContainer.startComponents();
    return moduleContainer;
  }

  public void unregisterModule(Object moduleKey) {
    ModuleContainer moduleContainer = moduleContainersById.remove(moduleKey);
    if (moduleContainer != null) {
      moduleContainer.stopComponents();
    }
  }

  public void stopAll() {
    moduleContainersById.values().forEach(ComponentContainer::stopComponents);
    moduleContainersById.clear();
  }

  @CheckForNull
  public ModuleContainer getContainerFor(String moduleId) {
    return moduleContainersById.get(moduleId);
  }
}
