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

import mockwebserver3.MockResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.usertoken.RevokeTokenParams;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;

class UserTokenMediumTests {
  @RegisterExtension
  private final MockWebServerExtension mockWebServerExtension = new MockWebServerExtension();
  
  private SonarLintBackendImpl backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  /**
   *  INFO: Check {@link its.SonarCloudTests#test_revoke_token()} and
   *        {@link its.SonarQubeCommunityEditionTests#test_revoke_token()} for actual (non-mocked) tests against SQ/SC.
   */
  @Test
  void test_revoke_user_token() {
    var fakeClient = newFakeClient().build();
    backend = newBackend().build(fakeClient);

    mockWebServerExtension.addResponse("/api/user_tokens/revoke",
      new MockResponse().setResponseCode(200));

    assertThat(backend
      .getUserTokenService()
      .revokeToken(new RevokeTokenParams(mockWebServerExtension.url("/"), "tokenNameTest", "tokenValueTest")))
      .succeedsWithin(Duration.ofSeconds(5));
  }
}
