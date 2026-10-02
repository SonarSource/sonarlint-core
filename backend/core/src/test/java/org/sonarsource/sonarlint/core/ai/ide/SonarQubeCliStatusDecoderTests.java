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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SonarQubeCliStatusDecoderTests {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "{} | UNKNOWN",
    "{\"auth\":null} | UNKNOWN",
    "{\"auth\":false} | UNKNOWN",
    "{\"auth\":{\"status\":\"unauthenticated\"}} | UNAUTHENTICATED",
    "{\"auth\":{\"status\":\"authenticated\",\"token\":\"invalid\"}} | INVALID"
  })
  void should_decode_integrations_independently_of_authentication(String authJson, CliAuthenticationStatus authenticationStatus) throws IOException {
    var json = authJson.substring(0, authJson.length() - 1) + (authJson.length() == 2 ? "" : ",")
      + "\"integrations\":[{\"id\":\"codex\"}]}";

    var status = SonarQubeCliStatusDecoder.decode(json);

    assertThat(status.authenticationStatus()).isEqualTo(authenticationStatus);
    assertThat(status.cliIntegrations().get(2).getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.RECORDED);
    assertThat(status.cliIntegrations().get(2).getConfigurations()).hasSize(1);
  }

  @Test
  void should_map_all_five_cli_ids_independently_of_detection() throws IOException {
    var status = SonarQubeCliStatusDecoder.decode("""
      {"integrations":[{"id":"claude-code"},{"id":"copilot-cli"},{"id":"codex"},{"id":"cursor"},{"id":"antigravity"}]}
      """);

    assertThat(status.cliIntegrations()).extracting(CliIntegrationState::getAgent)
      .containsExactly(AiAgent.CLAUDE_CODE, AiAgent.GITHUB_COPILOT_CLI, AiAgent.CODEX, AiAgent.CURSOR, AiAgent.ANTIGRAVITY);
    assertThat(status.cliIntegrations()).allSatisfy(state -> {
      assertThat(state.getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.RECORDED);
      assertThat(state.getConfigurations()).hasSize(1);
      assertThat(state.getConfigurations().get(0).getMcp()).isNull();
      assertThat(state.getConfigurations().get(0).getHooks()).isNull();
    });
  }

  @Test
  void should_distinguish_a_valid_empty_array_from_unavailable_evidence() throws IOException {
    assertThat(SonarQubeCliStatusDecoder.decode("{\"integrations\":[]}").cliIntegrations()).hasSize(5).allSatisfy(state -> {
      assertThat(state.getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.NOT_RECORDED);
      assertThat(state.getConfigurations()).isEmpty();
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"integrations\":null}", "{\"integrations\":{}}", "{\"integrations\":true}",
    "{\"integrations\":\"[]\"}", "[]", "null", ""})
  void should_report_unknown_when_the_integration_array_is_unavailable(String json) throws IOException {
    assertUnknown(SonarQubeCliStatusDecoder.decode(json));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "false", "42", "[]", "{}", "{\"id\":null}", "{\"id\":42}", "{\"id\":true}",
    "{\"id\":{}}", "{\"id\":\"\"}", "{\"id\":\"  \"}"})
  void should_preserve_positive_recordings_but_not_infer_absence_from_malformed_rows(String malformedRow) throws IOException {
    var states = SonarQubeCliStatusDecoder.decode("{\"integrations\":[" + malformedRow + ",{\"id\":\"codex\"}]}").cliIntegrations();

    assertThat(states).allSatisfy(state -> {
      var recorded = state.getAgent() == AiAgent.CODEX;
      assertThat(state.getRecordingStatus()).isEqualTo(recorded ? CliIntegrationRecordingStatus.RECORDED : CliIntegrationRecordingStatus.UNKNOWN);
      assertThat(state.getConfigurations()).hasSize(recorded ? 1 : 0);
    });
  }

  @Test
  void should_keep_duplicate_and_pathless_configurations_in_order() throws IOException {
    var states = SonarQubeCliStatusDecoder.decode("""
      {"integrations":[
        {"id":"claude-code","path":"/first","mcp":{"configured":true,"valid":false}},
        {"id":"codex","path":"/other"},
        {"id":"claude-code","path":"/first","hooks":{"configured":false}},
        {"id":"claude-code"},
        {"id":"claude-code","path":42}
      ]}
      """).cliIntegrations();

    assertThat(states.get(0).getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.RECORDED);
    assertThat(states.get(0).getConfigurations()).extracting(configuration -> configuration.getPath(),
      configuration -> configuration.getMcp(), configuration -> configuration.getHooks()).containsExactly(
        tuple("/first", CliIntegrationCheckStatus.INVALID, null),
        tuple("/first", null, CliIntegrationCheckStatus.NOT_CONFIGURED),
        tuple(null, null, null), tuple(null, null, null));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "{\"configured\":false} | NOT_CONFIGURED",
    "{\"configured\":false,\"valid\":true} | NOT_CONFIGURED",
    "{\"configured\":false,\"valid\":false} | NOT_CONFIGURED",
    "{\"configured\":false,\"valid\":\"bad\"} | NOT_CONFIGURED",
    "{\"configured\":true,\"valid\":true} | CONFIGURED",
    "{\"configured\":true,\"valid\":false} | INVALID",
    "{\"configured\":true} | UNKNOWN",
    "{\"configured\":true,\"valid\":null} | UNKNOWN",
    "{\"configured\":true,\"valid\":\"true\"} | UNKNOWN",
    "{\"configured\":true,\"valid\":1} | UNKNOWN",
    "{\"configured\":\"false\",\"valid\":true} | UNKNOWN",
    "{\"configured\":0,\"valid\":true} | UNKNOWN",
    "{\"configured\":null,\"valid\":true} | UNKNOWN",
    "{\"valid\":true} | UNKNOWN",
    "{} | UNKNOWN",
    "null | UNKNOWN",
    "false | UNKNOWN",
    "[] | UNKNOWN"
  })
  void should_strictly_decode_each_reported_optional_check(String checkJson, CliIntegrationCheckStatus expected) throws IOException {
    var state = SonarQubeCliStatusDecoder.decode("{\"integrations\":[{\"id\":\"codex\",\"mcp\":" + checkJson
      + ",\"hooks\":" + checkJson + "}]}").cliIntegrations().get(2);

    assertThat(state.getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.RECORDED);
    assertThat(state.getConfigurations().get(0).getMcp()).isEqualTo(expected);
    assertThat(state.getConfigurations().get(0).getHooks()).isEqualTo(expected);
  }

  @Test
  void should_ignore_unknown_identifiable_rows_including_git() throws IOException {
    var states = SonarQubeCliStatusDecoder.decode("""
      {"integrations":[{"id":"git"},{"id":"future-agent","mcp":false},{"id":"codex"}]}
      """).cliIntegrations();

    assertThat(states.get(2).getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.RECORDED);
    assertThat(states).filteredOn(state -> state.getAgent() != AiAgent.CODEX)
      .extracting(CliIntegrationState::getRecordingStatus).containsOnly(CliIntegrationRecordingStatus.NOT_RECORDED);
  }

  @Test
  void should_report_unknown_integration_state_when_status_is_unknown_or_unavailable() {
    assertUnknown(SonarQubeCliStatusDecoder.CliStatus.unknown());
    assertUnknown(SonarQubeCliStatusDecoder.CliStatus.unavailable());
  }

  private static void assertUnknown(SonarQubeCliStatusDecoder.CliStatus status) {
    assertThat(status.cliIntegrations()).hasSize(5).allSatisfy(state -> {
      assertThat(state.getRecordingStatus()).isEqualTo(CliIntegrationRecordingStatus.UNKNOWN);
      assertThat(state.getConfigurations()).isEmpty();
    });
  }
}
