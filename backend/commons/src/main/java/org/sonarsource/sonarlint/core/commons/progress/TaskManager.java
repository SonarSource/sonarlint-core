/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.progress;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class TaskManager {
  private static final ProgressMonitor NO_OP = new NoOpProgressMonitor();
  private final ConcurrentHashMap<String, ProgressMonitor> progressMonitorsByTaskId = new ConcurrentHashMap<>();

  public final void runTask(@Nullable String configurationScopeId, UUID taskId, String title, @Nullable String message, boolean indeterminate, boolean cancellable, Task task,
    SonarLintCancelMonitor cancelMonitor) {
    var progressMonitor = startProgress(configurationScopeId, taskId, title, message, indeterminate, cancellable, cancelMonitor);
    progressMonitorsByTaskId.put(taskId.toString(), progressMonitor);
    try {
      task.run(progressMonitor);
    } finally {
      progressMonitor.complete();
      progressMonitorsByTaskId.remove(taskId.toString());
    }
  }

  public void cancel(String taskId) {
    SonarLintLogger.get().debug("Cancelling task from RPC request {}", taskId);
    var progressMonitor = progressMonitorsByTaskId.remove(taskId);
    if (progressMonitor != null) {
      progressMonitor.cancel();
    }
  }

  protected ProgressMonitor startProgress(@Nullable String configurationScopeId, UUID taskId, String title, @Nullable String message, boolean indeterminate, boolean cancellable,
    SonarLintCancelMonitor cancelMonitor) {
    // can be overridden
    return NO_OP;
  }
}
