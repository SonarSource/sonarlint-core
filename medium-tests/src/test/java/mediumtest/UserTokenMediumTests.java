/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.usertoken.RevokeTokenParams;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;

class UserTokenMediumTests {

  private SonarLintTestRpcServer backend;
  private ServerFixture.Server server;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  @Test
  void test_revoke_user_token() {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);
    var tokenName = "tokenNameTest";
    server = ServerFixture.newSonarQubeServer().withToken(tokenName).start();

    assertThat(backend
      .getUserTokenService()
      .revokeToken(new RevokeTokenParams(server.baseUrl(), tokenName, "tokenValueTest")))
      .succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  void test_revoke_unknown_user_token() {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);
    var tokenName = "tokenNameTest";
    server = ServerFixture.newSonarQubeServer().start();

    assertThat(backend
      .getUserTokenService()
      .revokeToken(new RevokeTokenParams(server.baseUrl(), tokenName, "tokenValueTest")))
      .failsWithin(Duration.ofSeconds(5));
  }

}
