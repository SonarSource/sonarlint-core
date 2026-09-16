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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdatePlanResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpConfigurationServiceTests {
  private static final String ENTRY = "{\"command\":\"docker\",\"args\":[\"sonarsource/sonarqube-mcp\"]}";
  private final McpConfigurationService service = new McpConfigurationService();

  @Test
  void should_plan_creation_for_absent_file() {
    var response = service.planUpdate(params(null));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(updatedRoot(response).getAsJsonObject("mcpServers").has("sonarqube")).isTrue();
    assertThat(response.getDiagnostics()).isEmpty();
  }

  @Test
  void should_treat_blank_content_as_not_configured() {
    var inspection = service.inspect(inspectionParams(AiAgent.CURSOR, " \n\t"));
    var update = service.planUpdate(params(" \n\t"));

    assertThat(inspection.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(update.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(updatedRoot(update).getAsJsonObject("mcpServers").has("sonarqube")).isTrue();
  }

  @Test
  void should_update_only_the_standalone_sonarqube_entry() {
    var source = "{\n  \"mcpServers\": {\n    // keep this server\n    \"other\": {\"command\": \"other\"},\n    \"sonarqube\": {\"command\": \"docker\", \"args\": [\"sonarsource/sonarqube-mcp\", \"--old\"]}\n  }\n}\n";
    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    var servers = updatedRoot(response).getAsJsonObject("mcpServers");
    assertThat(servers.getAsJsonObject("other").get("command").getAsString()).isEqualTo("other");
    assertThat(servers.getAsJsonObject("sonarqube").getAsJsonArray("args")).hasSize(1);
  }

  @Test
  void should_add_sonarqube_to_an_existing_mcp_section() {
    var source = "{\"mcpServers\":{\"other\":{\"command\":\"other\"}}}";
    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    var servers = updatedRoot(response).getAsJsonObject("mcpServers");
    assertThat(servers.keySet()).containsExactlyInAnyOrder("other", "sonarqube");
  }

  @Test
  void should_add_mcp_section_to_a_root_with_unrelated_properties() {
    var source = "{\"name\":\"workspace\"}";
    var response = service.planUpdate(params(source));
    var updated = updatedRoot(response);

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(updated.get("name").getAsString()).isEqualTo("workspace");
    assertThat(updated.getAsJsonObject("mcpServers").has("sonarqube")).isTrue();
  }

  @Test
  void should_inspect_jsonc_content() {
    var source = "{\n  // MCP configuration\n  \"mcpServers\": {\n    \"sonarqube\": " + ENTRY + "\n  }\n}";

    var response = service.inspect(inspectionParams(AiAgent.CURSOR, source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getDiagnostics()).isEmpty();
  }

  @Test
  void should_normalize_jsonc_without_changing_unrelated_server_arguments() {
    var source = """
      {
        // comment intentionally discarded when rewriting
        "mcpServers": {
          "other": {
            "command": "docker",
            "args": ["run", "--pull=always",],
          },
        },
      }""";

    var response = service.planUpdate(params(source));

    assertThat(response.getUpdatedContent()).contains("--pull=always").doesNotContain("\\u003d");
    var args = updatedRoot(response).getAsJsonObject("mcpServers").getAsJsonObject("other").getAsJsonArray("args");
    assertThat(args).extracting(JsonElement::getAsString).containsExactly("run", "--pull=always");
  }

  @Test
  void should_detect_the_current_cli_mcp_command_with_additional_arguments() {
    var source = "{\"mcpServers\":{\"sonarqube\":{\"command\":\"sonar\",\"args\":[\"run\",\"mcp\",\"--project\",\"my-project\"]}}}";

    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.CLI_MANAGED);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).singleElement().asString().contains("managed by the CLI");
  }

  @Test
  void should_leave_an_unrecognized_sonarqube_entry_untouched() {
    var source = "{\"mcpServers\":{\"sonarqube\":{\"command\":\"my-sonar-wrapper\"}}}";

    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.UNKNOWN);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).singleElement().asString().contains("will not be changed");
  }

  @Test
  void should_not_replace_an_unrelated_entry_that_references_the_sonarqube_mcp_image() {
    var source = "{\"mcpServers\":{\"other\":{\"command\":\"docker\",\"args\":[\"sonarsource/sonarqube-mcp\"]}}}";

    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    var servers = updatedRoot(response).getAsJsonObject("mcpServers");
    assertThat(servers.keySet()).containsExactlyInAnyOrder("other", "sonarqube");
    assertThat(servers.getAsJsonObject("other").get("command").getAsString()).isEqualTo("docker");
  }

  @Test
  void should_reject_malformed_configuration() {
    var response = service.inspect(inspectionParams(AiAgent.CURSOR, "{\"mcpServers\":"));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getDiagnostics()).anyMatch(diagnostic -> diagnostic.contains("Malformed"));
  }

  @Test
  void should_reject_a_malformed_desired_entry_without_changing_the_document() {
    var params = new McpConfigurationUpdateParams(AiAgent.CURSOR, "{}", "not JSON");

    var response = service.planUpdate(params);

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).singleElement().asString().contains("cannot be applied");
  }

  @Test
  void should_reject_a_non_object_mcp_section() {
    var response = service.inspect(inspectionParams(AiAgent.CURSOR, "{\"mcpServers\":[]}"));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getDiagnostics()).singleElement().asString().contains("must be an object");
  }

  @Test
  void should_use_the_servers_section_for_github_copilot() {
    var source = "{\"servers\":{\"sonarqube\":" + ENTRY + "}}";

    var response = service.inspect(inspectionParams(AiAgent.GITHUB_COPILOT, source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
  }

  @Test
  void should_reject_codex_toml_configuration() {
    assertThatThrownBy(() -> service.inspect(inspectionParams(AiAgent.CODEX, "{}")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Codex uses a TOML MCP configuration and is not supported here");
  }

  private static JsonObject updatedRoot(McpConfigurationUpdatePlanResponse response) {
    assertThat(response.getUpdatedContent()).isNotNull();
    return JsonParser.parseString(response.getUpdatedContent()).getAsJsonObject();
  }

  private static McpConfigurationUpdateParams params(String content) {
    return new McpConfigurationUpdateParams(AiAgent.CURSOR, content, ENTRY);
  }

  private static McpConfigurationInspectionParams inspectionParams(AiAgent agent, String content) {
    return new McpConfigurationInspectionParams(agent, content);
  }
}
