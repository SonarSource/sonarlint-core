/*
 * SonarLint Core Library
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

import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;

@BatchSide
public class ModuleInputFileCache extends DefaultFileSystem.Cache {

  private final InputPathCache inputPathCache;

  public ModuleInputFileCache(InputPathCache projectCache) {
    this.inputPathCache = projectCache;
  }

  @Override
  public Iterable<InputFile> inputFiles() {
    return inputPathCache.allFiles();
  }

  @Override
  public InputFile inputFile(String relativePath) {
    return inputPathCache.getFile(relativePath);
  }

  @Override
  public InputDir inputDir(String relativePath) {
    return inputPathCache.getDir(relativePath);
  }

  @Override
  protected void doAdd(InputFile inputFile) {
    inputPathCache.put(inputFile);
  }

  @Override
  protected void doAdd(InputDir inputDir) {
    inputPathCache.put(inputDir);
  }
}
