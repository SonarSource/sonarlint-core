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
package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.List;
import java.util.Optional;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileListener;

public class ModuleFileEventNotifier {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final List<ModuleFileListener> listeners;
  private final ModuleInputFileBuilder inputFileBuilder;

  public ModuleFileEventNotifier(Optional<List<ModuleFileListener>> listeners, ModuleInputFileBuilder inputFileBuilder) {
    this.listeners = listeners.orElse(List.of());
    this.inputFileBuilder = inputFileBuilder;
  }

  public void fireModuleFileEvent(ClientModuleFileEvent event) {
    ModuleFileEvent apiEvent = DefaultModuleFileEvent.of(inputFileBuilder.create(event.target()), event.type());
    listeners.forEach(l -> tryFireModuleFileEvent(l, apiEvent));
  }

  private static void tryFireModuleFileEvent(ModuleFileListener listener, ModuleFileEvent event) {
    try {
      listener.process(event);
    } catch (Exception e) {
      LOG.error("Error processing file event", e);
    }
  }
}
