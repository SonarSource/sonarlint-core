/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

@Immutable
public class ConnectedAnalysisConfiguration extends org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration {

  private final String moduleKey;

  public ConnectedAnalysisConfiguration(@Nullable String moduleKey, Path baseDir, Path workDir, Iterable<ClientInputFile> inputFiles, Map<String, String> extraProperties) {
    super(baseDir, workDir, inputFiles, extraProperties);
    this.moduleKey = moduleKey;
  }

  @CheckForNull
  public String moduleKey() {
    return moduleKey;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    if (moduleKey != null) {
      sb.append("  moduleKey: ").append(moduleKey).append("\n");
    }
    sb.append("  baseDir: ").append(baseDir()).append("\n");
    sb.append("  workDir: ").append(workDir()).append("\n");
    sb.append("  extraProperties: ").append(extraProperties()).append("\n");
    sb.append("  inputFiles: [\n");
    for (ClientInputFile inputFile : inputFiles()) {
      sb.append("    ").append(inputFile.getPath().toString());
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
