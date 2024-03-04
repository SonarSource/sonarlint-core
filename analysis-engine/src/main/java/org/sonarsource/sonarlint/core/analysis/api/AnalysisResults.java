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
package org.sonarsource.sonarlint.core.analysis.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Language;

public class AnalysisResults {
  private final Set<ClientInputFile> failedAnalysisFiles = new LinkedHashSet<>();
  private int indexedFileCount;
  private final Map<ClientInputFile, Language> languagePerFile = new LinkedHashMap<>();

  public AnalysisResults setIndexedFileCount(int indexedFileCount) {
    this.indexedFileCount = indexedFileCount;
    return this;
  }

  public void addFailedAnalysisFile(ClientInputFile inputFile) {
    failedAnalysisFiles.add(inputFile);
  }

  /**
   * Detected languages for each file.
   * The values in the map can be null if no language was detected for some files.
   */
  public Map<ClientInputFile, Language> languagePerFile() {
    return languagePerFile;
  }

  public void setLanguageForFile(ClientInputFile file, @Nullable Language language) {
    this.languagePerFile.put(file, language);
  }

  /**
   * Number of file indexed. This number can be different than number of provided {@link ClientInputFile} since
   * InputFileFilter can exclude some files.
   */
  public int indexedFileCount() {
    return indexedFileCount;
  }

  /**
   * Input files for which there were analysis errors. The analyzers failed to correctly handle these files, and therefore there might be issues
   * missing or no issues at all for these files.
   */
  public Collection<ClientInputFile> failedAnalysisFiles() {
    return failedAnalysisFiles;
  }

}
