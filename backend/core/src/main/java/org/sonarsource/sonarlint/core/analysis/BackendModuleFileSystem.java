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

import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;


public class BackendModuleFileSystem implements ClientModuleFileSystem {

  private final ClientFileSystemService clientFileSystemService;
  private final String configScopeId;

  public BackendModuleFileSystem(ClientFileSystemService clientFileSystemService, String configScopeId) {
    this.clientFileSystemService = clientFileSystemService;
    this.configScopeId = configScopeId;
  }

  @Override
  public Stream<ClientInputFile> files(String suffix, InputFile.Type type) {
    return files()
      .filter(file -> file.relativePath().endsWith(suffix))
      .filter(file -> file.isTest() == (type == InputFile.Type.TEST));
  }

  @Override
  public Stream<ClientInputFile> files() {
    return this.clientFileSystemService.getFiles(configScopeId).stream().map(BackendInputFile::new);
  }

}
