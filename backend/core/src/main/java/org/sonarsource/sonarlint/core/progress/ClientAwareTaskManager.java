/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.progress;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.progress.TaskManager;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;

public class ClientAwareTaskManager extends TaskManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintRpcClient client;

  public ClientAwareTaskManager(SonarLintRpcClient client) {
    this.client = client;
  }

  @Override
  protected void startProgress(@Nullable String configurationScopeId, UUID taskId, String title, @Nullable String message, boolean indeterminate, boolean cancellable,
    SonarLintCancelMonitor cancelMonitor) {
    try {
      client.startProgress(new StartProgressParams(taskId.toString(), configurationScopeId, title, message, indeterminate, cancellable)).get();
    } catch (InterruptedException e) {
      LOG.error("The progress report for the '" + title + "' was interrupted", e);
      Thread.currentThread().interrupt();
      throw new CanceledException();
    } catch (ExecutionException e) {
      LOG.error("The client was unable to start progress, cause:", e);
      super.startProgress(configurationScopeId, taskId, title, message, indeterminate, cancellable, cancelMonitor);
    }
  }

  @Override
  protected ProgressMonitor createProgress(UUID taskId, SonarLintCancelMonitor cancelMonitor) {
    return new ClientAwareProgressMonitor(client, taskId, cancelMonitor);
  }
}
