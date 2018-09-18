/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.nio.file.Path;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;

@Immutable
public class ConnectedAnalysisConfiguration extends StandaloneAnalysisConfiguration {

  private final String projectKey;
  private final String toString;

  public ConnectedAnalysisConfiguration(@Nullable String projectKey, Path baseDir, Path workDir, Iterable<ClientInputFile> inputFiles, Map<String, String> extraProperties) {
    super(baseDir, workDir, inputFiles, extraProperties);
    this.projectKey = projectKey;
    this.toString = generateString();
  }

  @CheckForNull
  public String projectKey() {
    return projectKey;
  }

  @Override
  public String toString() {
    return toString;
  }
  
  private String generateString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    if (projectKey != null) {
      sb.append("  projectKey: ").append(projectKey).append("\n");
    }
    sb.append("  baseDir: ").append(baseDir()).append("\n");
    sb.append("  workDir: ").append(workDir()).append("\n");
    sb.append("  extraProperties: ").append(extraProperties()).append("\n");
    sb.append("  inputFiles: [\n");
    for (ClientInputFile inputFile : inputFiles()) {
      sb.append("    ").append(inputFile.getPath());
      sb.append(" (").append(getCharsetLabel(inputFile)).append(")");
      if (inputFile.isTest()) {
        sb.append(" [test]");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("]\n");
    return sb.toString();
  }

}
