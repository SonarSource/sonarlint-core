/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.api.sonarlint.SonarLintSide;

@SonarLintSide
public class InputFileCache implements FileSystem.Index {

  private final Set<InputFile> inputFileCache = new LinkedHashSet<>();
  private final SetMultimap<String, InputFile> filesByNameCache = LinkedHashMultimap.create();
  private final SetMultimap<String, InputFile> filesByExtensionCache = LinkedHashMultimap.create();
  private final SortedSet<String> languages = new TreeSet<>();

  @Override
  public Iterable<InputFile> inputFiles() {
    return inputFileCache;
  }

  public void doAdd(InputFile inputFile) {
    if (inputFile.language() != null) {
      languages.add(inputFile.language());
    }
    inputFileCache.add(inputFile);
    filesByNameCache.put(inputFile.filename(), inputFile);
    filesByExtensionCache.put(FileExtensionPredicate.getExtension(inputFile), inputFile);
  }

  @Override
  public InputFile inputFile(String relativePath) {
    throw new UnsupportedOperationException("inputFile(String relativePath)");
  }

  @Override
  public Iterable<InputFile> getFilesByName(String filename) {
    return filesByNameCache.get(filename);
  }

  @Override
  public Iterable<InputFile> getFilesByExtension(String extension) {
    return filesByExtensionCache.get(extension);
  }

  protected SortedSet<String> languages() {
    return languages;
  }

}
