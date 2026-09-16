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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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
    var sectionName = sectionName(params.getAgent());
    var content = params.getContent();
    if (content == null || content.isBlank()) {
      return new McpConfigurationInspectionResponse(McpConfigurationState.NOT_CONFIGURED, List.of());
    }
    var classified = parseAndClassify(content, sectionName);
    if (classified.error != null) {
      return new McpConfigurationInspectionResponse(McpConfigurationState.MALFORMED,
        List.of(classified.error));
    }
    return new McpConfigurationInspectionResponse(classified.state, diagnosticsFor(classified.state));
  }

  public McpConfigurationUpdatePlanResponse planUpdate(McpConfigurationUpdateParams params) {
    var content = params.getContent();
    var desiredEntry = parseDesiredEntry(params.getSonarMcpConfiguration());
    if (desiredEntry == null) {
      return plan(McpConfigurationState.MALFORMED, null,
        List.of("The SonarQube MCP configuration is malformed and cannot be applied."));
    }
    var sectionName = sectionName(params.getAgent());
    if (content == null || content.isBlank()) {
      return plan(McpConfigurationState.NOT_CONFIGURED,
        updatedDocument(updateRoot(JSONC_MAPPER.createObjectNode(), sectionName, desiredEntry)), List.of());
    }
    var classified = parseAndClassify(content, sectionName);
    if (classified.error != null) {
      return plan(McpConfigurationState.MALFORMED, null, List.of(classified.error));
    }
    if (classified.state == McpConfigurationState.CLI_MANAGED || classified.state == McpConfigurationState.UNKNOWN) {
      return plan(classified.state, null, diagnosticsFor(classified.state));
    }
    return plan(classified.state,
      updatedDocument(updateRoot(classified.root, sectionName, desiredEntry)), List.of());
  }

  private static String sectionName(AiAgent agent) {
    return AiAgentSupport.jsonSectionName(agent)
      .orElseThrow(() -> new IllegalArgumentException(agent + " is not supported by standalone MCP configuration"));
  }

  private static ClassifiedDocument parseAndClassify(String content, String sectionName) {
    var parsed = parse(content, sectionName);
    if (parsed.error != null) {
      return new ClassifiedDocument(McpConfigurationState.MALFORMED, null, parsed.error);
    }
    return new ClassifiedDocument(classify(parsed.section), parsed.root, null);
  }

  private static String updatedDocument(ObjectNode root) {
    return root.toPrettyString() + "\n";
  }

  private static ObjectNode updateRoot(ObjectNode original, String sectionName, JsonNode entry) {
    var root = original.deepCopy();
    var existingSection = root.get(sectionName);
    var section = existingSection == null ? root.putObject(sectionName) : (ObjectNode) existingSection;
    section.set(SONARQUBE_ENTRY, entry.deepCopy());
    return root;
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
  private static JsonNode parseDesiredEntry(String configuration) {
    try {
      var entry = JSONC_MAPPER.readTree(configuration);
      return entry != null && entry.isObject() ? entry : null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static Parsed parse(String source, String sectionName) {
    try {
      var document = JSONC_MAPPER.readTree(source);
      if (document == null || !document.isObject()) {
        return new Parsed(null, null, "The MCP configuration root must be an object.");
      }
      var root = (ObjectNode) document;
      var section = root.get(sectionName);
      if (section != null && !section.isObject()) {
        return new Parsed(root, null, "The " + sectionName + " section must be an object.");
      }
      return new Parsed(root, section == null ? null : (ObjectNode) section, null);
    } catch (IOException | RuntimeException e) {
      return new Parsed(null, null, "Malformed MCP configuration: " + e.getMessage());
    }
  }

  private record Parsed(@Nullable ObjectNode root, @Nullable ObjectNode section, @Nullable String error) { }

  private record ClassifiedDocument(McpConfigurationState state, @Nullable ObjectNode root, @Nullable String error) { }
}
