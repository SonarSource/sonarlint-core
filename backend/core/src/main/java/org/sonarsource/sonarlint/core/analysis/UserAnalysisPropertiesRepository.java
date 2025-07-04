/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public class UserAnalysisPropertiesRepository {
  private static final String PATH_TO_COMPILE_COMMANDS_ANALYZER_PROPERTY = "sonar.cfamily.compile-commands";
  private final Map<String, String> pathToCompileCommandsByConfigScope = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> propertiesByConfigScope = new ConcurrentHashMap<>();

  public Map<String, String> getUserProperties(String configurationScopeId) {
    var properties = propertiesByConfigScope.getOrDefault(configurationScopeId, new HashMap<>());
    var pathToCompileCommands = pathToCompileCommandsByConfigScope.get(configurationScopeId);
    if (pathToCompileCommands == null) {
      return properties;
    }
    properties.put(PATH_TO_COMPILE_COMMANDS_ANALYZER_PROPERTY, pathToCompileCommands);
    return properties;
  }

  public boolean setUserProperties(String configurationScopeId, Map<String, String> userProperties) {
    var oldProperties = propertiesByConfigScope.get(configurationScopeId);
    var newProperties = new HashMap<>(userProperties);
    var changed = !newProperties.equals(oldProperties);
    if (changed) {
      propertiesByConfigScope.put(configurationScopeId, newProperties);
    }
    return changed;
  }

  public boolean setOrUpdatePathToCompileCommands(String configurationScopeId, @Nullable String value) {
    var newValue = value == null ? "" : value;
    var oldValue = pathToCompileCommandsByConfigScope.get(configurationScopeId);
    var changed = !newValue.equals(oldValue);
    if (changed) {
      pathToCompileCommandsByConfigScope.put(configurationScopeId, newValue);
    }
    return changed;
  }
}
