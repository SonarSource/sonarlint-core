/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleInputFileBuilder;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileSystem;

public class SonarLintModuleFileSystem implements ModuleFileSystem {

  private final ClientModuleFileSystem clientFileSystem;
  private final ModuleInputFileBuilder inputFileBuilder;

  public SonarLintModuleFileSystem(ClientModuleFileSystem clientFileSystem, ModuleInputFileBuilder inputFileBuilder) {
    this.clientFileSystem = clientFileSystem;
    this.inputFileBuilder = inputFileBuilder;
  }

  @Override
  public Stream<InputFile> files(String suffix, InputFile.Type type) {
    return clientFileSystem.files(suffix, type)
      .map(inputFileBuilder::create);
  }

  @Override
  public Stream<InputFile> files() {
    return clientFileSystem.files()
      .map(inputFileBuilder::create);
  }
}
