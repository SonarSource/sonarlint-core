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

import com.google.gson.JsonParser;
import java.util.List;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintLauncherBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class CliIntegrationStateTests {

  @Test
  void should_preserve_enum_ordinals_for_ide_transports() {
    assertThat(CliIntegrationRecordingStatus.values()).extracting(Enum::name)
      .containsExactly("RECORDED", "NOT_RECORDED", "UNKNOWN");
    assertThat(CliIntegrationCheckStatus.values()).extracting(Enum::name)
      .containsExactly("CONFIGURED", "NOT_CONFIGURED", "INVALID", "UNKNOWN");
  }

  @Test
  void should_serialize_new_status_fields_through_the_core_rpc_launcher() {
    var cli = new SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNAUTHENTICATED, "/sonar", "1.9.0", null, null);
    var configurations = List.of(
      new CliIntegrationConfiguration("/agent", CliIntegrationCheckStatus.INVALID, CliIntegrationCheckStatus.NOT_CONFIGURED),
      new CliIntegrationConfiguration("/agent", CliIntegrationCheckStatus.CONFIGURED, CliIntegrationCheckStatus.UNKNOWN),
      new CliIntegrationConfiguration(null, null, null));
    var response = new GetAiIntegrationStateResponse(cli, List.of(), List.of(), null,
      List.of(new CliIntegrationState(AiAgent.CODEX, CliIntegrationRecordingStatus.RECORDED, configurations)));
    var message = new ResponseMessage();
    message.setId("1");
    message.setResult(response);

    var serialized = new TestLauncherBuilder().serialize(message);
    var result = JsonParser.parseString(serialized).getAsJsonObject().getAsJsonObject("result");
    var state = result.getAsJsonArray("cliIntegrations").get(0).getAsJsonObject();

    assertThat(result.getAsJsonObject("cli").get("authenticationStatus").getAsString()).isEqualTo("UNAUTHENTICATED");
    assertThat(state.get("agent").getAsString()).isEqualTo("CODEX");
    assertThat(state.get("recordingStatus").getAsString()).isEqualTo("RECORDED");
    var rows = state.getAsJsonArray("configurations");
    assertThat(rows.size()).isEqualTo(3);
    assertThat(rows.get(0).getAsJsonObject().get("path").getAsString()).isEqualTo("/agent");
    assertThat(rows.get(1).getAsJsonObject().get("path").getAsString()).isEqualTo("/agent");
    assertThat(rows.get(0).getAsJsonObject().get("mcp").getAsString()).isEqualTo("INVALID");
    assertThat(rows.get(0).getAsJsonObject().get("hooks").getAsString()).isEqualTo("NOT_CONFIGURED");
    assertThat(rows.get(1).getAsJsonObject().get("mcp").getAsString()).isEqualTo("CONFIGURED");
    assertThat(rows.get(1).getAsJsonObject().get("hooks").getAsString()).isEqualTo("UNKNOWN");
    assertThat(rows.get(2).getAsJsonObject().get("path").isJsonNull()).isTrue();
    assertThat(rows.get(2).getAsJsonObject().get("mcp").isJsonNull()).isTrue();
    assertThat(rows.get(2).getAsJsonObject().get("hooks").isJsonNull()).isTrue();
  }

  @Test
  void should_keep_existing_response_constructor_source_compatible() {
    var response = new GetAiIntegrationStateResponse(new SonarQubeCliState(CliInstallationStatus.NOT_INSTALLED,
      CliAuthenticationStatus.UNKNOWN, null, null, null, null), List.of(), List.of(), null);

    assertThat(response.getCliIntegrations()).isEmpty();
  }

  private static class TestLauncherBuilder extends SonarLintLauncherBuilder<AiAgentRpcService> {
    String serialize(ResponseMessage message) {
      setLocalService(new Object());
      setRemoteInterface(AiAgentRpcService.class);
      return createJsonHandler().serialize(message);
    }
  }
}
