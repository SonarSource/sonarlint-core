/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OpenFilesRepository {
  private final Map<String, Set<URI>> openFilesByConfigScopeId = new ConcurrentHashMap<>();

  /**
   * @return true if the file was previously not considered open; it is a newly opened file
   */
  public boolean considerOpened(String configurationScopeId, URI fileUri) {
    var openFiles = openFilesByConfigScopeId.computeIfAbsent(configurationScopeId, k -> new HashSet<>());
    return openFiles.add(fileUri);
  }

  public void considerClosed(String configurationScopeId, URI fileUri) {
    var openFiles = openFilesByConfigScopeId.get(configurationScopeId);
    if (openFiles != null) {
      openFiles.remove(fileUri);
    }
  }

  public Set<URI> getOpenFilesAmong(String configurationScopeId, Set<URI> fileUris) {
    var openFiles = openFilesByConfigScopeId.getOrDefault(configurationScopeId, Set.of());
    return openFiles.stream().filter(fileUris::contains).collect(Collectors.toSet());
  }

  public Map<String, Set<URI>> getOpenFilesByConfigScopeId() {
    return openFilesByConfigScopeId;
  }

  public Set<URI> getOpenFilesForConfigScope(String configurationScopeId) {
    return openFilesByConfigScopeId.getOrDefault(configurationScopeId, Set.of());
  }
}
