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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdatePlanResponse;

/** Shared MCP inspection and update planning. Clients own file access and writes. */
public class McpConfigurationService {

  private static final String SONARQUBE_ENTRY = "sonarqube";
  private static final JsonMapper JSONC_MAPPER = JsonMapper.builder()
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
    .build();

  public McpConfigurationInspectionResponse inspect(McpConfigurationInspectionParams params) {
    var content = params.getContent();
    if (content == null || content.isBlank()) {
      return new McpConfigurationInspectionResponse(McpConfigurationState.NOT_CONFIGURED, List.of());
    }
    var parsed = parse(content, sectionName(params.getAgent()));
    if (parsed.error != null) {
      return new McpConfigurationInspectionResponse(McpConfigurationState.MALFORMED,
        List.of(parsed.error));
    }
    var state = classify(parsed.section);
    return new McpConfigurationInspectionResponse(state, diagnosticsFor(state));
  }

  public McpConfigurationUpdatePlanResponse planUpdate(McpConfigurationUpdateParams params) {
    var content = params.getContent();
    var desiredConfiguration = parseDesiredConfiguration(params.getSonarMcpConfiguration());
    if (desiredConfiguration == null) {
      return plan(McpConfigurationState.MALFORMED, null,
        List.of("The SonarQube MCP configuration must be a JSON object and cannot be applied."));
    }
    var sectionName = sectionName(params.getAgent());
    if (content == null || content.isBlank()) {
      return plan(McpConfigurationState.NOT_CONFIGURED,
        canonicalDocument(sectionName, desiredConfiguration), List.of());
    }
    var parsed = parse(content, sectionName);
    if (parsed.error != null) {
      return plan(McpConfigurationState.MALFORMED, null, List.of(parsed.error));
    }
    var state = classify(parsed.section);
    if (state == McpConfigurationState.CLI_MANAGED || state == McpConfigurationState.UNKNOWN) {
      return plan(state, null, diagnosticsFor(state));
    }
    return plan(state,
      updatedDocument(updateRoot(parsed.root, sectionName, desiredConfiguration)), List.of());
  }

  private static String sectionName(AiAgent agent) {
    return switch (agent) {
      case GITHUB_COPILOT -> "servers";
      case CURSOR, WINDSURF, KIRO, CLAUDE_CODE -> "mcpServers";
      case CODEX -> throw new IllegalArgumentException("Codex uses a TOML MCP configuration and is not supported here");
    };
  }

  private static String updatedDocument(ObjectNode root) {
    try {
      return JSONC_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize the MCP configuration", e);
    }
  }

  private static String canonicalDocument(String sectionName, ObjectNode entry) {
    var root = JSONC_MAPPER.createObjectNode();
    root.set(sectionName, sectionWithEntry(entry));
    return updatedDocument(root);
  }

  private static ObjectNode updateRoot(ObjectNode original, String sectionName, ObjectNode entry) {
    var root = original.deepCopy();
    var section = root.withObject(sectionName);
    section.set(SONARQUBE_ENTRY, entry);
    return root;
  }

  private static ObjectNode sectionWithEntry(ObjectNode entry) {
    var section = JSONC_MAPPER.createObjectNode();
    section.set(SONARQUBE_ENTRY, entry);
    return section;
  }

  private static McpConfigurationUpdatePlanResponse plan(McpConfigurationState state,
    @Nullable String updatedContent, List<String> diagnostics) {
    return new McpConfigurationUpdatePlanResponse(state, updatedContent, diagnostics);
  }

  private static McpConfigurationState classify(@Nullable ObjectNode section) {
    if (section == null || !section.has(SONARQUBE_ENTRY)) {
      return McpConfigurationState.NOT_CONFIGURED;
    }
    var entry = section.get(SONARQUBE_ENTRY);
    if (isCliManagedEntry(entry)) {
      return McpConfigurationState.CLI_MANAGED;
    }
    return isStandaloneEntry(entry) ? McpConfigurationState.STANDALONE : McpConfigurationState.UNKNOWN;
  }

  private static boolean isStandaloneEntry(JsonNode entry) {
    return entry.isObject() && entry.toString().contains("sonarsource/sonarqube-mcp");
  }

  private static boolean isCliManagedEntry(JsonNode entry) {
    if (!entry.isObject()) {
      return false;
    }
    var object = (ObjectNode) entry;
    var command = object.get("command");
    if (command == null || !command.isTextual() || !"sonar".equals(command.asText())) {
      return false;
    }
    var argsElement = object.get("args");
    if (argsElement == null || !argsElement.isArray()) {
      return false;
    }
    var args = argsElement;
    return args.size() >= 2
      && args.get(0).isTextual()
      && args.get(1).isTextual()
      && "run".equals(args.get(0).asText())
      && "mcp".equals(args.get(1).asText());
  }

  private static List<String> diagnosticsFor(McpConfigurationState state) {
    return switch (state) {
      case CLI_MANAGED -> List.of("The SonarQube MCP entry is managed by the CLI. Complete the CLI integration flow to change it.");
      case UNKNOWN -> List.of("The sonarqube MCP entry is not recognized and will not be changed.");
      default -> List.of();
    };
  }

  @Nullable
  private static ObjectNode parseDesiredConfiguration(String configuration) {
    try {
      return parseJsonObject(configuration);
    } catch (JsonProcessingException | IllegalStateException e) {
      return null;
    }
  }

  private static Parsed parse(String source, String sectionName) {
    try {
      var root = parseJsonObject(source);
      var section = root.has(sectionName) ? root.get(sectionName) : null;
      if (section != null && !section.isObject()) {
        return new Parsed(root, null, "The " + sectionName + " section must be an object.");
      }
      return new Parsed(root, section == null ? null : (ObjectNode) section, null);
    } catch (JsonProcessingException | IllegalStateException e) {
      return new Parsed(null, null, "Malformed MCP configuration: " + e.getMessage());
    }
  }

  private static ObjectNode parseJsonObject(String source) throws JsonProcessingException {
    var json = JSONC_MAPPER.readTree(source);
    if (json == null || !json.isObject()) {
      throw new IllegalStateException("Expected a JSON object");
    }
    return (ObjectNode) json;
  }

  private record Parsed(@Nullable ObjectNode root, @Nullable ObjectNode section, @Nullable String error) { }
}
