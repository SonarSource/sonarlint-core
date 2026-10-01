/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UninstallCliResponseTests {
  @Test
  void serializes_status_output_and_optional_message() {
    for (var status : UninstallCliResponse.Status.values()) {
      var response = new UninstallCliResponse(status, "warning", "stderr", null);
      var gson = new Gson();
      var roundTrip = gson.fromJson(gson.toJson(response), UninstallCliResponse.class);

      assertThat(roundTrip.getStatus()).isEqualTo(status);
      assertThat(roundTrip.getStdout()).isEqualTo("warning");
      assertThat(roundTrip.getStderr()).isEqualTo("stderr");
      assertThat(roundTrip.getMessage()).isNull();
    }
  }

  @Test
  void keeps_existing_state_constructor_defaulting_to_uninstall_unavailable() {
    var state = new SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNKNOWN, "/sonar", "1.9.0", null, null);

    assertThat(state.isUninstallAvailable()).isFalse();
    assertThat(new Gson().toJson(state)).contains("\"uninstallAvailable\":false");
  }
}
