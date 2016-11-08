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
package org.sonarsource.sonarlint.core.util;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class LoggedErrorHandler {
  private static final String PARSING_ERROR_PREFIX = "Unable to parse source file : ";
  private static final String PARSING_ERROR_PREFIX_JS = "Unable to parse file: ";
  private final Set<ClientInputFile> erroredFiles;
  private final Iterable<ClientInputFile> inputFiles;

  public LoggedErrorHandler(Iterable<ClientInputFile> inputFiles) {
    this.inputFiles = inputFiles;
    this.erroredFiles = new HashSet<>();
  }

  public void handleException(String className) {
    if (className.equals(IllegalStateException.class.getName())) {
      for (ClientInputFile file : inputFiles) {
        erroredFiles.add(file);
      }
    }
  }

  public void handleError(String msg) {
    if (msg.startsWith(PARSING_ERROR_PREFIX)) {
      markFileError(msg.substring(PARSING_ERROR_PREFIX.length()));
    } else if (msg.startsWith(PARSING_ERROR_PREFIX_JS)) {
      markFileError(msg.substring(PARSING_ERROR_PREFIX_JS.length()));
    }
  }

  private void markFileError(String absoluteFilePath) {
    Optional<ClientInputFile> matched = StreamSupport.stream(inputFiles.spliterator(), false)
      .filter(f -> f.getPath().equals(absoluteFilePath))
      .findAny();
    if (matched.isPresent()) {
      erroredFiles.add(matched.get());
    }
  }

  public Set<ClientInputFile> getErrorFiles() {
    return erroredFiles;
  }
}
