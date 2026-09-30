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

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdatePlanResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpConfigurationServiceTests {
  private static final String ENTRY = "{\"command\":\"docker\",\"args\":[\"sonarsource/sonarqube-mcp\"]}";
  private static final String GENERATED_ENTRY = """
    {
      "command": "docker",
      "args": ["run", "--init", "--pull=always", "-i", "--rm", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_URL",
        "-e", "SONARQUBE_IDE_PORT", "-e", "SONARQUBE_ORG", "sonarsource/sonarqube-mcp"],
      "env": {"SONARQUBE_URL": "https://new.example.com", "SONARQUBE_TOKEN": "new-token", "SONARQUBE_IDE_PORT": "64120",
        "SONARQUBE_ORG": "new-org"}
    }
    """;
  private static final String CUSTOM_ENTRY = """
    {
      "command": "docker",
      "args": ["run", "--init", "--pull=always", "-i", "--rm", "--user", "0:0", "-e", "SONARQUBE_TOKEN",
        "-e", "SONARQUBE_URL", "-e", "SONARQUBE_IDE_PORT", "-v", "/example/user.p12:/etc/ssl/mcp/user.p12:ro,z",
        "-e", "JAVA_OPTS", "sonarsource/sonarqube-mcp:custom"],
      "env": {
        "SONARQUBE_URL": "${input:sonarqube-url}", "SONARQUBE_TOKEN": "${input:sonarqube-token}", "SONARQUBE_IDE_PORT": "64121",
        "SONARQUBE_ORG": "${input:sonarqube-org}",
        "JAVA_OPTS": "-Djavax.net.ssl.keyStore=/etc/ssl/mcp/user.p12 -Djavax.net.ssl.keyStoreType=PKCS12",
        "CUSTOM_SETTING": "keep"
      },
      "disabled": false,
      "autoApprove": ["custom-tool"]
    }
    """;
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).enable(JsonReadFeature.ALLOW_TRAILING_COMMA).build();
  private final McpConfigurationService service = new McpConfigurationService();

  @Test
  void should_plan_creation_for_absent_file() {
    var response = service.planUpdate(params(null));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(mcpServers(response).has("sonarqube")).isTrue();
    assertThat(response.getDiagnostics()).isEmpty();
  }

  @Test
  void should_preserve_standalone_arguments_when_no_environment_update_is_requested() {
    var source = "{\n  \"mcpServers\": {\n    // keep this server\n    \"other\": {\"command\": \"other\"},\n    \"sonarqube\": {\"command\": \"docker\", \"args\": [\"sonarsource/sonarqube-mcp\", \"--old\"]}\n  }\n}\n";
    var response = service.planUpdate(params(source));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getUpdatedContent()).isEqualTo(source);
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

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_update_only_the_ide_port_and_preserve_the_complete_customized_document(AiAgent agent) throws IOException {
    var sectionName = sectionName(agent);
    var original = (ObjectNode) JSON_MAPPER.readTree(CUSTOM_ENTRY);
    var root = JSON_MAPPER.createObjectNode().put("custom-root", "keep");
    var section = root.putObject(sectionName);
    section.putObject("other").put("command", "other");
    section.set("sonarqube", original);
    var expected = root.deepCopy();
    ((ObjectNode) expected.path(sectionName).path("sonarqube").path("env")).put("SONARQUBE_IDE_PORT", "64120");
    var desired = (ObjectNode) JSON_MAPPER.readTree(GENERATED_ENTRY);
    desired.put("command", "different-launcher");
    desired.putArray("args").add("--different").add("sonarsource/sonarqube-mcp:generated");
    desired.put("disabled", true);
    desired.putArray("autoApprove").add("different-tool");
    ((ObjectNode) desired.get("env")).put("JAVA_OPTS", "-Ddifferent=true").put("CUSTOM_SETTING", "replace").put("NEW_SETTING", "add");

    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, root.toString(), desired.toString()));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getDiagnostics()).isEmpty();
    assertThat(updatedRoot(response)).isEqualTo(expected);
    var repeated = service.planUpdate(new McpConfigurationUpdateParams(agent, response.getUpdatedContent(), desired.toString()));
    assertThat(repeated.getUpdatedContent()).isEqualTo(response.getUpdatedContent());
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_use_the_complete_generated_entry_for_new_configuration(AiAgent agent) throws IOException {
    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, null, GENERATED_ENTRY));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.NOT_CONFIGURED);
    assertThat(updatedRoot(response).path(sectionName(agent)).get("sonarqube")).isEqualTo(JSON_MAPPER.readTree(GENERATED_ENTRY));
    assertThat(updatedRoot(response).path(sectionName(agent)).path("sonarqube").path("env").path("SONARQUBE_IDE_PORT").asText()).isEqualTo("64120");
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_leave_an_existing_entry_unchanged_when_the_environment_is_absent(AiAgent agent) throws IOException {
    var original = (ObjectNode) JSON_MAPPER.readTree(CUSTOM_ENTRY);
    original.remove("env");
    var response = service.planUpdate(updateParams(agent, original, GENERATED_ENTRY));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).containsExactly(
      "The existing SonarQube MCP configuration has no SONARQUBE_IDE_PORT; the IDE port was not updated.");
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_leave_an_existing_entry_unchanged_when_its_environment_lacks_the_ide_port(AiAgent agent) throws IOException {
    var original = (ObjectNode) JSON_MAPPER.readTree(CUSTOM_ENTRY);
    original.putObject("env").put("CUSTOM_SETTING", "keep");
    var response = service.planUpdate(updateParams(agent, original, GENERATED_ENTRY));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).containsExactly(
      "The existing SonarQube MCP configuration has no SONARQUBE_IDE_PORT; the IDE port was not updated.");
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_not_remove_connection_values_missing_from_the_desired_configuration(AiAgent agent) throws IOException {
    var original = (ObjectNode) JSON_MAPPER.readTree(CUSTOM_ENTRY);
    var expected = original.deepCopy();
    ((ObjectNode) expected.get("env")).put("SONARQUBE_IDE_PORT", "64120");

    var response = service.planUpdate(updateParams(agent, original, "{\"env\":{\"SONARQUBE_IDE_PORT\":\"64120\"}}"));

    assertThat(updatedRoot(response).path(sectionName(agent)).get("sonarqube")).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_return_original_content_when_the_desired_environment_is_absent(AiAgent agent) {
    var source = customizedConfiguration(agent);

    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, source, ENTRY));

    assertThat(response.getUpdatedContent()).isEqualTo(source);
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_return_original_content_when_the_desired_port_is_absent(AiAgent agent) throws IOException {
    var source = customizedConfiguration(agent);
    var desired = (ObjectNode) JSON_MAPPER.readTree(GENERATED_ENTRY);
    ((ObjectNode) desired.get("env")).remove("SONARQUBE_IDE_PORT");

    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, source, desired.toString()));

    assertThat(response.getUpdatedContent()).isEqualTo(source);
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_return_original_content_when_the_desired_port_is_null(AiAgent agent) throws IOException {
    var source = customizedConfiguration(agent);
    var desired = (ObjectNode) JSON_MAPPER.readTree(GENERATED_ENTRY);
    ((ObjectNode) desired.get("env")).putNull("SONARQUBE_IDE_PORT");

    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, source, desired.toString()));

    assertThat(response.getUpdatedContent()).isEqualTo(source);
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_return_original_content_when_the_port_is_unchanged_despite_other_desired_changes(AiAgent agent) throws IOException {
    var source = customizedConfiguration(agent);
    var desired = (ObjectNode) JSON_MAPPER.readTree(GENERATED_ENTRY);
    ((ObjectNode) desired.get("env")).put("SONARQUBE_IDE_PORT", "64121");

    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, source, desired.toString()));

    assertThat(response.getUpdatedContent()).isEqualTo(source);
  }

  @ParameterizedTest
  @CsvSource({
    "CURSOR, null", "CURSOR, []", "CURSOR, \"custom\"", "CURSOR, 42",
    "GITHUB_COPILOT, null", "GITHUB_COPILOT, []", "GITHUB_COPILOT, \"custom\"", "GITHUB_COPILOT, 42"
  })
  void should_decline_updates_to_a_non_object_existing_environment(AiAgent agent, String environment) throws IOException {
    var original = (ObjectNode) JSON_MAPPER.readTree(CUSTOM_ENTRY);
    original.set("env", JSON_MAPPER.readTree(environment));

    var response = service.planUpdate(updateParams(agent, original, GENERATED_ENTRY));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.STANDALONE);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).containsExactly("The existing SonarQube MCP environment is invalid or malformed.");
  }

  @ParameterizedTest
  @CsvSource({
    "CURSOR, null", "CURSOR, []", "CURSOR, \"custom\"", "CURSOR, 42",
    "GITHUB_COPILOT, null", "GITHUB_COPILOT, []", "GITHUB_COPILOT, \"custom\"", "GITHUB_COPILOT, 42"
  })
  void should_decline_a_non_object_desired_environment(AiAgent agent, String environment) throws IOException {
    var original = (ObjectNode) JSON_MAPPER.readTree(CUSTOM_ENTRY);

    var response = service.planUpdate(updateParams(agent, original, "{\"env\":" + environment + "}"));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getDiagnostics()).containsExactly("The SonarQube MCP environment is invalid or malformed.");
  }

  @ParameterizedTest
  @EnumSource(value = AiAgent.class, names = {"CURSOR", "GITHUB_COPILOT"})
  void should_update_jsonc_configuration_without_changing_custom_values(AiAgent agent) throws IOException {
    var source = """
      {
        // Custom MCP configuration
        "%s": {
          "sonarqube": {
            "command": "docker", "args": ["sonarsource/sonarqube-mcp",],
            "env": {"SONARQUBE_IDE_PORT": /* port */ "64121", "CUSTOM": "é",},
          },
        },
      }
      """.formatted(sectionName(agent)).replace("\n", "\r\n");
    source = source.replace("SONARQUBE_IDE_PORT", "SONARQUBE_IDE_\\u0050ORT");

    var response = service.planUpdate(new McpConfigurationUpdateParams(agent, source, GENERATED_ENTRY));

    assertThat(updatedRoot(response)).isEqualTo(JSON_MAPPER.readTree(source.replace("\"64121\"", "\"64120\"")));
    assertThat(response.getDiagnostics()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"mcpServers\":{},\"mcpServers\":{}}",
    "{\"mcpServers\":{\"sonarqube\":{},\"sonarqube\":{}}}",
    "{\"mcpServers\":{\"sonarqube\":{\"args\":[\"sonarsource/sonarqube-mcp\"],\"env\":{\"SONARQUBE_IDE_PORT\":1,\"SONARQUBE_IDE_PORT\":2}}}}",
    "{\"unrelated\":{\"key\":1,\"k\\u0065y\":2}}"
  })
  void should_decline_ambiguous_duplicate_keys(String source) {
    var inspection = service.inspect(inspectionParams(AiAgent.CURSOR, source));
    var response = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, source, GENERATED_ENTRY));

    assertThat(inspection.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getUpdatedContent()).isNull();
  }

  @Test
  void should_decline_duplicate_keys_in_the_desired_entry() {
    var response = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, "{}", "{\"env\":{},\"env\":{}}"));

    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
    assertThat(response.getUpdatedContent()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "65536", "1.5", "1.0", "true", "[]", "{}", "\"0\"", "\"-1\"", "\"65536\"", "\"1.5\"", "\"\"", "\" 123\"", "\"+1\"", "\"9999999999999999999999999\""})
  void should_decline_invalid_ports_for_creation_and_update(String port) {
    var desired = "{\"env\":{\"SONARQUBE_IDE_PORT\":" + port + "}}";
    for (var content : new String[] {null, "{}", customizedConfiguration(AiAgent.CURSOR)}) {
      var response = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, content, desired));

      assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
      assertThat(response.getUpdatedContent()).isNull();
      assertThat(response.getDiagnostics()).containsExactly("The SonarQube IDE port must be an integer from 1 to 65535.");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "65535", "64120", "\"1\"", "\"65535\"", "\"64120\"", "\"00001\""})
  void should_accept_valid_numeric_and_string_ports(String port) throws IOException {
    var source = customizedConfiguration(AiAgent.CURSOR);
    var desired = "{\"env\":{\"SONARQUBE_IDE_PORT\":" + port + "}}";

    var response = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, source, desired));

    assertThat(updatedRoot(response)).isEqualTo(JSON_MAPPER.readTree(source.replace("\"64121\"", port)));
    assertThat(updatedRoot(response).path("mcpServers").path("sonarqube").path("env").get("SONARQUBE_IDE_PORT")).isEqualTo(JSON_MAPPER.readTree(port));
  }

  @ParameterizedTest
  @ValueSource(strings = {"64121", "null", "true", "[]", "{\"old\":64121}", "\"64\\u003121\""})
  void should_replace_the_existing_port_value_without_changing_other_settings(String oldPort) throws IOException {
    for (var agent : new AiAgent[] {AiAgent.CURSOR, AiAgent.GITHUB_COPILOT}) {
      var source = "{\"%s\":{\"sonarqube\":{\"args\":[\"sonarsource/sonarqube-mcp\"],\"env\":{\"SONARQUBE_IDE_PORT\":%s,\"NEXT\":1}}}}"
        .formatted(sectionName(agent), oldPort);

      var response = service.planUpdate(new McpConfigurationUpdateParams(agent, source, GENERATED_ENTRY));

      var expected = (ObjectNode) JSON_MAPPER.readTree(source);
      ((ObjectNode) expected.path(sectionName(agent)).path("sonarqube").path("env")).put("SONARQUBE_IDE_PORT", "64120");
      assertThat(updatedRoot(response)).isEqualTo(expected);
    }
  }

  @Test
  void should_reject_a_null_port_on_creation() {
    var response = service.planUpdate(new McpConfigurationUpdateParams(AiAgent.CURSOR, "{}", "{\"env\":{\"SONARQUBE_IDE_PORT\":null}}"));

    assertThat(response.getUpdatedContent()).isNull();
    assertThat(response.getState()).isEqualTo(McpConfigurationState.MALFORMED);
  }

  private static String customizedConfiguration(AiAgent agent) {
    return "{\"" + sectionName(agent) + "\":{\"sonarqube\":" + CUSTOM_ENTRY + "}}";
  }

  private static String sectionName(AiAgent agent) {
    return agent == AiAgent.GITHUB_COPILOT ? "servers" : "mcpServers";
  }

  private static McpConfigurationUpdateParams updateParams(AiAgent agent, ObjectNode entry, String desiredEntry) {
    var root = JSON_MAPPER.createObjectNode();
    root.putObject(sectionName(agent)).set("sonarqube", entry);
    return new McpConfigurationUpdateParams(agent, root.toString(), desiredEntry);
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
