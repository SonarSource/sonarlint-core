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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.progress.CanceledException;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientAwareTaskManagerTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void it_should_throw_when_interrupted() throws InterruptedException {
    var client = mock(SonarLintRpcClient.class);
    when(client.startProgress(any())).thenReturn(new CompletableFuture<>());
    var taskManager = new ClientAwareTaskManager(client);
    var caughtException = new AtomicReference<Exception>();
    var thread = new Thread(() -> {
      try {
        taskManager.createAndRunTask("configScopeId", UUID.randomUUID(), "Title", null, true, true, progressIndicator -> {
        }, new SonarLintCancelMonitor());
      } catch (Exception e) {
        caughtException.set(e);
      }
    });
    thread.start();
    Thread.sleep(500);

    thread.interrupt();

    await().untilAsserted(() -> assertThat(caughtException.get()).isInstanceOf(CanceledException.class));
  }

}
