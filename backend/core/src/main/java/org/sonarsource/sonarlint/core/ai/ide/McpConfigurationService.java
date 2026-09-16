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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  public McpConfigurationInspectionResponse inspect(McpConfigurationInspectionParams params) {
    var content = params.getContent();
    if (content == null) {
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
    var desiredError = validateDesiredConfiguration(params.getSonarMcpConfiguration());
    if (desiredError != null) {
      return plan(McpConfigurationState.MALFORMED, null, List.of(desiredError));
    }
    var sectionName = sectionName(params.getAgent());
    if (content == null) {
      return plan(McpConfigurationState.NOT_CONFIGURED,
        canonicalDocument(sectionName, params.getSonarMcpConfiguration()), List.of());
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
      updatedDocument(updateRoot(parsed.root, sectionName, params.getSonarMcpConfiguration())), List.of());
  }

  private static String sectionName(AiAgent agent) {
    return agent == AiAgent.GITHUB_COPILOT ? "servers" : "mcpServers";
  }

  private static String updatedDocument(JsonObject root) {
    return GSON.toJson(root) + "\n";
  }

  private static String canonicalDocument(String sectionName, String entry) {
    var root = new JsonObject();
    root.add(sectionName, sectionWithEntry(entry));
    return GSON.toJson(root) + "\n";
  }

  private static JsonObject updateRoot(JsonObject original, String sectionName, String entry) {
    var root = original.deepCopy();
    var section = root.has(sectionName) && root.get(sectionName).isJsonObject()
      ? root.getAsJsonObject(sectionName)
      : new JsonObject();
    section.add(SONARQUBE_ENTRY, JsonParser.parseString(entry));
    root.add(sectionName, section);
    return root;
  }

  private static JsonObject sectionWithEntry(String entry) {
    var section = new JsonObject();
    section.add(SONARQUBE_ENTRY, JsonParser.parseString(entry));
    return section;
  }

  private static McpConfigurationUpdatePlanResponse plan(McpConfigurationState state,
    @Nullable String updatedContent, List<String> diagnostics) {
    return new McpConfigurationUpdatePlanResponse(state, updatedContent, diagnostics);
  }

  private static McpConfigurationState classify(@Nullable JsonObject section) {
    if (section == null || !section.has(SONARQUBE_ENTRY)) {
      return McpConfigurationState.NOT_CONFIGURED;
    }
    var entry = section.get(SONARQUBE_ENTRY);
    if (isCliManagedEntry(entry)) {
      return McpConfigurationState.CLI_MANAGED;
    }
    return isStandaloneEntry(entry) ? McpConfigurationState.STANDALONE : McpConfigurationState.UNKNOWN;
  }

  private static boolean isStandaloneEntry(JsonElement entry) {
    return entry.isJsonObject() && entry.toString().contains("sonarsource/sonarqube-mcp");
  }

  private static boolean isCliManagedEntry(JsonElement entry) {
    if (!entry.isJsonObject()) {
      return false;
    }
    var object = entry.getAsJsonObject();
    var command = object.get("command");
    if (command == null || !command.isJsonPrimitive() || !command.getAsJsonPrimitive().isString()
      || !"sonar".equals(command.getAsString())) {
      return false;
    }
    var argsElement = object.get("args");
    if (argsElement == null || !argsElement.isJsonArray()) {
      return false;
    }
    var args = argsElement.getAsJsonArray();
    return args.size() >= 2
      && args.get(0).isJsonPrimitive()
      && args.get(1).isJsonPrimitive()
      && "run".equals(args.get(0).getAsString())
      && "mcp".equals(args.get(1).getAsString());
  }

  private static List<String> diagnosticsFor(McpConfigurationState state) {
    return switch (state) {
      case CLI_MANAGED -> List.of("The SonarQube MCP entry is managed by the CLI. Complete the CLI integration flow to change it.");
      case UNKNOWN -> List.of("The sonarqube MCP entry is not recognized and will not be changed.");
      default -> List.of();
    };
  }

  private static String validateDesiredConfiguration(String configuration) {
    try {
      if (!JsonParser.parseString(configuration).isJsonObject()) return "The SonarQube MCP configuration must be a JSON object.";
      return null;
    } catch (RuntimeException e) {
      return "The SonarQube MCP configuration is malformed and cannot be applied.";
    }
  }

  private static Parsed parse(String source, String sectionName) {
    try {
      var root = JsonParser.parseString(source).getAsJsonObject();
      var section = root.has(sectionName) ? root.get(sectionName) : null;
      if (section != null && !section.isJsonObject()) {
        return new Parsed(root, null, "The " + sectionName + " section must be an object.");
      }
      return new Parsed(root, section == null ? null : section.getAsJsonObject(), null);
    } catch (JsonSyntaxException | IllegalStateException e) {
      return new Parsed(null, null, "Malformed MCP configuration: " + e.getMessage());
    }
  }

  private record Parsed(@Nullable JsonObject root, @Nullable JsonObject section, @Nullable String error) { }
}
