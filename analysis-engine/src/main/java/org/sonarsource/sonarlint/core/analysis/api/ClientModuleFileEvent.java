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
package org.sonarsource.sonarlint.core.analysis.api;

import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

public class ClientModuleFileEvent {
  private final ClientInputFile target;

  private final ModuleFileEvent.Type type;

  private ClientModuleFileEvent(ClientInputFile target, ModuleFileEvent.Type type) {
    this.target = target;
    this.type = type;
  }

  public static ClientModuleFileEvent of(ClientInputFile target, ModuleFileEvent.Type type) {
    return new ClientModuleFileEvent(target, type);
  }

  public ClientInputFile target() {
    return target;
  }

  public ModuleFileEvent.Type type() {
    return type;
  }
}
