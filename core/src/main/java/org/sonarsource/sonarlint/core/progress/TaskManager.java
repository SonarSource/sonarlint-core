/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.progress;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ProgressEndNotification;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class TaskManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final SonarLintClient client;

  public TaskManager(SonarLintClient client) {
    this.client = client;
  }

  public CompletableFuture<Void> startTask(@Nullable String configurationScopeId, String title, @Nullable String message, boolean indeterminate, boolean cancellable,
    Consumer<ProgressNotifier> task) {
    var taskId = UUID.randomUUID();
    return client.startProgress(new StartProgressParams(taskId.toString(), configurationScopeId, title, message, indeterminate, cancellable))
      .thenAccept(nothing -> {
        try {
          task.accept(new ClientProgressNotifier(client, taskId));
        } catch (Exception e) {
          LOG.error("Error running task '" + title + "'", e);
        } finally {
          client.reportProgress(new ReportProgressParams(taskId.toString(), new ProgressEndNotification()));
        }
      })
      .exceptionally(error -> {
        LOG.error("The client was unable to start progress, cause:", error);
        try {
          task.accept(new NoOpProgressNotifier());
        } catch (Exception e) {
          LOG.error("Error running task '" + title + "'", e);
        }
        return null;
      });
  }
}
