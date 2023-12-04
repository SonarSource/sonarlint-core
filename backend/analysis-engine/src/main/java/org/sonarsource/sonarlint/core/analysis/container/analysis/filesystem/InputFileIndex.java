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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.api.sonarlint.SonarLintSide;

@SonarLintSide
public class InputFileIndex implements FileSystem.Index {

  private final Set<InputFile> inputFiles = new LinkedHashSet<>();
  private final Map<String, Set<InputFile>> filesByNameIndex = new LinkedHashMap<>();
  private final Map<String, Set<InputFile>> filesByExtensionIndex = new LinkedHashMap<>();
  private final SortedSet<String> languages = new TreeSet<>();

  @Override
  public Iterable<InputFile> inputFiles() {
    return inputFiles;
  }

  public void doAdd(InputFile inputFile) {
    if (inputFile.language() != null) {
      languages.add(inputFile.language());
    }
    inputFiles.add(inputFile);
    filesByNameIndex.computeIfAbsent(inputFile.filename(), f -> new LinkedHashSet<>()).add(inputFile);
    filesByExtensionIndex.computeIfAbsent(FileExtensionPredicate.getExtension(inputFile), f -> new LinkedHashSet<>()).add(inputFile);
  }

  @Override
  public InputFile inputFile(String relativePath) {
    throw new UnsupportedOperationException("inputFile(String relativePath)");
  }

  @Override
  public Iterable<InputFile> getFilesByName(String filename) {
    return filesByNameIndex.get(filename);
  }

  @Override
  public Iterable<InputFile> getFilesByExtension(String extension) {
    return filesByExtensionIndex.get(extension);
  }

  protected SortedSet<String> languages() {
    return languages;
  }

}
