/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.scanner.IssueExclusionsLoader;

public class InputFileBuilder {
  private static final Logger LOG = Loggers.get(InputFileBuilder.class);
  private final LanguageDetection langDetection;
  private final FileMetadata fileMetadata;
  private final IssueExclusionsLoader exclusionsScanner;

  public InputFileBuilder(LanguageDetection langDetection, FileMetadata fileMetadata, IssueExclusionsLoader exclusionsScanner) {
    this.langDetection = langDetection;
    this.fileMetadata = fileMetadata;
    this.exclusionsScanner = exclusionsScanner;
  }

  LanguageDetection langDetection() {
    return langDetection;
  }

  SonarLintInputFile create(ClientInputFile inputFile) {
    SonarLintInputFile defaultInputFile = new SonarLintInputFile(inputFile, f -> {
      LOG.debug("Initializing metadata of file {}", f.uri());
      Charset charset = f.charset();
      InputStream stream;
      try {
        stream = f.inputStream();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to open a stream on file: " + f.uri(), e);
      }
      return fileMetadata.readMetadata(stream, charset != null ? charset : Charset.defaultCharset(), f.uri(), exclusionsScanner.createCharHandlerFor(f));
    });
    defaultInputFile.setType(inputFile.isTest() ? Type.TEST : Type.MAIN);
    if (inputFile.language() != null) {
      LOG.debug("Language of file '{}' is set to '{}'", inputFile.uri(), inputFile.language());
      defaultInputFile.setLanguage(inputFile.language());
    } else {
      defaultInputFile.setLanguage(langDetection.language(defaultInputFile));
    }

    return defaultInputFile;
  }

}
