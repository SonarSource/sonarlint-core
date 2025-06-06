/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SonarLintRpcClientImplTest {
  @Test
  void it_should_print_notification_handling_errors_to_the_client_logs() {
    var fakeClientDelegate = mock(SonarLintRpcClientDelegate.class);
    var argumentCaptor = ArgumentCaptor.forClass(LogParams.class);

    var rpcClient = new SonarLintRpcClientImpl(fakeClientDelegate, Runnable::run, Runnable::run);

    rpcClient.notify(() -> {
      throw new IllegalStateException("Kaboom");
    });

    verify(fakeClientDelegate).log(argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues())
      .anySatisfy(logParam -> {
        assertThat(logParam.getMessage()).contains("Error when handling a notification");
        assertThat(logParam.getStackTrace()).contains("java.lang.IllegalStateException: Kaboom");
      });
  }

  @Test
  void it_should_match_project_branch() throws ExecutionException, InterruptedException {
    var fakeClientDelegate = mock(SonarLintRpcClientDelegate.class);
    var rpcClient = new SonarLintRpcClientImpl(fakeClientDelegate, Runnable::run, Runnable::run);
    var params = new MatchProjectBranchParams("configScopeId", "branch");

    var response = rpcClient.matchProjectBranch(params);

    assertThat(params.getConfigurationScopeId()).isEqualTo("configScopeId");
    assertThat(params.getServerBranchToMatch()).isEqualTo("branch");
    assertThat(response.get().isBranchMatched()).isTrue();
  }
}
