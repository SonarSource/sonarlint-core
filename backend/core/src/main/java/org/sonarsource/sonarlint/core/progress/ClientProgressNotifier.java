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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressUpdateNotification;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;

public class ClientProgressNotifier implements ProgressNotifier {
  private final SonarLintRpcClient client;
  private final UUID taskId;

  public ClientProgressNotifier(SonarLintRpcClient client, UUID taskId) {
    this.client = client;
    this.taskId = taskId;
  }

  @Override
  public void notify(@Nullable String message, @Nullable Integer percentage) {
    client.reportProgress(new ReportProgressParams(taskId.toString(), new ProgressUpdateNotification(message, percentage)));
  }
}
