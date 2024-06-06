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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class AnalysisPropertiesService {

  Map<String, Map<String, String>> propertiesByConfigScope = new ConcurrentHashMap<>();

  public Map<String, String> getProperties(String configurationScopeId) {
    return propertiesByConfigScope.getOrDefault(configurationScopeId, new ConcurrentHashMap<>());
  }

  public void setProperties(String configurationScopeId, Map<String, String> extraProperties) {
    propertiesByConfigScope.put(configurationScopeId, new ConcurrentHashMap<>(extraProperties));
  }

  public void setOrUpdateProperties(String configurationScopeId, Map<String, String> extraProperties) {
    propertiesByConfigScope.computeIfAbsent(configurationScopeId, k -> new ConcurrentHashMap<>());
    propertiesByConfigScope.get(configurationScopeId).putAll(extraProperties);
  }

}
