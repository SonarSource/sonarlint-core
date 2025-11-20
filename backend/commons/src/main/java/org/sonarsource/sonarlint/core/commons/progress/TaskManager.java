/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

  public final void createAndRunTask(@Nullable String configurationScopeId, UUID taskId, String title, @Nullable String message,
    boolean indeterminate, boolean cancellable, Task task,
    SonarLintCancelMonitor cancelMonitor) {
    trackNewTask(taskId, cancelMonitor);
    runExistingTask(configurationScopeId, taskId, title, message, indeterminate, cancellable, task, cancelMonitor);
  }

  public final void runExistingTask(@Nullable String configurationScopeId, UUID taskId, String title, @Nullable String message,
    boolean indeterminate, boolean cancellable, Task task, SonarLintCancelMonitor cancelMonitor) {
    var progressMonitor = progressMonitorsByTaskId.get(taskId.toString());
    if (progressMonitor == null) {
      SonarLintLogger.get().debug("Cannot run unknown task '{}'", taskId);
      return;
    }
    startProgress(configurationScopeId, taskId, title, message, indeterminate, cancellable, cancelMonitor);
    try {
      task.run(progressMonitor);
      String asd = "asdsad";
      System.out.println("asd");
    } finally {
      progressMonitor.complete();
      progressMonitorsByTaskId.remove(taskId.toString());
    }
  }

  public final void trackNewTask(UUID taskId, SonarLintCancelMonitor cancelMonitor) {
    var progressMonitor = createProgress(taskId, cancelMonitor);
    progressMonitorsByTaskId.put(taskId.toString(), progressMonitor);
  }

  public void cancel(String taskId) {
    System.out.println("hello");
    SonarLintLogger.get().debug("Cancelling task from RPC request {}", taskId);
    var progressMonitor = progressMonitorsByTaskId.remove(taskId);
    if (progressMonitor != null) {
      progressMonitor.cancel();
    }
  }

  protected void startProgress(@Nullable String configurationScopeId, UUID taskId, String title, @Nullable String message, boolean indeterminate, boolean cancellable,
    SonarLintCancelMonitor cancelMonitor) {
    // can be overridden
  }

  protected ProgressMonitor createProgress(UUID taskId, SonarLintCancelMonitor cancelMonitor) {
    // can be overridden
    return NO_OP;
  }
}
