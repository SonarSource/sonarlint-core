/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.Map;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonarsource.api.sonarlint.SonarLintSide;

@SonarLintSide
public class InputPathCache extends DefaultFileSystem.Cache {

  private final Map<Path, InputFile> inputFileCache = Maps.newLinkedHashMap();
  private final Map<Path, InputDir> inputDirCache = Maps.newLinkedHashMap();

  @Override
  public Iterable<InputFile> inputFiles() {
    return inputFileCache.values();
  }

  public Iterable<InputDir> allDirs() {
    return inputDirCache.values();
  }

  @Override
  public void doAdd(InputFile inputFile) {
    inputFileCache.put(inputFile.path(), inputFile);
  }

  @Override
  public void doAdd(InputDir inputDir) {
    inputDirCache.put(inputDir.path(), inputDir);
  }

  @Override
  public InputFile inputFile(String relativePath) {
    return null;
  }

  @Override
  public InputDir inputDir(String relativePath) {
    return null;
  }

  public InputFile inputFile(Path path) {
    return inputFileCache.get(path);
  }

  public InputDir inputDir(Path path) {
    return inputDirCache.get(path);
  }

}
