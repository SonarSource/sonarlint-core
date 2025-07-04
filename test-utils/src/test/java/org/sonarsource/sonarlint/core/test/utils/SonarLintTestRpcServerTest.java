/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarLintTestRpcServerTest {

  @Test
  void it_should_throw_an_assertion_exception_when_telemetry_file_does_not_exist(@TempDir Path userHome) {
    var clientLauncher = mock(ClientJsonRpcLauncher.class);
    var rpcServer = mock(SonarLintRpcServer.class);
    when(rpcServer.initialize(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(clientLauncher.getServerProxy()).thenReturn(rpcServer);
    var sonarLintTestRpcServer = new SonarLintTestRpcServer(mock(BackendJsonRpcLauncher.class), clientLauncher);
    sonarLintTestRpcServer
      .initialize(
        new InitializeParams(null, new TelemetryClientConstantAttributesDto("product", null, null, null, null), null, null, Set.of(), Paths.get(""), Paths.get(""), null, null,
          null, null, null, null, null, userHome.toString(), null, false, null, false, null))
      .join();

    var throwable = catchThrowable(sonarLintTestRpcServer::telemetryFileContent);

    assertThat(throwable).isInstanceOf(AssertionError.class);
  }

}
