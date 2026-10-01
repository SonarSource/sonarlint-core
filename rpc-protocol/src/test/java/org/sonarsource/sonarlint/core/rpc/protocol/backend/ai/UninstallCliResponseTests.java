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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UninstallCliResponseTests {
  @Test
  void serializes_all_statuses_and_captured_output() {
    var gson = new Gson();
    for (var status : UninstallCliResponse.Status.values()) {
      var response = new UninstallCliResponse(status, "/home/space ' user/sonar", 0, "stdout\n", "stderr\n", List.of("warning"));

      var json = gson.toJson(response);
      var roundTrip = gson.fromJson(json, UninstallCliResponse.class);

      assertThat(roundTrip.getStatus()).isEqualTo(status);
      assertThat(roundTrip.getExecutablePath()).isEqualTo(response.getExecutablePath());
      assertThat(roundTrip.getResetExitCode()).isZero();
      assertThat(roundTrip.getStdout()).isEqualTo("stdout\n");
      assertThat(roundTrip.getStderr()).isEqualTo("stderr\n");
      assertThat(roundTrip.getDiagnostics()).containsExactly("warning");
    }
  }

  @Test
  void copies_diagnostics_and_rejects_null_output() {
    var messages = new ArrayList<>(List.of("notice"));
    var response = new UninstallCliResponse(UninstallCliResponse.Status.NOT_AVAILABLE, null, null, "", "", messages);
    messages.add("later");

    assertThat(response.getDiagnostics()).containsExactly("notice");
    assertThatThrownBy(() -> response.getDiagnostics().add("mutation")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new UninstallCliResponse(UninstallCliResponse.Status.NOT_AVAILABLE, null, null, null, "", List.of()))
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  void keeps_existing_state_constructor_defaulting_to_uninstall_unavailable() {
    var state = new SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNKNOWN, "/sonar", "1.9.0", null, null);

    assertThat(state.isUninstallAvailable()).isFalse();
    assertThat(new Gson().toJson(state)).contains("\"uninstallAvailable\":false");
    assertThat(new SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNKNOWN, "/sonar", "1.9.0", null, null, true)
      .isUninstallAvailable()).isTrue();
  }
}
