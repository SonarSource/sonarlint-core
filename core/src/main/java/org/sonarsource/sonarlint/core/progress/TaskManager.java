/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressEndNotification;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class TaskManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintClient client;

  public TaskManager(SonarLintClient client) {
    this.client = client;
  }

  public void startTask(@Nullable String configurationScopeId, String title, @Nullable String message, boolean indeterminate, boolean cancellable,
    Consumer<ProgressNotifier> task) {
    var taskId = UUID.randomUUID();
    // fixme improve method structure
    try {
      client.startProgress(new StartProgressParams(taskId.toString(), configurationScopeId, title, message, indeterminate, cancellable)).get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      LOG.error("The client was unable to start progress, cause:", e);
      try {
        task.accept(new NoOpProgressNotifier());
      } catch (Exception ex) {
        LOG.error("Error running task '" + title + "'", ex);
      }
      return;
    }
    try {
      task.accept(new ClientProgressNotifier(client, taskId));
    } catch (Exception e) {
      LOG.error("Error running task '" + title + "'", e);
    } finally {
      client.reportProgress(new ReportProgressParams(taskId.toString(), new ProgressEndNotification()));
    }
  }
}
