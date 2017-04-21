/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.FileExtensionPredicate;
import org.sonar.api.batch.fs.internal.FilenamePredicate;
import org.sonarsource.api.sonarlint.SonarLintSide;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

@SonarLintSide
public class InputPathCache extends DefaultFileSystem.Cache {

  private final Map<Path, InputFile> inputFileCache = new LinkedHashMap<>();
  private final Map<Path, InputDir> inputDirCache = new LinkedHashMap<>();
  private final SetMultimap<String, InputFile> filesByNameCache = LinkedHashMultimap.create();
  private final SetMultimap<String, InputFile> filesByExtensionCache = LinkedHashMultimap.create();
  private final SortedSet<String> languages = new TreeSet<>();

  @Override
  public Iterable<InputFile> inputFiles() {
    return inputFileCache.values();
  }

  public Iterable<InputDir> allDirs() {
    return inputDirCache.values();
  }

  @Override
  public void doAdd(InputFile inputFile) {
    if (inputFile.language() != null) {
      languages.add(inputFile.language());
    }
    inputFileCache.put(inputFile.path(), inputFile);
    filesByNameCache.put(FilenamePredicate.getFilename(inputFile), inputFile);
    filesByExtensionCache.put(FileExtensionPredicate.getExtension(inputFile), inputFile);
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

  @Override
  public Iterable<InputFile> getFilesByName(String filename) {
    return filesByNameCache.get(filename);
  }

  @Override
  public Iterable<InputFile> getFilesByExtension(String extension) {
    return filesByExtensionCache.get(extension);
  }

  @Override
  protected SortedSet<String> languages() {
    return languages;
  }

}
