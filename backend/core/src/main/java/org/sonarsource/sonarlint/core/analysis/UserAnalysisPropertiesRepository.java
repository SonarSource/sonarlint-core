/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class UserAnalysisPropertiesRepository {

  private static final String PATH_TO_COMPILE_COMMANDS_ANALYSER_PROPERTY = "sonar.cfamily.compile-commands";
  private static final Map<String, String> pathToCompileCommandsByConfigScope = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> propertiesByConfigScope = new ConcurrentHashMap<>();

  public Map<String, String> getUserProperties(String configurationScopeId) {
    var properties = propertiesByConfigScope.getOrDefault(configurationScopeId, new HashMap<>());
    var pathToCompileCommands = pathToCompileCommandsByConfigScope.get(configurationScopeId);
    if (pathToCompileCommands == null) {
      return properties;
    }
    properties.put(PATH_TO_COMPILE_COMMANDS_ANALYSER_PROPERTY, pathToCompileCommands);
    return properties;
  }

  public void setUserProperties(String configurationScopeId, Map<String, String> extraProperties) {
    propertiesByConfigScope.put(configurationScopeId, new HashMap<>(extraProperties));
  }

  public void setOrUpdatePathToCompileCommands(String configurationScopeId, String value) {
    pathToCompileCommandsByConfigScope.put(configurationScopeId, value);
  }
}
