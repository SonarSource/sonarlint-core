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
package org.sonarsource.sonarlint.core.analysis.command;

import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleFileEventNotifier;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

public class NotifyModuleEventCommand implements Command<Void> {
  private final Object moduleKey;
  private final ClientModuleFileEvent event;

  public NotifyModuleEventCommand(Object moduleKey, ClientModuleFileEvent event) {
    this.moduleKey = moduleKey;
    this.event = event;
  }

  @Override
  public Void execute(ModuleRegistry moduleRegistry, ProgressMonitor progressMonitor) {
    var moduleContainer = moduleRegistry.getContainerFor(moduleKey);
    if (moduleContainer != null) {
      moduleContainer.getComponentByType(ModuleFileEventNotifier.class).fireModuleFileEvent(event);
    }
    return null;
  }
}
