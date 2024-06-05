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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

@Named
@Singleton
public class AnalysisExtraPropertiesService {

  Map<String, Map<String, String>> extraPropertiesByConfigScope = new ConcurrentHashMap<>();

  public AnalysisExtraPropertiesService(InitializeParams initializeParams) {
    extraPropertiesByConfigScope.putAll(initializeParams.getExtraAnalyserPropsByConfigScopeId());
  }

  public Map<String, String> getExtraProperties(String configurationScopeId) {
    return extraPropertiesByConfigScope.getOrDefault(configurationScopeId, new HashMap<>());
  }

  public void setExtraProperties(String configurationScopeId, Map<String, String> extraProperties) {
    extraPropertiesByConfigScope = new HashMap<>();
    extraPropertiesByConfigScope.put(configurationScopeId, new HashMap<>(extraProperties));
  }

  public void setOrUpdateExtraProperties(String configurationScopeId, Map<String, String> extraProperties) {
    var properties = extraPropertiesByConfigScope.get(configurationScopeId);
    if (properties != null) {
      properties.putAll(extraProperties);
    } else {
      extraPropertiesByConfigScope.put(configurationScopeId, new HashMap<>(extraProperties));
    }
  }

}
