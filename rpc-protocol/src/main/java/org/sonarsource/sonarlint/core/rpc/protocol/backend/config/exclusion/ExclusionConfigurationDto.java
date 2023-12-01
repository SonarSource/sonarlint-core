/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.exclusion;

import java.nio.file.Path;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

public class ExclusionConfigurationDto {

  private final List<Path> fileExclusions;
  private final List<Path> directoryExclusions;
  private final List<String> globPatternExclusions;

  /**
   *
   * @param fileExclusions Should match {@link ClientFileDto#getRelativePath()}
   * @param directoryExclusions Should match any parent dir of {@link ClientFileDto#getRelativePath()}
   * @param globPatternExclusions Patterns using the Java glob syntax.
   */
  public ExclusionConfigurationDto(List<Path> fileExclusions, List<Path> directoryExclusions, List<String> globPatternExclusions) {
    this.fileExclusions = fileExclusions;
    this.directoryExclusions = directoryExclusions;
    this.globPatternExclusions = globPatternExclusions;
  }

  public List<Path> getFileExclusions() {
    return fileExclusions;
  }

  public List<Path> getDirectoryExclusions() {
    return directoryExclusions;
  }

  public List<String> getGlobPatternExclusions() {
    return globPatternExclusions;
  }
}
