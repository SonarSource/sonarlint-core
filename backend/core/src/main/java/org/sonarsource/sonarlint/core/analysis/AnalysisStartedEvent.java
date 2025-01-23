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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

import static java.util.stream.Collectors.toSet;

public class AnalysisStartedEvent {
  private final String configurationScopeId;
  private final UUID analysisId;
  private final List<ClientInputFile> files;
  private final boolean enableTracking;

  public AnalysisStartedEvent(String configurationScopeId, UUID analysisId, Iterable<ClientInputFile> files, boolean enableTracking) {
    this.configurationScopeId = configurationScopeId;
    this.analysisId = analysisId;
    this.files = StreamSupport.stream(files.spliterator(), false).toList();
    this.enableTracking = enableTracking;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public Set<Path> getFileRelativePaths() {
    return files.stream().map(ClientInputFile::relativePath).map(Path::of).collect(toSet());
  }

  public Set<URI> getFileUris() {
    return files.stream().map(ClientInputFile::uri).collect(toSet());
  }

  public boolean isTrackingEnabled() {
    return enableTracking;
  }

  public UnaryOperator<String> getFileContentProvider() {
    return path -> files.stream()
      .filter(ClientInputFile::isDirty)
      .filter(clientInputFile -> clientInputFile.relativePath().equals(path))
      .findFirst()
      .map(AnalysisStartedEvent::getClientInputFileContent)
      .orElse(null);
  }

  private static String getClientInputFileContent(ClientInputFile clientInputFile) {
    try {
      return clientInputFile.contents();
    } catch (IOException e) {
      return "";
    }
  }
}
