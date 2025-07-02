/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.progress;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class CancelTaskParams {
  private final String taskId;
  @Nullable
  private final String configurationScopeId;

  public CancelTaskParams(String taskId, @Nullable String configurationScopeId) {
    this.taskId = taskId;
    this.configurationScopeId = configurationScopeId;
  }

  public String getTaskId() {
    return taskId;
  }

  @CheckForNull
  public String getConfigurationScopeId() {
    return configurationScopeId;
  }
}
