/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerPathProviderTests {


  @Test
  void new_auth_path_for_9_7_version() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.7", 1234, "My IDE");

    assertThat(serverPath).isEqualTo("baseUrl/sonarlint/auth?port=1234&ideName=My IDE");
  }

  @Test
  void new_auth_path_for_version_greater_than_9_7() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.8", 1234, "My IDE");

    assertThat(serverPath).isEqualTo("baseUrl/sonarlint/auth?port=1234&ideName=My IDE");
  }

  @Test
  void profile_token_generation_path_for_version_lower_than_9_7() {
    var serverPath = ServerPathProvider.buildServerPath("baseUrl", "9.6", 1234, "My IDE");

    assertThat(serverPath).isEqualTo("baseUrl/account/security?port=1234&ideName=My IDE");
  }

}
