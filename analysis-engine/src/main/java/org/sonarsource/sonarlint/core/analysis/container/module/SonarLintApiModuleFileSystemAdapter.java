/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.module;

import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem.FileType;

public class SonarLintApiModuleFileSystemAdapter implements org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileSystem {

  private final ModuleFileSystem moduleFileSystem;
  private final ModuleInputFileBuilder inputFileBuilder;

  public SonarLintApiModuleFileSystemAdapter(ModuleFileSystem moduleFileSystem, ModuleInputFileBuilder inputFileBuilder) {
    this.moduleFileSystem = moduleFileSystem;
    this.inputFileBuilder = inputFileBuilder;
  }

  @Override
  public Stream<InputFile> files(String suffix, InputFile.Type type) {
    return moduleFileSystem.listFiles(suffix, convert(type))
      .map(inputFileBuilder::create);
  }

  private static FileType convert(Type type) {
    switch (type) {
      case MAIN:
        return FileType.MAIN;
      case TEST:
        return FileType.TEST;
      default:
        throw new IllegalStateException("Unknown enum value: " + type);
    }
  }

  @Override
  public Stream<InputFile> files() {
    return moduleFileSystem.listAllFiles()
      .map(inputFileBuilder::create);
  }
}
