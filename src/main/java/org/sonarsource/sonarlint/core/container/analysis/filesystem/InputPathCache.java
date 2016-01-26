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

import com.google.common.collect.Maps;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;

/**
 * Cache of all files and dirs. This cache is shared amongst all project modules. Inclusion and
 * exclusion patterns are already applied.
 */
@BatchSide
public class InputPathCache {

  private final Map<String, InputFile> inputFileCache = Maps.newLinkedHashMap();
  private final Map<String, InputDir> inputDirCache = Maps.newLinkedHashMap();

  public Iterable<InputFile> allFiles() {
    return inputFileCache.values();
  }

  public Iterable<InputDir> allDirs() {
    return inputDirCache.values();
  }

  public InputPathCache put(InputFile inputFile) {
    inputFileCache.put(inputFile.relativePath(), inputFile);
    return this;
  }

  public InputPathCache put(InputDir inputDir) {
    inputDirCache.put(inputDir.relativePath(), inputDir);
    return this;
  }

  @CheckForNull
  public InputFile getFile(String relativePath) {
    return inputFileCache.get(relativePath);
  }

  @CheckForNull
  public InputDir getDir(String relativePath) {
    return inputDirCache.get(relativePath);
  }

}
