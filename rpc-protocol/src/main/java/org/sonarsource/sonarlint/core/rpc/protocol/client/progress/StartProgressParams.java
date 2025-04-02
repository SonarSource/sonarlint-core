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
package org.sonarsource.sonarlint.core.rpc.protocol.client.progress;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.progress.CancelTaskParams;

public class StartProgressParams {
  /**
   * The task ID is a unique identifier, generated by the backend that identifies a long-running task.
   * The same ID needs to be re-used when reporting progress to the client for a given task.
   * This ID can be used by the client to cancel the task by using {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.progress.TaskProgressRpcService#cancelTask(CancelTaskParams)}
   */
  private final String taskId;
  /**
   * Configuration scope on which to report the progress.
   * Could be null for a task that does not relate to a configuration scope in particular,
   * or if several configuration scopes are involved.
   */
  private final String configurationScopeId;
  private final String title;
  private final String message;
  private final boolean indeterminate;
  private final boolean cancellable;

  public StartProgressParams(String taskId, @Nullable String configurationScopeId, String title, @Nullable String message, boolean indeterminate,
    boolean cancellable) {
    this.taskId = taskId;
    this.configurationScopeId = configurationScopeId;
    this.title = title;
    this.message = message;
    this.indeterminate = indeterminate;
    this.cancellable = cancellable;
  }

  public String getTaskId() {
    return taskId;
  }

  @CheckForNull
  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public String getTitle() {
    return title;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  public boolean isIndeterminate() {
    return indeterminate;
  }

  public boolean isCancellable() {
    return cancellable;
  }
}
