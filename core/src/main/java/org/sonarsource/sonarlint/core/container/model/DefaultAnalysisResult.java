/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class DefaultAnalysisResult implements AnalysisResults {
  private Set<ClientInputFile> failedAnalysisFiles = new LinkedHashSet<>();
  private int indexedFileCount;
  private Map<ClientInputFile, String> languagePerFile = new LinkedHashMap<>();

  public DefaultAnalysisResult setIndexedFileCount(int indexedFileCount) {
    this.indexedFileCount = indexedFileCount;
    return this;
  }

  public void addFailedAnalysisFile(ClientInputFile inputFile) {
    failedAnalysisFiles.add(inputFile);
  }

  @Override
  public Map<ClientInputFile, String> languagePerFile() {
    return languagePerFile;
  }

  public void setLanguageForFile(ClientInputFile file, @Nullable String language) {
    this.languagePerFile.put(file, language);
  }

  @Override
  public int indexedFileCount() {
    return indexedFileCount;
  }

  @Override
  public Collection<ClientInputFile> failedAnalysisFiles() {
    return failedAnalysisFiles;
  }

}
