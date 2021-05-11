/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.client.api.common.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;

public abstract class AbstractSonarLintEngine implements SonarLintEngine {
  protected abstract ModuleRegistry getModuleRegistry();

  @Override
  public void declareModule(ModuleInfo module) {
    getModuleRegistry().registerModule(module);
  }

  @Override
  public void stopModule(Object moduleKey) {
    getModuleRegistry().unregisterModule(moduleKey);
  }

  @Override
  public void fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event) {
    ComponentContainer moduleContainer = getModuleRegistry().getContainerFor(moduleKey);
    if (moduleContainer != null) {
      moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
    }
  }
}
