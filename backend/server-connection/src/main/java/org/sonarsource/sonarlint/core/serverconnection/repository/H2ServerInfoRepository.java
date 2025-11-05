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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.JSON;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverapi.system.ServerStatusInfo;
import org.sonarsource.sonarlint.core.serverconnection.ServerSettings;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;

import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVERS;
import static org.sonarsource.sonarlint.core.commons.storage.model.Tables.SERVER_FEATURES;

/**
 * H2-based implementation of ServerInfoRepository.
 */
public class H2ServerInfoRepository implements ServerInfoRepository {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintDatabase database;

  public H2ServerInfoRepository(SonarLintDatabase database) {
    this.database = database;
  }

  @Override
  public synchronized void store(String connectionId, ServerStatusInfo serverStatus, Set<Feature> features, Map<String, String> globalSettings) {
    var dsl = database.dsl();
    try {
      // Serialize global settings to JSON string
      var globalSettingsJson = serializeGlobalSettings(globalSettings);
      var globalSettingsJsonValue = globalSettingsJson != null ? JSON.valueOf(globalSettingsJson) : null;

      // Store server info
      int serverUpdated = dsl.update(SERVERS)
        .set(SERVERS.VERSION, serverStatus.version())
        .set(SERVERS.ID, serverStatus.id())
        .set(SERVERS.GLOBAL_SETTINGS, globalSettingsJsonValue)
        .where(SERVERS.CONNECTION_ID.eq(connectionId))
        .execute();
      if (serverUpdated == 0) {
        dsl.insertInto(SERVERS, SERVERS.CONNECTION_ID, SERVERS.VERSION, SERVERS.ID, SERVERS.GLOBAL_SETTINGS)
          .values(connectionId, serverStatus.version(), serverStatus.id(), globalSettingsJsonValue)
          .execute();
      }

      // Store features
      var featureKeys = features.stream().map(Feature::getKey).toArray(String[]::new);
      int featuresUpdated = dsl.update(SERVER_FEATURES)
        .set(SERVER_FEATURES.FEATURES, featureKeys)
        .where(SERVER_FEATURES.CONNECTION_ID.eq(connectionId))
        .execute();
      if (featuresUpdated == 0) {
        dsl.insertInto(SERVER_FEATURES, SERVER_FEATURES.CONNECTION_ID, SERVER_FEATURES.FEATURES)
          .values(connectionId, featureKeys)
          .execute();
      }
    } catch (RuntimeException ex) {
      LOG.debug("Store failed: " + ex.getMessage());
      throw ex;
    }
  }

  @Override
  public Optional<StoredServerInfo> read(String connectionId) {
    var serverRec = database.dsl()
      .select(SERVERS.VERSION, SERVERS.ID, SERVERS.GLOBAL_SETTINGS)
      .from(SERVERS)
      .where(SERVERS.CONNECTION_ID.eq(connectionId))
      .fetchOne();

    if (serverRec == null) {
      return Optional.empty();
    }

    var version = Version.create(serverRec.get(SERVERS.VERSION));
    var serverId = serverRec.get(SERVERS.ID);

    // Read features
    var featuresRec = database.dsl()
      .select(SERVER_FEATURES.FEATURES)
      .from(SERVER_FEATURES)
      .where(SERVER_FEATURES.CONNECTION_ID.eq(connectionId))
      .fetchOne();

    var features = featuresRec != null && featuresRec.get(SERVER_FEATURES.FEATURES) != null
      ? Arrays.stream(featuresRec.get(SERVER_FEATURES.FEATURES))
        .map(Feature::fromKey)
        .flatMap(Optional::stream)
        .collect(Collectors.toSet())
      : Collections.<Feature>emptySet();

    // Deserialize global settings from JSON
    var globalSettingsJsonValue = serverRec.get(SERVERS.GLOBAL_SETTINGS);
    var globalSettingsJsonString = globalSettingsJsonValue != null ? globalSettingsJsonValue.data() : null;
    var globalSettingsMap = deserializeGlobalSettings(globalSettingsJsonString);
    var globalSettings = new ServerSettings(globalSettingsMap);

    return Optional.of(new StoredServerInfo(version, features, globalSettings, serverId));
  }

  private static String serializeGlobalSettings(Map<String, String> globalSettings) {
    if (globalSettings == null || globalSettings.isEmpty()) {
      return null;
    }
    // Simple JSON serialization: {"key1":"value1","key2":"value2",...}
    // Escape quotes and backslashes in values
    return globalSettings.entrySet().stream()
      .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
      .collect(Collectors.joining(",", "{", "}"));
  }

  private static Map<String, String> deserializeGlobalSettings(String globalSettingsJson) {
    if (globalSettingsJson == null || globalSettingsJson.trim().isEmpty() || globalSettingsJson.equals("{}")) {
      return new HashMap<>();
    }
    // Parse simple JSON: {"key1":"value1","key2":"value2",...}
    var map = new HashMap<String, String>();
    // Remove outer braces
    var content = globalSettingsJson.trim();
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
}
