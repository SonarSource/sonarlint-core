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
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressEndNotification;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressUpdateNotification;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;

public class ClientAwareProgressMonitor implements ProgressMonitor {
  private final SonarLintRpcClient client;
  private final UUID taskId;
  private final SonarLintCancelMonitor cancelMonitor;

  public ClientAwareProgressMonitor(SonarLintRpcClient client, UUID taskId, SonarLintCancelMonitor cancelMonitor) {
    this.client = client;
    this.taskId = taskId;
    this.cancelMonitor = cancelMonitor;
  }

  @Override
  public void notifyProgress(@Nullable String message, @Nullable Integer percentage) {
    client.reportProgress(new ReportProgressParams(taskId.toString(), new ProgressUpdateNotification(message, percentage)));
  }

  @Override
  public boolean isCanceled() {
    return cancelMonitor.isCanceled();
  }

  @Override
  public void cancel() {
    cancelMonitor.cancel();
  }

  @Override
  public void complete() {
    client.reportProgress(new ReportProgressParams(taskId.toString(), new ProgressEndNotification()));
  }
}
