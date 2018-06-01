/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.common.analysis;

import java.util.Collection;
import java.util.Map;

public interface AnalysisResults {

  /**
   * Number of file indexed. This number can be different than number of provided {@link ClientInputFile} since
   * {@link InputFileFilter} can exclude some files.
   */
  int indexedFileCount();

  /**
   * Input files for which there were analysis errors. The analyzers failed to correctly handle these files, and therefore there might be issues
   * missing or no issues at all for these files.
   */
  Collection<ClientInputFile> failedAnalysisFiles();

  /**
   * Detected languages for each file.
   * The values in the map can be null if no language was detected for some files.
   */
  Map<ClientInputFile, String> languagePerFile();

}
