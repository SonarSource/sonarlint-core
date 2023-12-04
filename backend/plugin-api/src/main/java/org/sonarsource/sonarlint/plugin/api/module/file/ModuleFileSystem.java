/*
 * SonarLint Plugin API
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
package org.sonarsource.sonarlint.plugin.api.module.file;

import java.util.stream.Stream;
import org.sonar.api.batch.fs.InputFile;

/**
 * This class is made available to components annotated with {@code @SonarLintSide(MODULE)}.
 * @since 6.0
 */
public interface ModuleFileSystem {
  /**
   * Returns all the files within the module that end with {@code suffix} and match {@code type}.
   *
   * @param suffix a suffix to filter the files
   * @param type   the type of file
   * @return a stream of files that match the given suffix and type in the module
   * @since 6.0
   */
  Stream<InputFile> files(String suffix, InputFile.Type type);


  /**
   * Returns all the files within the module.
   *
   * @return a stream of module files
   * @since 6.0
   */
  Stream<InputFile> files();
}
