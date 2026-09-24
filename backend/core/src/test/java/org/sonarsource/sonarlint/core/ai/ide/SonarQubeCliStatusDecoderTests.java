/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState;

import static org.assertj.core.api.Assertions.assertThat;

class SonarQubeCliStatusDecoderTests {
  @ParameterizedTest
  @CsvSource({"enabled,true", "over_consumption,true", "not_entitled,false", "check_failed,false", "unknown,false", "disabled,false"})
  void should_use_vortex_entitlement_independently_of_missing_auth(String status, boolean expected) throws IOException {
    var decoded = SonarQubeCliStatusDecoder.decode("""
      {"vortex":{"applicable":true,"status":"%s"}}
      """.formatted(status));
    assertThat(decoded.vortexAvailable()).isEqualTo(expected);
    assertThat(decoded.authenticationStatus()).isEqualTo(CliAuthenticationStatus.UNKNOWN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"enabled", "over_consumption"})
  void should_decode_vortex_before_unauthenticated_early_return(String status) throws IOException {
    var decoded = SonarQubeCliStatusDecoder.decode("""
      {"auth":{"status":"unauthenticated"},"vortex":{"applicable":true,"status":"%s"}}
      """.formatted(status));
    assertThat(decoded.vortexAvailable()).isTrue();
    assertThat(decoded.authenticationStatus()).isEqualTo(CliAuthenticationStatus.UNAUTHENTICATED);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "null", "true", "[]", "{}", "{\"status\":\"enabled\"}",
    "{\"applicable\":null,\"status\":\"enabled\"}", "{\"applicable\":false,\"status\":\"enabled\"}",
    "{\"applicable\":\"true\",\"status\":\"enabled\"}", "{\"applicable\":1,\"status\":\"enabled\"}",
    "{\"applicable\":[],\"status\":\"enabled\"}", "{\"applicable\":true,\"status\":null}",
    "{\"applicable\":true,\"status\":true}", "{\"applicable\":true,\"status\":[\"enabled\"]}",
    "{\"applicable\":true,\"status\":{}}", "{\"applicable\":true,\"status\":\"ENABLED\"}"
  })
  void should_default_missing_or_malformed_vortex_fields_to_false(String vortex) throws IOException {
    var decoded = SonarQubeCliStatusDecoder.decode("{\"auth\":{\"token\":\"active\"},\"vortex\":" + vortex + "}");
    assertThat(decoded.vortexAvailable()).isFalse();
    assertThat(decoded.authenticationStatus()).isEqualTo(CliAuthenticationStatus.AUTHENTICATED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "[]", "{}", "{\"auth\":{\"token\":\"active\"}}"})
  void should_default_missing_vortex_to_false(String json) throws IOException {
    assertThat(SonarQubeCliStatusDecoder.decode(json).vortexAvailable()).isFalse();
  }

  @Test
  void should_default_unavailable_vortex_to_false() {
    assertThat(SonarQubeCliStatusDecoder.CliStatus.unavailable().vortexAvailable()).isFalse();
    assertThat(SonarQubeCliStatusDecoder.CliStatus.unknown().vortexAvailable()).isFalse();
    assertThat(new SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, null, null, null, null).isVortexAvailable()).isFalse();
    assertThat(new SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, null, null, null, null, true).isVortexAvailable()).isTrue();
  }
}
