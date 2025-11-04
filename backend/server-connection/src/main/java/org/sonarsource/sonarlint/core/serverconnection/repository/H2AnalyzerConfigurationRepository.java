/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.jooq.JSON;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.RuleSet;
import org.sonarsource.sonarlint.core.serverconnection.Settings;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.ACTIVE_RULESETS;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.PROJECTS;

public class H2AnalyzerConfigurationRepository implements AnalyzerConfigurationRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

  private final SonarLintDatabase database;

  public H2AnalyzerConfigurationRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public boolean hasActiveRules(String connectionId, String projectKey) {
    var count = database.dsl()
      .selectCount()
      .from(ACTIVE_RULESETS)
      .where(ACTIVE_RULESETS.CONNECTION_ID.eq(connectionId))
      .and(ACTIVE_RULESETS.PROJECT_KEY.eq(projectKey))
      .fetchOne(0, int.class);
    return count > 0;
  }

  @Override
  public boolean hasSettings(String connectionId, String projectKey) {
    var settings = database.dsl()
      .select(PROJECTS.SETTINGS)
      .from(PROJECTS)
      .where(PROJECTS.CONNECTION_ID.eq(connectionId))
      .and(PROJECTS.PROJECT_KEY.eq(projectKey))
      .fetchAny();
    return settings != null;
  }

  @Override
  public void store(String connectionId, String projectKey, AnalyzerConfiguration analyzerConfiguration) {
    var dsl = database.dsl();
    try {
      // Delete all existing rule sets for this project
      dsl.deleteFrom(ACTIVE_RULESETS)
        .where(ACTIVE_RULESETS.CONNECTION_ID.eq(connectionId))
        .and(ACTIVE_RULESETS.PROJECT_KEY.eq(projectKey))
        .execute();

      // Insert all rule sets
      for (var entry : analyzerConfiguration.getRuleSetByLanguageKey().entrySet()) {
        var languageKey = entry.getKey();
        var ruleSet = entry.getValue();
        
        // Serialize rules to JSON
        var rulesJson = serializeRules(ruleSet.getRules());
        var rulesJsonValue = JSON.valueOf(rulesJson);
        
        // Convert lastModified string to LocalDateTime
        var lastModified = parseLastModified(ruleSet.getLastModified());

        dsl.insertInto(ACTIVE_RULESETS,
          ACTIVE_RULESETS.CONNECTION_ID,
          ACTIVE_RULESETS.PROJECT_KEY,
          ACTIVE_RULESETS.LANGUAGE_KEY,
          ACTIVE_RULESETS.LAST_MODIFIED,
          ACTIVE_RULESETS.RULES)
          .values(connectionId, projectKey, languageKey, lastModified, rulesJsonValue)
          .execute();
      }

      // Store settings in PROJECTS table
      var settings = analyzerConfiguration.getSettings().getAll();
      var settingsJson = serializeSettings(settings);
      var settingsJsonValue = settingsJson != null ? JSON.valueOf(settingsJson) : null;
      
      int projectUpdated = dsl.update(PROJECTS)
        .set(PROJECTS.SETTINGS, settingsJsonValue)
        .where(PROJECTS.CONNECTION_ID.eq(connectionId))
        .and(PROJECTS.PROJECT_KEY.eq(projectKey))
        .execute();
      if (projectUpdated == 0) {
        // Project doesn't exist yet, insert it
        dsl.insertInto(PROJECTS, PROJECTS.CONNECTION_ID, PROJECTS.PROJECT_KEY, PROJECTS.SETTINGS)
          .values(connectionId, projectKey, settingsJsonValue)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public AnalyzerConfiguration read(String connectionId, String projectKey) {
    var records = database.dsl()
      .select(ACTIVE_RULESETS.LANGUAGE_KEY, ACTIVE_RULESETS.LAST_MODIFIED, ACTIVE_RULESETS.RULES)
      .from(ACTIVE_RULESETS)
      .where(ACTIVE_RULESETS.CONNECTION_ID.eq(connectionId))
      .and(ACTIVE_RULESETS.PROJECT_KEY.eq(projectKey))
      .fetch();

    var ruleSetByLanguageKey = new HashMap<String, RuleSet>();
    for (var rec : records) {
      var languageKey = rec.get(ACTIVE_RULESETS.LANGUAGE_KEY);
      var lastModifiedLdt = rec.get(ACTIVE_RULESETS.LAST_MODIFIED);
      var rulesJsonValue = rec.get(ACTIVE_RULESETS.RULES);
      
      // Convert LocalDateTime back to string
      var lastModified = formatLastModified(lastModifiedLdt);
      
      // Deserialize rules from JSON
      var rulesJsonString = rulesJsonValue != null ? rulesJsonValue.data() : "[]";
      var rules = deserializeRules(rulesJsonString);
      
      ruleSetByLanguageKey.put(languageKey, new RuleSet(rules, lastModified));
    }

    // Read settings from PROJECTS table
    var projectRec = database.dsl()
      .select(PROJECTS.SETTINGS)
      .from(PROJECTS)
      .where(PROJECTS.CONNECTION_ID.eq(connectionId))
      .and(PROJECTS.PROJECT_KEY.eq(projectKey))
      .fetchOne();

    Map<String, String> settingsMap;
    if (projectRec != null) {
      var settingsJsonValue = projectRec.get(PROJECTS.SETTINGS);
      var settingsJsonString = settingsJsonValue != null ? settingsJsonValue.data() : null;
      settingsMap = deserializeSettings(settingsJsonString);
    } else {
      settingsMap = Collections.emptyMap();
    }
    var settings = new Settings(settingsMap);
    var schemaVersion = AnalyzerConfiguration.CURRENT_SCHEMA_VERSION;

    return new AnalyzerConfiguration(settings, ruleSetByLanguageKey, schemaVersion);
  }

  @Override
  public void update(String connectionId, String projectKey, UnaryOperator<AnalyzerConfiguration> updater) {
    var current = read(connectionId, projectKey);
    var updated = updater.apply(current);
    store(connectionId, projectKey, updated);
  }

  private static String serializeRules(Collection<ServerActiveRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return "[]";
    }
    return rules.stream()
      .map(rule -> {
        var sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ruleKey\":\"").append(escapeJson(rule.ruleKey())).append("\",");
        sb.append("\"severity\":\"").append(rule.severity().name()).append("\",");
        if (rule.templateKey() != null) {
          sb.append("\"templateKey\":\"").append(escapeJson(rule.templateKey())).append("\",");
        }
        sb.append("\"params\":{");
        var paramsJson = rule.params().entrySet().stream()
          .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
          .collect(Collectors.joining(","));
        sb.append(paramsJson);
        sb.append("},");
        sb.append("\"overriddenImpacts\":[");
        var impactsJson = rule.overriddenImpacts().stream()
          .map(impact -> "{\"softwareQuality\":\"" + impact.softwareQuality() + "\",\"severity\":\"" + impact.severity() + "\"}")
          .collect(Collectors.joining(","));
        sb.append(impactsJson);
        sb.append("]");
        sb.append("}");
        return sb.toString();
      })
      .collect(Collectors.joining(",", "[", "]"));
  }

  private static List<ServerActiveRule> deserializeRules(String rulesJson) {
    if (rulesJson == null || rulesJson.trim().isEmpty() || rulesJson.equals("[]")) {
      return Collections.emptyList();
    }
    // Parse JSON array: [{"ruleKey":"...","severity":"...","params":{...},...},...]
    var rules = new java.util.ArrayList<ServerActiveRule>();
    // Remove outer brackets
    var content = rulesJson.trim();
    if (content.startsWith("[") && content.endsWith("]")) {
      content = content.substring(1, content.length() - 1);
    }
    if (content.isEmpty()) {
      return rules;
    }
    // Split by object boundaries (simple approach - assumes no nested objects in params)
    var ruleObjects = splitJsonObjects(content);
    for (var ruleJson : ruleObjects) {
      try {
        var rule = parseRule(ruleJson);
        if (rule != null) {
          rules.add(rule);
        }
      } catch (Exception e) {
        LOG.debug("Failed to parse rule JSON: " + ruleJson, e);
      }
    }
    return rules;
  }

  private static ServerActiveRule parseRule(String ruleJson) {
    // Simple JSON parsing for rule object
    var ruleKey = extractJsonString(ruleJson, "ruleKey");
    if (ruleKey == null) {
      return null;
    }
    var severityStr = extractJsonString(ruleJson, "severity");
    var severity = severityStr != null ? IssueSeverity.valueOf(severityStr) : IssueSeverity.MAJOR;
    var templateKey = extractJsonString(ruleJson, "templateKey");
    var paramsJson = extractJsonObject(ruleJson, "params");
    var params = paramsJson != null ? parseParamsObject(paramsJson) : Collections.<String, String>emptyMap();
    var impactsJson = extractJsonArray(ruleJson, "overriddenImpacts");
    var overriddenImpacts = impactsJson != null ? parseImpactsArray(impactsJson) : Collections.<ImpactPayload>emptyList();
    
    return new ServerActiveRule(ruleKey, severity, params, templateKey, overriddenImpacts);
  }

  private static Map<String, String> parseParamsObject(String paramsJson) {
    var map = new HashMap<String, String>();
    if (paramsJson == null || paramsJson.equals("{}")) {
      return map;
    }
    var content = paramsJson.trim();
    if (content.startsWith("{") && content.endsWith("}")) {
      content = content.substring(1, content.length() - 1);
    }
    var pairs = splitJsonPairs(content);
    for (var pair : pairs) {
      var colonIndex = pair.indexOf(':');
      if (colonIndex > 0) {
        var key = unescapeJson(pair.substring(0, colonIndex).trim().replaceAll("^\"|\"$", ""));
        var value = unescapeJson(pair.substring(colonIndex + 1).trim().replaceAll("^\"|\"$", ""));
        map.put(key, value);
      }
    }
    return map;
  }

  private static List<ImpactPayload> parseImpactsArray(String impactsJson) {
    var impacts = new java.util.ArrayList<ImpactPayload>();
    if (impactsJson == null || impactsJson.equals("[]")) {
      return impacts;
    }
    var content = impactsJson.trim();
    if (content.startsWith("[") && content.endsWith("]")) {
      content = content.substring(1, content.length() - 1);
    }
    if (content.isEmpty()) {
      return impacts;
    }
    var impactObjects = splitJsonObjects(content);
    for (var impactJson : impactObjects) {
      var softwareQuality = extractJsonString(impactJson, "softwareQuality");
      var severity = extractJsonString(impactJson, "severity");
      if (softwareQuality != null && severity != null) {
        impacts.add(new ImpactPayload(softwareQuality, severity));
      }
    }
    return impacts;
  }

  private static String extractJsonString(String json, String key) {
    var pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"";
    var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
    if (matcher.find()) {
      return unescapeJson(matcher.group(1));
    }
    return null;
  }

  private static String extractJsonObject(String json, String key) {
    var pattern = "\"" + key + "\"\\s*:\\s*\\{";
    var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
    if (matcher.find()) {
      var start = matcher.end() - 1;
      var depth = 0;
      var inString = false;
      var escapeNext = false;
      for (var i = start; i < json.length(); i++) {
        var c = json.charAt(i);
        if (escapeNext) {
          escapeNext = false;
          continue;
        }
        if (c == '\\') {
          escapeNext = true;
          continue;
        }
        if (c == '"') {
          inString = !inString;
          continue;
        }
        if (!inString) {
          if (c == '{') {
            depth++;
          } else if (c == '}') {
            depth--;
            if (depth == 0) {
              return json.substring(start, i + 1);
            }
          }
        }
      }
    }
    return null;
  }

  private static String extractJsonArray(String json, String key) {
    var pattern = "\"" + key + "\"\\s*:\\s*\\[";
    var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
    if (matcher.find()) {
      var start = matcher.end() - 1;
      var depth = 0;
      var inString = false;
      var escapeNext = false;
      for (var i = start; i < json.length(); i++) {
        var c = json.charAt(i);
        if (escapeNext) {
          escapeNext = false;
          continue;
        }
        if (c == '\\') {
          escapeNext = true;
          continue;
        }
        if (c == '"') {
          inString = !inString;
          continue;
        }
        if (!inString) {
          if (c == '[') {
            depth++;
          } else if (c == ']') {
            depth--;
            if (depth == 0) {
              return json.substring(start, i + 1);
            }
          }
        }
      }
    }
    return null;
  }

  private static List<String> splitJsonObjects(String content) {
    var objects = new java.util.ArrayList<String>();
    var current = new StringBuilder();
    var depth = 0;
    var inString = false;
    var escapeNext = false;

    for (var c : content.toCharArray()) {
      if (escapeNext) {
        current.append(c);
        escapeNext = false;
      } else if (c == '\\') {
        current.append(c);
        escapeNext = true;
      } else if (c == '"') {
        current.append(c);
        inString = !inString;
      } else if (!inString) {
        if (c == '{') {
          depth++;
          current.append(c);
        } else if (c == '}') {
          depth--;
          current.append(c);
          if (depth == 0) {
            objects.add(current.toString());
            current = new StringBuilder();
            // Skip comma if present
          }
        } else if (c == ',' && depth == 0) {
          // Skip comma between objects
        } else {
          current.append(c);
        }
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0 && depth == 0) {
      objects.add(current.toString());
    }
    return objects;
  }

  private static List<String> splitJsonPairs(String content) {
    var pairs = new java.util.ArrayList<String>();
    var current = new StringBuilder();
    var inQuotes = false;
    var escapeNext = false;

    for (var c : content.toCharArray()) {
      if (escapeNext) {
        current.append(c);
        escapeNext = false;
      } else if (c == '\\') {
        current.append(c);
        escapeNext = true;
      } else if (c == '"') {
        current.append(c);
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        pairs.add(current.toString());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      pairs.add(current.toString());
    }
    return pairs;
  }

  private static LocalDateTime parseLastModified(String lastModified) {
    if (lastModified == null || lastModified.isEmpty()) {
      // Use current time as default
      return LocalDateTime.now();
    }
    try {
      // Try parsing as ISO date-time
      return LocalDateTime.parse(lastModified, ISO_DATE_TIME_FORMATTER);
    } catch (DateTimeParseException e) {
      try {
        // Try parsing as Instant (epoch millis or ISO string)
        var instant = Instant.parse(lastModified);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
      } catch (Exception e2) {
        try {
          // Try parsing as epoch milliseconds
          var epochMillis = Long.parseLong(lastModified);
          return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        } catch (Exception e3) {
          LOG.debug("Failed to parse lastModified: " + lastModified + ", using current time", e3);
          return LocalDateTime.now();
        }
      }
    }
  }

  private static String formatLastModified(LocalDateTime lastModified) {
    if (lastModified == null) {
      return "";
    }
    return lastModified.format(ISO_DATE_TIME_FORMATTER);
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t");
  }

  private static String unescapeJson(String s) {
    return s.replace("\\t", "\t")
      .replace("\\r", "\r")
      .replace("\\n", "\n")
      .replace("\\\"", "\"")
      .replace("\\\\", "\\");
  }

  private static String serializeSettings(Map<String, String> settings) {
    if (settings == null || settings.isEmpty()) {
      return null;
    }
    // Simple JSON serialization: {"key1":"value1","key2":"value2",...}
    // Escape quotes and backslashes in values
    return settings.entrySet().stream()
      .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
      .collect(Collectors.joining(",", "{", "}"));
  }

  private static Map<String, String> deserializeSettings(String settingsJson) {
    if (settingsJson == null || settingsJson.trim().isEmpty() || settingsJson.equals("{}")) {
      return new HashMap<>();
    }
    // Parse simple JSON: {"key1":"value1","key2":"value2",...}
    var map = new HashMap<String, String>();
    // Remove outer braces
    var content = settingsJson.trim();
    if (content.startsWith("{") && content.endsWith("}")) {
      content = content.substring(1, content.length() - 1);
    }
    // Split by comma (but not inside quotes)
    var pairs = splitJsonPairs(content);
    for (var pair : pairs) {
      var colonIndex = pair.indexOf(':');
      if (colonIndex > 0) {
        var key = unescapeJson(pair.substring(0, colonIndex).trim().replaceAll("^\"|\"$", ""));
        var value = unescapeJson(pair.substring(colonIndex + 1).trim().replaceAll("^\"|\"$", ""));
        map.put(key, value);
      }
    }
    return map;
  }
}
