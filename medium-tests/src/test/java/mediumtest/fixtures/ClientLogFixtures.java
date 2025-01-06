/*
 * SonarLint Core - Medium Tests
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
package mediumtest.fixtures;

import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

public class ClientLogFixtures {
  public static void verifyClientLog(SonarLintBackendFixture.FakeSonarLintRpcClient client, LogLevel logLevel, String message) {
    var argumentCaptor = ArgumentCaptor.forClass(LogParams.class);
    verify(client, atLeast(1)).log(argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues())
      .anySatisfy(logParam -> {
        assertThat(logParam.getMessage()).contains(message);
        assertThat(logParam.getLevel()).isEqualTo(logLevel);
      });

  }
}
