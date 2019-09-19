/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarlint.languageserver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

@SuppressWarnings("unchecked")
class UserSettings {

  static final String RULES = "rules";
  static final String DISABLE_TELEMETRY = "disableTelemetry";
  static final String TYPESCRIPT_LOCATION = "typeScriptLocation";
  static final String TEST_FILE_PATTERN = "testFilePattern";
  static final String ANALYZER_PROPERTIES = "analyzerProperties";
  static final String CONNECTED_MODE_SERVERS_PROP = "connectedModeServers";
  static final String CONNECTED_MODE_PROJECT_PROP = "connectedModeProject";

  final Map<String, String> analyzerProperties;
  final boolean disableTelemetry;
  final PathMatcher testMatcher;
  final Collection<RuleKey> excludedRules;

  UserSettings() {
    this(Collections.emptyMap());
  }

  UserSettings(Map<String, Object> params) {
    String testFilePattern = (String) params.get(TEST_FILE_PATTERN);
    testMatcher = testFilePattern != null ? FileSystems.getDefault().getPathMatcher("glob:" + testFilePattern) : (p -> false);
    this.analyzerProperties = getAnalyzerProperties(params);
    this.disableTelemetry = (Boolean) params.getOrDefault(DISABLE_TELEMETRY, false);
    this.excludedRules = parseExcludedRules(params);
  }

  private static Set<RuleKey> parseExcludedRules(Map<String, Object> params) {
    return ((Map<String, Object>) params.getOrDefault(RULES, Collections.emptyMap()))
      .entrySet()
      .stream()
      .filter(UserSettings::hasLevelSetToOff)
      .map(UserSettings::safeParseRuleKey)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private static boolean hasLevelSetToOff(Map.Entry<String, Object> e) {
    return e.getValue() instanceof Map &&
      "off".equals(((Map) e.getValue()).get("level"));
  }

  @CheckForNull
  private static RuleKey safeParseRuleKey(Map.Entry<String, Object> e) {
    try {
      return RuleKey.parse(e.getKey());
    } catch (Exception any) {
      return null;
    }
  }

  private static Map<String, String> getAnalyzerProperties(Map<String, Object> params) {
    Map map = (Map) params.get(ANALYZER_PROPERTIES);
    if (map == null) {
      return Collections.emptyMap();
    }
    return map;
  }

  // See the changelog for any evolutions on how properties are parsed:
  // https://github.com/eclipse/lsp4j/blob/master/CHANGELOG.md
  // (currently JsonElement, used to be Map<String, Object>)
  static Map<String, Object> parseToMap(Object obj) {
    try {
      return new Gson().fromJson((JsonElement) obj, Map.class);
    } catch (JsonSyntaxException e) {
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "Expected a JSON map but was: " + obj, e));
    }
  }
}
