/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.progress;

import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;

public class RpcProgressMonitor extends ProgressMonitor {
  private final SonarLintCancelMonitor cancelMonitor;
  private final String configurationScopeId;
  private final TaskManager taskManager;
  private final UUID taskId;

  public RpcProgressMonitor(SonarLintRpcClient client, SonarLintCancelMonitor cancelMonitor, String configurationScopeId, UUID taskId) {
    super(null);
    this.cancelMonitor = cancelMonitor;
    this.configurationScopeId = configurationScopeId;
    this.taskManager = new TaskManager(client);
    this.taskId = taskId;
  }

  @Override
  public void startTask(String message, Runnable task) {
    taskManager.startTask(configurationScopeId, taskId, message, null, true, false, notifier -> task.run());
  }

  @Override
  public boolean isCanceled() {
    return cancelMonitor.isCanceled();
  }
}
