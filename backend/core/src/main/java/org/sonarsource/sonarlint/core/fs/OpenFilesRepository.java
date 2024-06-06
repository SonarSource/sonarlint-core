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
package org.sonarsource.sonarlint.core.fs;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class OpenFilesRepository {
  private final Map<String, List<URI>> openFilesByConfigScopeId = new ConcurrentHashMap<>();

  public void considerOpened(String configurationScopeId, URI fileUri) {
    openFilesByConfigScopeId.computeIfAbsent(configurationScopeId, k -> new ArrayList<>()).add(fileUri);
  }

  public void considerClosed(String configurationScopeId, URI fileUri) {
    var openFiles = openFilesByConfigScopeId.get(configurationScopeId);
    if (openFiles != null) {
      openFiles.remove(fileUri);
    }
  }

  public List<URI> getOpenFilesAmong(String configurationScopeId, Set<URI> fileUris) {
    var openFiles = openFilesByConfigScopeId.getOrDefault(configurationScopeId, new ArrayList<>());
    return openFiles.stream().filter(fileUris::contains).collect(Collectors.toList());
  }
}
