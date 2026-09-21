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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
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
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private final McpConfigurationService service = new McpConfigurationService();

  @Test
  void should_plan_creation_for_absent_file() {
    var response = service.planUpdate(params(null));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(mcpServers(response).has("sonarqube")).isTrue();
    assertThat(response.getDiagnostics()).isEmpty();
  }

  @Test
  void should_update_only_the_standalone_sonarqube_entry() {
    var source = "{\n  \"mcpServers\": {\n    // keep this server\n    \"other\": {\"command\": \"other\"},\n    \"sonarqube\": {\"command\": \"docker\", \"args\": [\"sonarsource/sonarqube-mcp\", \"--old\"]}\n  }\n}\n";
    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    var servers = mcpServers(response);
    assertThat(servers.get("other").get("command").asText()).isEqualTo("other");
    assertThat(servers.get("sonarqube").get("args").size()).isEqualTo(1);
  }

  @Test
  void should_add_sonarqube_to_an_existing_mcp_section() {
    var source = "{\"mcpServers\":{\"other\":{\"command\":\"other\"}}}";
    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    var servers = mcpServers(response);
    assertThat(servers.has("other")).isTrue();
    assertThat(servers.has("sonarqube")).isTrue();
  }

  @Test
  void should_add_mcp_section_to_a_root_with_unrelated_properties() {
    var source = "{\"name\":\"workspace\"}";
    var response = service.planUpdate(params(source));
    var updated = updatedRoot(response);

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(updated.get("name").asText()).isEqualTo("workspace");
    assertThat(((ObjectNode) updated.get("mcpServers")).has("sonarqube")).isTrue();
  }

  @Test
  void should_inspect_jsonc_content() {
    var source = "{\n  // MCP configuration\n  \"mcpServers\": {\n    \"sonarqube\": " + ENTRY + "\n  }\n}";

    var response = service.inspect(inspectionParams(AiAgent.CURSOR, source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getDiagnostics()).isEmpty();
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
    var servers = mcpServers(response);
    assertThat(servers.has("other")).isTrue();
    assertThat(servers.has("sonarqube")).isTrue();
    assertThat(servers.get("other").get("command").asText()).isEqualTo("docker");
  }

  @Test
  void should_reject_malformed_configuration() {
    var response = service.inspect(inspectionParams(AiAgent.CURSOR, "{\"mcpServers\":"));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getDiagnostics()).anyMatch(diagnostic -> diagnostic.contains("Malformed"));
  }

  @Test
  void should_reject_trailing_json_content_without_an_update_plan() {
    var source = "{\"mcpServers\":{}} {}";

    var inspection = service.inspect(inspectionParams(AiAgent.CURSOR, source));
    var update = service.planUpdate(params(source));

    assertThat(inspection.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(update.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(update.getUpdatedContent()).isNull();
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
  void should_treat_blank_configuration_as_not_configured() {
    var inspection = service.inspect(inspectionParams(AiAgent.CURSOR, " \n"));
    var update = service.planUpdate(params(" \n"));

    assertThat(inspection.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(update.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(mcpServers(update).has("sonarqube")).isTrue();
  }

  @Test
  void should_preserve_unrelated_jsonc_trailing_commas_without_null_entries() {
    var source = """
      {
        // Unrelated MCP server
        "mcpServers": {
          "other": {"command": "docker", "args": ["run", "--pull=always",],},
        },
      }
      """;

    var response = service.planUpdate(params(source));
    var args = mcpServers(response).get("other").get("args");

    assertThat(args.size()).isEqualTo(2);
    assertThat(args.get(1).asText()).isEqualTo("--pull=always");
  }

  @Test
  void should_reject_codex_toml_configuration() {
    var updateParams = new McpConfigurationUpdateParams(AiAgent.CODEX, null, ENTRY);
    var inspectionParams = inspectionParams(AiAgent.CODEX, null);

    assertThatThrownBy(() -> service.planUpdate(updateParams))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not supported");
    assertThatThrownBy(() -> service.inspect(inspectionParams))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not supported");
  }

  @Test
  void should_reject_an_unsupported_agent_before_validating_the_desired_entry() {
    var updateParams = new McpConfigurationUpdateParams(AiAgent.CODEX, "{}", "not JSON");

    assertThatThrownBy(() -> service.planUpdate(updateParams))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not supported");
  }

  @Test
  void should_use_the_servers_section_for_github_copilot() {
    var source = "{\"servers\":{\"sonarqube\":" + ENTRY + "}}";

    var response = service.inspect(inspectionParams(AiAgent.GITHUB_COPILOT, source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
  }

  @Test
  void should_reject_malformed_file_content_on_plan_without_replacement() {
    var response = service.planUpdate(params("{\"mcpServers\":"));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).anyMatch(diagnostic -> diagnostic.contains("Malformed"));
  }

  @Test
  void should_inspect_cli_managed_and_unknown_entries_without_writing() {
    var cliManaged = "{\"mcpServers\":{\"sonarqube\":{\"command\":\"sonar\",\"args\":[\"run\",\"mcp\",\"--project\",\"my-project\"]}}}";
    var unknown = "{\"mcpServers\":{\"sonarqube\":{\"command\":\"my-sonar-wrapper\"}}}";

    var cliInspection = service.inspect(inspectionParams(AiAgent.CURSOR, cliManaged));
    var unknownInspection = service.inspect(inspectionParams(AiAgent.CURSOR, unknown));

    assertThat(cliInspection.getState()).isEqualTo(McpConfigurationState.CLI_MANAGED);
    assertThat(cliInspection.getDiagnostics()).singleElement().asString().contains("managed by the CLI");
    assertThat(unknownInspection.getState()).isEqualTo(McpConfigurationState.UNKNOWN);
    assertThat(unknownInspection.getDiagnostics()).singleElement().asString().contains("will not be changed");
  }

  @Test
  void should_plan_github_copilot_updates_under_servers() {
    var source = "{\"servers\":{\"other\":{\"command\":\"other\"}}}";
    var params = new McpConfigurationUpdateParams(AiAgent.GITHUB_COPILOT, source, ENTRY);

    var response = service.planUpdate(params);
    var root = updatedRoot(response);
    var servers = (ObjectNode) root.get("servers");

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(root.has("mcpServers")).isFalse();
    assertThat(servers.has("other")).isTrue();
    assertThat(servers.has("sonarqube")).isTrue();
  }

  @Test
  void should_prefer_desired_entry_errors_when_the_file_is_also_malformed() {
    var params = new McpConfigurationUpdateParams(AiAgent.CURSOR, "{\"mcpServers\":", "not JSON");

    var response = service.planUpdate(params);

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).singleElement().asString().contains("cannot be applied");
  }

  private static ObjectNode mcpServers(McpConfigurationUpdatePlanResponse response) {
    return (ObjectNode) updatedRoot(response).get("mcpServers");
  }

  private static ObjectNode updatedRoot(McpConfigurationUpdatePlanResponse response) {
    assertThat(response.getUpdatedContent()).isNotNull();
    try {
      return (ObjectNode) JSON_MAPPER.readTree(response.getUpdatedContent());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static McpConfigurationUpdateParams params(String content) {
    return new McpConfigurationUpdateParams(AiAgent.CURSOR, content, ENTRY);
  }

  private static McpConfigurationInspectionParams inspectionParams(AiAgent agent, String content) {
    return new McpConfigurationInspectionParams(agent, content);
  }
}
